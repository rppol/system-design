# System Design: Proximity Service (Nearby Places Search)

## Intuition

> **Design intuition**: A proximity service answers one question — "what's near me, that matches what I'm looking for, ranked best-first?" — but that single sentence hides two entirely different sub-problems wearing a trenchcoat. The first sub-problem is purely geometric: "which of 50 million businesses fall within 2km of this point?" The second is nothing to do with geometry at all: "of those, which are open right now, rated 4+ stars, in the 'sushi' category, and not too far down the relevance ranking?" Treating these as one query — one giant index that's somehow both spatially sorted *and* filterable by ten unrelated attributes *and* freshly updated for "open now" status — is the trap. The production answer is a **two-phase pipeline**: a geo-index narrows millions of businesses down to a few hundred-to-thousand spatial candidates cheaply, and a second-phase attribute/ranking index (typically a search engine like Elasticsearch) does the expensive filtering and scoring on that much smaller candidate set.

**Key insight**: This system is **read-heavy with rich, slow-changing data and loose-to-moderate freshness requirements** — the opposite profile from Uber's driver-matching problem, which is **write-heavy with minimal data and tight freshness requirements**. A business's location, name, category, and price tier change maybe once a year; a driver's location changes every 4 seconds. A "find sushi near me, open now, 4+ stars" query needs ten different filterable attributes ranked by a weighted formula; "find any available driver within 2km" needs exactly one filter (availability) and the nearest candidate wins. Because of this, proximity *search* can afford to build and maintain a much heavier, richer index (Elasticsearch with full-text + geo + numeric filters, replicated and cached aggressively) — something that would be far too slow to keep in sync against Uber's 375K location-updates/sec write firehose (cross-ref [`./design_uber.md`](./design_uber.md) §4). The entire architecture of this design — two-phase search, dual indexes for "what" vs. "where," and a split-TTL cache for slow attributes vs. fast "open now" status — follows from this one observation about read/write ratio and filter complexity.

---

## 1. Requirements Clarification

### Functional Requirements
- **Nearby search**: given `(lat, lng, radius)`, return businesses/places within that radius, ranked by a combination of distance and relevance
- **Attribute filtering**: support filters on category (e.g., "coffee", "italian restaurant"), price tier (`$`-`$$$$`), minimum rating, and "open now"
- **Free-text search**: allow a query string (e.g., "best ramen") combined with location, so the system functions as "Google/Yelp search box" not just a pure radius filter
- **Business listing management**: business owners (or an internal CMS) can create, update, and delete listings — name, address/coordinates, category, hours of operation, price tier, photos, description
- **Operating-status updates**: business owners can mark their listing as temporarily closed, or update today's hours (holiday hours, early closing) — this feeds the "open now" filter
- **Detail view**: given a business ID, return full details (hours, photos, reviews summary, phone, website)

### Non-Functional Requirements
- **Low latency**: nearby-search queries should return in **under 100ms at p99**, even under filter combinations and at peak QPS
- **High read throughput**: must sustain **on the order of 100K QPS average, 500K QPS peak** for search (§2)
- **Dense and sparse region support**: must work equally well for a 2km radius search in Manhattan (tens of thousands of candidates) and a 50km radius search in rural Montana (a handful of candidates)
- **Listing-data freshness**: address, hours, category, and other slow-changing attributes can lag writes by **minutes** — this is acceptable because these fields change rarely
- **"Open now" freshness**: a business owner toggling "temporarily closed" (e.g., for a holiday or emergency) should be reflected in search results within **seconds to low tens of seconds** — this is the one field in the system with a genuinely tight freshness SLA, and it gets a dedicated design treatment (§4.5)
- **High availability**: search is a primary, revenue-driving read path — the system should degrade (e.g., serve slightly stale results) rather than fail outright
- **Global scale, regional locality**: businesses and searchers are both geographically distributed; a search in Tokyo should not need to query infrastructure in São Paulo

### Out of Scope
- **Turn-by-turn routing to a business** — once a user picks a result, getting directions there is the [Design Google Maps](./design_google_maps.md) routing problem (§4.5 there), not this one
- **Real-time driver/courier matching for delivery** — "is there a courier available to pick up from this restaurant" is [Design Uber](./design_uber.md)'s matching problem; this design covers *discovering* the restaurant, not dispatching to it
- **Reviews and review-ranking system** — review submission, moderation, and helpfulness-ranking are treated as an external system whose output (an aggregate rating and review count) this design consumes as an input signal (§4.3)
- **Payments, reservations, ordering** — transactional flows after a user picks a result are out of scope

---

## 2. Scale Estimation

### Business / Listing Volume
- **50 million businesses globally** (storefronts, restaurants, services, points of interest)
- Each listing's core attributes (name, category, address, lat/lng, price tier, hours, rating, photo references) average **~1KB** -> 50M x 1KB = **~50GB** of core attribute data — small enough to be fully replicated across regional search clusters
- Listing **writes** (new businesses, attribute edits) are low-rate: estimate **~500K listing updates/day** globally -> 500,000 / 86,400 ~= **~6 writes/sec average**, bursting to maybe 50/sec during business-hours peaks — three to four orders of magnitude below the read QPS below

### Search Query Volume
- **100,000 QPS average**, **500,000 QPS peak** (lunch-hour and evening peaks in major metro timezones, compounding across time zones at global scale)
- Each search returns a ranked top-20 (typical page size), but the **candidate set before ranking is much larger**: a 2km-radius search in a dense urban core can return **on the order of 1,000 candidates** from the geo-index before category/rating/open-now filters and ranking trim it to 20

### "Open Now" Status Updates
- Of the 50M businesses, estimate **~5% (2.5M)** actively toggle hours/closures with any regularity (most businesses have static hours that almost never change)
- Status-toggle events: estimate **~50,000/day** globally (holidays, emergencies, manual "closing early today" toggles) -> roughly **~0.6/sec average**, but these are the highest-priority writes in the system because of the tight freshness SLA (§1)

### Geo-Index Size
- Using geohash precision 6 (~1.2km x 0.6km cells, per the table in [Design Google Maps](./design_google_maps.md) §4.1) as the baseline candidate-retrieval precision: 50M businesses, each indexed under its geohash cell -> a geo-index entry of `(geohash_string, business_id, lat, lng)` at roughly **24 bytes/entry** -> 50M x 24 bytes ~= **~1.2GB** for the raw geo-index — trivially small, and the reason this layer can live entirely in memory (Redis or an in-memory geo-grid)

### Search-Index (Attributes) Size
- The richer per-business document used for filtering/ranking (category, price, rating, hours, description text for full-text matching, popularity signals) averages **~2KB/document** -> 50M x 2KB = **~100GB** — this is the Elasticsearch-style index, sharded across a cluster (§10)

---

## 3. High-Level Architecture

```
                              READ PATH (Search)
+----------+      +----------------+      +---------------------------+
|  Client  |----->|  API Gateway /  |----->|   Proximity Search Service |
| (lat,lng,|      |  Load Balancer  |      |   (orchestrator)           |
|  radius, |      +----------------+      +-------------+--------------+
|  filters)|                                             |
+----------+                            Phase 1: geo candidate retrieval
                                                          |
                                                          v
                                    +---------------------------------------+
                                    |   Geo-Index (Redis GEO / geo-grid)     |
                                    |  geohash cell -> set of business IDs   |
                                    |  in-memory, ~1.2GB total (§2)          |
                                    +-------------------+---------------------+
                                                          |
                                          candidate IDs (~100-1000, §2)
                                                          |
                                    Phase 2: attribute filter + ranking
                                                          v
                                    +---------------------------------------+
                                    |  Search/Attributes Index (Elasticsearch)|
                                    |  geo_point + category + price + rating |
                                    |  + text fields, ~100GB sharded (§2)    |
                                    +-------------------+---------------------+
                                                          |
                                       filtered, ranked top-20
                                                          |
                          +-------------------------------+------------------+
                          |                                                   |
                          v                                                   v
              +---------------------+                          +---------------------------+
              | Attributes Cache     |                          |  "Open Now" Status Cache    |
              | (long TTL: hours)    |                          |  (short TTL: 30-60s, §4.5)  |
              | name/hours/category  |                          |  webhook-invalidated on     |
              +---------------------+                          |  owner toggle               |
                                                                 +---------------------------+
                                                          |
                                                          v
                                                  +----------------+
                                                  |     Client      |
                                                  +----------------+


                              WRITE PATH (Listing Update)
+------------------+     +-------------------+     +------------------------+
| Business Owner /  |---->|  Listing Service   |---->|  Primary DB (source    |
| Admin CMS          |     |  (validation,      |     |  of truth, e.g.        |
| (create/update/    |     |   geocoding new    |     |  PostgreSQL)           |
|  close listing)    |     |   addresses)       |     +-----------+------------+
+------------------+     +-------------------+                 |
                                                                  |  CDC / outbox event
                                                                  v
                                                       +------------------------+
                                                       |   Message Queue (Kafka) |
                                                       |   listing_updates topic |
                                                       +-----------+------------+
                                                                  |
                                       +--------------------------+--------------------------+
                                       |                                                       |
                                       v                                                       v
                          +---------------------------+                       +---------------------------+
                          |  Geo-Index Updater          |                       |  Search-Index Updater       |
                          |  - on coordinate change:    |                       |  - re-index full document   |
                          |    GEOADD new cell, remove   |                       |    into Elasticsearch       |
                          |    from old cell             |                       |  - eventual consistency,    |
                          +---------------------------+                       |    seconds-to-minutes lag    |
                                                                                +---------------------------+

                              FAST PATH ("Open Now" toggle)
+------------------+     +-------------------+     +---------------------------+
| Business Owner    |---->|  Status Service    |---->|  Status Cache (Redis,      |
| toggles "closed"  |     |  (webhook/API)     |     |  TTL 30-60s) + pub/sub      |
| for today         |     +-------------------+     |  invalidation broadcast     |
+------------------+                                +---------------------------+
```

### Request Flow

1. **Phase 1 — Geo candidate retrieval**: the **Proximity Search Service** receives `(lat, lng, radius, filters)`, computes the appropriate geohash precision for `radius` (§4.4), and queries the **Geo-Index** for the center cell plus its 8 neighbors (§4.1), returning a set of candidate business IDs — typically 100-1,000 IDs for a dense-urban 2km search (§2).
2. **Phase 2 — Attribute filter + ranking**: those candidate IDs (or, more commonly in production, the geo-coordinates are pushed *into* the search index as a `geo_distance` filter rather than passed as an ID list — see §4.2) are filtered by category/price/rating/"open now" and scored by the ranking formula (§4.3) against the **Search/Attributes Index** (Elasticsearch), which returns the final ranked top-20.
3. **Caching**: business attribute data (name, hours, category, photos) is cached with a **long TTL (hours)** since it rarely changes; "open now" status is cached separately with a **short TTL (30-60 seconds)** and actively invalidated via webhook when a business owner toggles it (§4.5).
4. **Write path**: a listing create/update goes through the **Listing Service**, lands in the primary datastore, and propagates via CDC/outbox + Kafka to both the **Geo-Index Updater** (if coordinates changed) and the **Search-Index Updater** (re-indexes the full document) — both consumers are eventually consistent within seconds to low minutes (§4.6).
5. **Fast path**: an "open now" toggle bypasses the slow CDC pipeline entirely and writes directly to the short-TTL **Status Cache**, with a pub/sub invalidation broadcast so any cached copies elsewhere expire immediately (§4.5).

---

## 4. Component Deep Dives

### 4.1 Two-Phase Search: Geo-Index + Attribute Index

The foundational architectural decision is **splitting "where" from "what."** A single index that is simultaneously a spatial structure (good at "what's within X of this point") *and* a rich attribute/text index (good at "category=sushi AND rating>=4 AND price<=$$") is hard to build well — spatial indexes (R-trees, geohash grids) are not naturally good at boolean/numeric/text filtering, and text/attribute indexes (B-trees, inverted indexes) are not naturally good at radius queries.

The two-phase design instead uses:

- **Phase 1 (Geo-Index)**: a small, fast, in-memory structure whose *only* job is "given `(lat, lng, radius)`, return business IDs within that radius" — geohash-based (Redis GEO, §4.2) or a custom geo-grid. No attribute filtering happens here.
- **Phase 2 (Attribute/Search Index)**: a heavier index (Elasticsearch, §4.2) that holds the full business document — category, price, rating, hours, description text — and is queried with the Phase 1 candidates' geo-coordinates as a `geo_distance` filter *combined* with the attribute filters, in a single query.

In practice, the cleanest production implementation **doesn't literally pass a list of IDs from Phase 1 to Phase 2** — Elasticsearch's `geo_point` field type and `geo_distance` query (§4.2) can do the radius filtering itself. So why have a separate Phase 1 at all? Two reasons:

1. **Cheap pre-filtering for adaptive precision** (§4.4): the lightweight geo-index can quickly tell you "how many candidates exist in this radius" *before* you pay the cost of a full Elasticsearch query, which lets the orchestrator widen or narrow the search radius/precision adaptively.
2. **A fallback / simpler tier**: for deployments that don't want to run Elasticsearch (or for an internal low-traffic admin tool), the Redis GEO index alone can answer "nearby" queries with simple radius semantics, with attribute filtering done in application code on the smaller candidate set — slower and less flexible, but a real fallback path (§5).

#### `GeoRangeQuery` — Candidate Retrieval with Neighbor Expansion

The following class implements the candidate-retrieval phase: given `(lat, lng, radius)`, it picks a geohash precision whose cell size is comparable to the radius, computes the center cell and its 8 neighbors, queries an in-memory index for each, merges the results, and applies an exact haversine post-filter to discard candidates that fall within the 3x3 cell block but outside the true radius (the same neighbor-expansion technique [Design Google Maps](./design_google_maps.md) §4.4 and War Story 1 there document for POI search).

```java
package com.rutik.systemdesign.hld.case_studies.proximity;

import java.util.*;

public class GeoRangeQuery {

    /** Approximate cell width/height in meters for each geohash precision (1-9). */
    private static final double[] CELL_SIZE_METERS = {
        5_000_000, 1_250_000, 156_000, 39_100, 4_890, 1_220, 153, 38.2, 4.77
    };

    private final GeoIndex geoIndex; // backing store: geohash prefix -> Set<BusinessLocation>

    public GeoRangeQuery(GeoIndex geoIndex) {
        this.geoIndex = geoIndex;
    }

    /**
     * Returns business IDs within `radiusMeters` of (lat, lng), exact-filtered
     * by haversine distance. Precision is chosen so that the cell size is
     * comparable to the search radius (finer precision for smaller radii).
     */
    public List<Candidate> findNearby(double lat, double lng, double radiusMeters) {
        int precision = choosePrecision(radiusMeters);
        String centerHash = Geohash.encode(lat, lng, precision);

        // Center cell + 8 neighbors = 9-cell block. Guarantees that any point
        // within `radiusMeters` is covered, as long as radiusMeters does not
        // exceed roughly half the cell size (see §9 War Story 1 for what
        // happens if you skip this).
        Set<String> cellsToQuery = new HashSet<>();
        cellsToQuery.add(centerHash);
        cellsToQuery.addAll(Geohash.neighbors(centerHash));

        Set<BusinessLocation> rawCandidates = new HashSet<>();
        for (String cell : cellsToQuery) {
            rawCandidates.addAll(geoIndex.getByGeohashPrefix(cell));
        }

        List<Candidate> result = new ArrayList<>();
        for (BusinessLocation biz : rawCandidates) {
            double distanceMeters = haversineMeters(lat, lng, biz.lat(), biz.lng());
            if (distanceMeters <= radiusMeters) {
                result.add(new Candidate(biz.businessId(), distanceMeters));
            }
        }
        result.sort(Comparator.comparingDouble(Candidate::distanceMeters));
        return result;
    }

    /** Pick the coarsest precision whose cell size is still >= 2x the radius,
     *  so the 9-cell block fully covers the search circle. */
    private int choosePrecision(double radiusMeters) {
        for (int precision = 1; precision <= 9; precision++) {
            if (CELL_SIZE_METERS[precision - 1] <= radiusMeters * 2) {
                return Math.max(1, precision - 1);
            }
        }
        return 9;
    }

    /** Haversine great-circle distance in meters. */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS_M = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    public record Candidate(String businessId, double distanceMeters) {}
    public record BusinessLocation(String businessId, double lat, double lng) {}

    /** Minimal abstraction over the backing geo-index (Redis GEO, in-memory map, etc). */
    public interface GeoIndex {
        Set<BusinessLocation> getByGeohashPrefix(String geohashPrefix);
    }
}
```

The haversine formula computes great-circle distance between two `(lat, lng)` points:

```
a = sin²(Δlat/2) + cos(lat1) · cos(lat2) · sin²(Δlng/2)
c = 2 · atan2(√a, √(1−a))
distance = R · c    (R = Earth's radius ≈ 6,371 km)
```

This is the **exact-distance post-filter** every geohash-based system needs — geohash cells are square approximations, so a candidate inside the 3x3 cell block might still be farther than `radiusMeters` from the query point (e.g., near a cell's corner), and the haversine check discards those false positives.

### 4.2 Backend Choices: Redis GEO vs. PostGIS vs. Elasticsearch

This is the central engineering decision for a proximity search system, and unlike many "it depends" tradeoffs, the three options map cleanly onto three different points on the simplicity-vs-capability spectrum.

#### Redis GEO (`GEOADD` / `GEOSEARCH`)

Redis stores geo-coordinates as a 52-bit interleaved geohash inside a sorted set, giving radius and "k-nearest" queries almost for free:

```bash
# Add businesses with their coordinates
GEOADD businesses -122.4194 37.7749 "biz:sf_cafe_1"
GEOADD businesses -122.4180 37.7755 "biz:sf_cafe_2"

# Find all businesses within 2km of a point, sorted by distance, max 50 results
GEOSEARCH businesses FROMLONLAT -122.4190 37.7750 BYRADIUS 2 km ASC COUNT 50

# Response includes distances:
# 1) "biz:sf_cafe_1"  0.0623
# 2) "biz:sf_cafe_2"  0.0891
```

**Strengths**: in-memory, sub-millisecond, trivially simple to operate, built-in radius and distance sorting. **Weaknesses**: no native support for filtering by category/price/rating/text — those filters require fetching candidate IDs from `GEOSEARCH` and then doing a second lookup (e.g., `HGETALL biz:sf_cafe_1`) per candidate to check attributes, which doesn't scale gracefully past a few hundred candidates. Redis GEO is best as **Phase 1 only**, or as the entire solution for a simple "nearby" feature with no filters (e.g., "find the 3 nearest warehouses" for logistics).

#### PostgreSQL + PostGIS

PostGIS adds geometry/geography types and **GiST-indexed** (R-tree-like) spatial queries to Postgres, enabling exact-distance radius queries combined with arbitrary SQL `WHERE` clauses:

```sql
SELECT business_id, name, category, rating,
       ST_Distance(location, ST_MakePoint(-122.4190, 37.7750)::geography) AS distance_m
FROM businesses
WHERE ST_DWithin(
        location,
        ST_MakePoint(-122.4190, 37.7750)::geography,
        2000  -- 2km in meters
      )
  AND category = 'cafe'
  AND price_tier <= 2
  AND rating >= 4.0
ORDER BY distance_m ASC
LIMIT 20;
```

**Strengths**: exact distances (no geohash approximation error), the *full power of SQL* for filters and joins (rating from a separate `reviews` table, inventory from another table — all in one query), strong consistency (a write is immediately visible to the next read, no eventual-consistency lag). **Weaknesses**: full-text relevance scoring ("best ramen") is weak compared to a dedicated search engine; a GiST index combined with several B-tree indexes on the same table, under 500K QPS peak (§2), requires either a very large read-replica fleet or aggressive caching — PostGIS scales well into the tens-of-thousands-of-QPS range per well-tuned replica, but 500K QPS peak (§2) typically pushes toward Elasticsearch or a cache-heavy architecture in front of PostGIS.

#### Elasticsearch `geo_point` + `geo_distance`

Elasticsearch documents include a `geo_point` field, and a single query combines geo-filtering, attribute filtering, full-text relevance, and custom scoring:

```json
{
  "query": {
    "bool": {
      "filter": [
        { "geo_distance": { "distance": "2km", "location": { "lat": 37.7750, "lon": -122.4190 } } },
        { "term": { "category": "cafe" } },
        { "range": { "rating": { "gte": 4.0 } } },
        { "term": { "open_now": true } }
      ],
      "should": [
        { "match": { "name": "best ramen" } }
      ]
    }
  },
  "sort": [
    { "_geo_distance": { "location": { "lat": 37.7750, "lon": -122.4190 }, "order": "asc", "unit": "km" } },
    "_score"
  ]
)
```

**Strengths**: this is the *only* one of the three that natively combines geo-filtering, structured filters, full-text relevance, and custom ranking formulas (§4.3) in **one query** — exactly the "search businesses near me with filters and ranking" problem statement. Geo-sharding (documents bucketed by geohash precision into shards) keeps each shard's working set manageable even at 50M documents / 100GB (§2). **Weaknesses**: eventual consistency — a write isn't searchable until the next refresh interval (default ~1 second, often tuned to several seconds in high-write clusters for indexing throughput); not a system of record (Elasticsearch should sit alongside, not instead of, a primary datastore like PostgreSQL, §4.6); operationally heavier (cluster management, shard rebalancing, JVM heap tuning).

#### Why Elasticsearch Is Usually the Production Choice Here

For *this specific problem* — "search nearby places with filters and ranking, read-heavy, at 100K-500K QPS" — Elasticsearch wins because **the filter+rank combination is the hard part**, not the geo-radius part in isolation. Redis GEO nails the geo-radius part but can't filter; PostGIS nails exact distance and SQL filters but its relevance/ranking story and QPS ceiling are weaker than a dedicated search engine's. The pattern many production systems converge on (§6) is: **PostgreSQL/PostGIS as the system of record** (strong consistency for writes, used by the Listing Service), **Elasticsearch as the read-optimized search index** (eventually consistent, used by the Search Service), connected by CDC (§4.6) — getting PostGIS's consistency for writes and Elasticsearch's filter+rank power for reads, at the cost of the eventual-consistency window discussed in §5.

### 4.3 Ranking Formula

A nearby-search result list is never sorted by distance alone — a mediocre restaurant 200m away usually shouldn't outrank an excellent one 600m away. The ranking score combines several normalized signals into one weighted sum:

```
score = w_distance * distance_score
      + w_rating   * rating_score
      + w_relevance * relevance_score
      + w_boost    * boost_score
```

| Signal | Normalization | Typical Weight |
|---|---|---|
| **distance_score** | `1 - (distance_km / radius_km)`, clamped to `[0, 1]` — closer is higher | `w_distance = 0.40` |
| **rating_score** | `(rating - 1) / 4` mapping a 1-5 star rating to `[0, 1]` | `w_rating = 0.30` |
| **relevance_score** | Elasticsearch's normalized `_score` for the text query (e.g., "ramen" matching name/category/description), scaled to `[0, 1]` | `w_relevance = 0.25` |
| **boost_score** | `1.0` for a sponsored/promoted listing, `0.0` otherwise | `w_boost = 0.05` |

#### Worked Example

Two ramen restaurants, both matching a "ramen" search within a 2km radius:

| Business | Distance | Rating | Relevance (text match) | Sponsored? |
|---|---|---|---|---|
| A: "Ichiraku Ramen" | 0.4 km | 4.7 | 0.95 (exact name match) | No |
| B: "Noodle House" | 1.8 km | 4.9 | 0.60 ("noodle" partial match) | Yes |

```
A: distance_score = 1 - (0.4/2)  = 0.80
   rating_score   = (4.7-1)/4    = 0.925
   relevance_score = 0.95
   boost_score     = 0.0

   score_A = 0.40*0.80 + 0.30*0.925 + 0.25*0.95 + 0.05*0.0
           = 0.320 + 0.2775 + 0.2375 + 0
           = 0.835

B: distance_score = 1 - (1.8/2)  = 0.10
   rating_score   = (4.9-1)/4    = 0.975
   relevance_score = 0.60
   boost_score     = 1.0

   score_B = 0.40*0.10 + 0.30*0.975 + 0.25*0.60 + 0.05*1.0
           = 0.040 + 0.2925 + 0.150 + 0.05
           = 0.5325
```

Despite B having a higher star rating *and* a sponsorship boost, A wins decisively (`0.835` vs. `0.5325`) — driven almost entirely by `w_distance = 0.40` and A being 4.5x closer. This illustrates the central tuning risk: **if `w_distance` is too high, the ranking degenerates into "just sort by distance," which defeats the purpose of having rating and relevance signals at all** — a 5-star restaurant 1.9km away would never outrank a 2-star one 100m away, no matter how bad the 2-star place is. Conversely, if `w_distance` is too low, users get results that are "technically the best match" but a 40-minute walk away, which feels broken for a "near me" query. Most production systems tune `w_distance` empirically via A/B testing against click-through and "did the user actually visit" conversion signals, and many also apply a **hard distance cutoff** (e.g., never show results beyond 1.5x the requested radius regardless of score) as a backstop against the weighting alone producing a far-away top result.

### 4.4 Dense vs. Sparse Regions — Adaptive Precision Selection

A **fixed** geohash precision breaks down at both ends of the density spectrum:

- **Dense urban core** (Manhattan, central Tokyo): at geohash precision 6 (~1.2km x 0.6km cells), a 9-cell block covers roughly 3.2km x 1.8km — in Manhattan that block can contain **tens of thousands** of businesses. Phase 2 (Elasticsearch) then has to filter/rank a candidate set that's 10-50x larger than the §2 estimate of ~1,000, increasing latency.
- **Sparse rural region** (rural Montana): the same precision-6 9-cell block might contain **zero or one** business, even though the user's requested radius (say 50km, reasonable for "nearest hardware store" in a rural area) would need a much coarser precision to find anything at all.

The fix is **adaptive precision selection with candidate-count feedback**:

```java
public class AdaptivePrecisionSelector {
    private static final int MIN_CANDIDATES = 20;   // need at least this many before ranking
    private static final int MAX_CANDIDATES = 2000; // beyond this, Phase 2 latency suffers

    private final GeoRangeQuery geoQuery;

    public AdaptivePrecisionSelector(GeoRangeQuery geoQuery) {
        this.geoQuery = geoQuery;
    }

    /**
     * Starts from the precision implied by the user's requested radius, then
     * adjusts based on actual candidate density before handing off to Phase 2.
     */
    public List<GeoRangeQuery.Candidate> retrieveCandidates(double lat, double lng, double radiusMeters) {
        double effectiveRadius = radiusMeters;
        List<GeoRangeQuery.Candidate> candidates = geoQuery.findNearby(lat, lng, effectiveRadius);

        // Sparse region: widen the radius (coarser precision) until enough
        // candidates exist, or a sane max radius is hit.
        int expansions = 0;
        while (candidates.size() < MIN_CANDIDATES && effectiveRadius < radiusMeters * 8 && expansions < 4) {
            effectiveRadius *= 2;
            candidates = geoQuery.findNearby(lat, lng, effectiveRadius);
            expansions++;
        }

        // Dense region: too many candidates for Phase 2 to rank cheaply.
        // Narrow the radius (finer precision) — the UI can offer "expand search area"
        // if the user wants the wider, slower result set.
        while (candidates.size() > MAX_CANDIDATES && effectiveRadius > radiusMeters / 4) {
            effectiveRadius *= 0.5;
            candidates = geoQuery.findNearby(lat, lng, effectiveRadius);
        }

        return candidates;
    }
}
```

The key behaviors:

- **Sparse fallback**: if the initial radius yields fewer than `MIN_CANDIDATES` (20), double the effective radius (up to 8x the original, capped at 4 expansions) — this is the same "expand ring by ring" idea [Design Google Maps](./design_google_maps.md) §11 describes for "find the nearest gas station" in rural areas.
- **Dense throttle**: if the initial radius yields more than `MAX_CANDIDATES` (2,000), shrink the effective radius — better to return a smaller, faster, still-relevant result set than to push 50,000 candidates into Elasticsearch's scoring phase. The original requested radius is preserved in the response metadata so the UI can offer "show more results further away" as an explicit user action rather than silently returning a huge unranked blob.
- This adaptive layer sits in front of Phase 2 precisely because **the lightweight geo-index (§4.1) is cheap enough to query repeatedly** (a few extra Redis `GEOSEARCH` calls cost low single-digit milliseconds total) — doing this same trial-and-error against Elasticsearch directly would be far more expensive per iteration.

### 4.5 "Open Now" Freshness — Split-TTL Caching

The core tension: **most business attributes are essentially static** (a restaurant's category, price tier, and street address almost never change), but **operating status can change at any moment** (a restaurant closes early for a private event, a store has a power outage, a holiday closure is announced same-day). Caching both with the same TTL forces a bad tradeoff — a long TTL (good for the static 99% of fields) makes "open now" dangerously stale (War Story 2, §9); a short TTL (good for "open now") means re-fetching the static 99% of the document from Elasticsearch far more often than necessary, wasting capacity.

The fix is **two caches with different TTLs and different invalidation strategies**:

| Cache | Contents | TTL | Invalidation |
|---|---|---|---|
| **Attributes Cache** | name, address, category, price tier, photos, description, *regular* hours-of-week | Hours (e.g., 6-24h) | Passive expiry, or active invalidation on listing update (§4.6) — low frequency, no urgency |
| **Status Cache** | `open_now` boolean, today's effective hours (accounting for holiday/temporary overrides), "temporarily closed" flag | **30-60 seconds** | Passive expiry **and** active invalidation via webhook/pub-sub when an owner toggles status |

```java
public class OpenNowStatusResolver {
    private static final long STATUS_CACHE_TTL_SECONDS = 45;

    private final Cache<String, BusinessStatus> statusCache; // short TTL
    private final Cache<String, BusinessAttributes> attributesCache; // long TTL
    private final StatusStore statusStore; // small table: business_id -> overrides

    public OpenNowStatusResolver(Cache<String, BusinessStatus> statusCache,
                                  Cache<String, BusinessAttributes> attributesCache,
                                  StatusStore statusStore) {
        this.statusCache = statusCache;
        this.attributesCache = attributesCache;
        this.statusStore = statusStore;
    }

    /** Called on a status toggle webhook — invalidates immediately rather than waiting on TTL. */
    public void onStatusToggle(String businessId, boolean temporarilyClosed) {
        statusStore.upsertOverride(businessId, temporarilyClosed);
        statusCache.invalidate(businessId); // next read recomputes from statusStore
        // also publish to a pub/sub channel so other regional caches invalidate too
    }

    /** Computed on read if not cached; cheap because the status table is tiny (§10). */
    public boolean isOpenNow(String businessId, long nowEpochSeconds) {
        BusinessStatus cached = statusCache.getIfPresent(businessId);
        if (cached != null && !cached.isExpired(nowEpochSeconds)) {
            return cached.openNow();
        }
        BusinessAttributes attrs = attributesCache.get(businessId); // long-TTL, regular hours
        boolean override = statusStore.isTemporarilyClosed(businessId);
        boolean openByRegularHours = attrs.hoursOfWeek().isOpenAt(nowEpochSeconds);
        boolean openNow = openByRegularHours && !override;

        statusCache.put(businessId, new BusinessStatus(openNow, nowEpochSeconds + STATUS_CACHE_TTL_SECONDS));
        return openNow;
    }

    public record BusinessStatus(boolean openNow, long expiresAtEpochSeconds) {
        public boolean isExpired(long now) { return now >= expiresAtEpochSeconds; }
    }

    public interface StatusStore {
        void upsertOverride(String businessId, boolean temporarilyClosed);
        boolean isTemporarilyClosed(String businessId);
    }
}
```

The **status table** (`StatusStore`) holding `business_id -> temporarily_closed override` is deliberately tiny — only the ~2.5M businesses that ever toggle status (§2) need entries, and most entries are short-lived (a holiday override expires after the holiday). This table can be a small, fast key-value store separate from the 100GB main search index — keeping it small is what makes "compute on read if cache miss" cheap enough to use as the fallback for a 45-second TTL cache.

### 4.6 Write Path: Listing Update Propagation (CDC vs. Dual-Write)

A listing update (new business, address change, category edit) must reach both the **Geo-Index** (§4.1, if coordinates changed) and the **Search/Attributes Index** (§4.2, for the new attribute values). Two ways to propagate:

**Dual-write**: the Listing Service, after writing to the primary database, *also* directly calls `GEOADD`/`GEODEL` on Redis and issues an Elasticsearch index/update API call, all within the same request.

**CDC (Change Data Capture)**: the Listing Service writes only to the primary database (e.g., PostgreSQL). A CDC connector (e.g., Debezium-style, reading the database's write-ahead log) or an **outbox table** pattern emits change events to Kafka, and separate consumers (`Geo-Index Updater`, `Search-Index Updater`) apply those events to Redis and Elasticsearch asynchronously.

```java
public class ListingUpdateHandler {

    private final PrimaryDatastore primaryDb;     // PostgreSQL — system of record
    private final OutboxWriter outboxWriter;       // writes to an outbox table in the same transaction

    public ListingUpdateHandler(PrimaryDatastore primaryDb, OutboxWriter outboxWriter) {
        this.primaryDb = primaryDb;
        this.outboxWriter = outboxWriter;
    }

    /** Single transaction: update the listing AND enqueue the propagation event atomically. */
    public void updateListing(ListingUpdate update) {
        primaryDb.runInTransaction(tx -> {
            tx.upsertListing(update);
            outboxWriter.enqueue(tx, new ListingChangedEvent(
                update.businessId(),
                update.coordinatesChanged(),
                update.toDocument() // full denormalized doc for Elasticsearch re-index
            ));
        });
        // A separate poller/CDC process reads the outbox table and publishes to Kafka,
        // guaranteeing the event is emitted if and only if the DB transaction committed.
    }

    public record ListingChangedEvent(String businessId, boolean coordinatesChanged, Object document) {}
}
```

The **outbox pattern** (cross-ref [`../distributed_transactions/README.md`](../distributed_transactions/README.md)) is the key correctness mechanism: writing to the primary DB and enqueueing the propagation event happen in the **same transaction**, so there's no window where the DB commits but the event is lost (or vice versa) — a failure mode that plain dual-write is exposed to (if the Elasticsearch call fails after the DB write succeeds, the two stores silently diverge with no record that anything went wrong).

Downstream, the **Geo-Index Updater** only acts when `coordinatesChanged` is true (the common case — category/price/hours edits don't move the business on the map): it issues `GEOADD` for the new coordinates and `ZREM` for the old geohash entry. The **Search-Index Updater** re-indexes the full document into Elasticsearch on every change, relying on Elasticsearch's near-real-time refresh (typically 1-5 seconds) for the update to become searchable. End-to-end, a listing edit becomes visible in search results within **low single-digit seconds to a couple of minutes** under load — well within the "minutes" freshness NFR from §1, and entirely decoupled from the tight "open now" path (§4.5), which deliberately bypasses this pipeline.

### 4.7 Regional Sharding and Multi-Region Deployment

Both the geo-index (§4.1) and the search index (§4.2) are partitioned **geographically**, not by a hash of business ID — this is the single most important infrastructure decision for keeping latency under the 100ms p99 target (§1) at global scale.

**Why geographic partitioning, not hash-based**: a search for "coffee near me" in São Paulo never needs to consider businesses in Tokyo. If shards were assigned by `hash(business_id) % num_shards` (the typical default for a generic key-value workload), every search query would need to fan out to *every* shard, because any shard could hold a business near the query point. Partitioning instead by **geohash prefix** (or a coarser region ID derived from it) means a query for `(lat, lng, radius)` only needs to touch the **1-3 shards covering that geographic area** — the same 9-cell neighbor expansion from §4.1 typically spans at most 2-3 shards even near a shard boundary, versus needing to query all 20+ shards under hash partitioning.

```
                    Global Request Router
                    (routes by lat/lng -> region)
                              |
        +---------------------+---------------------+
        |                     |                      |
        v                     v                      v
+----------------+   +----------------+    +----------------+
|  US-East Region  |   |  EU-West Region |    |  APAC Region     |
|  - Geo-index     |   |  - Geo-index    |    |  - Geo-index     |
|    shard(s)      |   |    shard(s)     |    |    shard(s)      |
|  - Search shards |   |  - Search shards|    |  - Search shards |
|    (NYC, Boston,  |   |    (London,     |    |    (Tokyo,        |
|     Atlanta...)   |   |     Paris...)   |    |     Singapore...) |
+----------------+   +----------------+    +----------------+
```

**Boundary-crossing queries**: a search near a regional boundary (e.g., a user in El Paso, Texas, searching with a 50km radius that spans into Mexico, served by a different region's shard) is handled the same way as the 9-cell neighbor-expansion problem (§4.1) but one level up — the **Global Request Router** identifies all regions whose coverage area intersects the query's bounding circle and fans out to each, merging results before applying the final ranking (§4.3). This is rare in practice (most 1-50km radius searches stay within one region's coverage) but must be handled correctly, since "missing results near a national border" is the same class of support-ticket-generating bug as War Story 1's cell-boundary problem, just at a coarser granularity.

**Replication for read availability**: because this system is overwhelmingly read-heavy (§2: ~500K QPS peak reads vs. ~6.6 writes/sec, §10), each region's search-index shards run with a replication factor of 2-3 (§10) primarily for **read-throughput scaling and availability**, not write durability — a replica falling behind by a few seconds of indexing lag is invisible to users, but a replica being unavailable would directly reduce read capacity at peak.

### 4.8 Personalization and Re-Ranking Signals

The ranking formula in §4.3 produces a single global ranking for a given query — but the same query ("coffee near me") from two different users often should *not* return identical results: a user with a strong order-history affinity for a specific chain, or with a dietary preference reflected in past activity, benefits from a final **personalization re-rank** pass applied after Phase 2's ranked candidate list (§3) is produced.

**Architecture**: personalization is a thin re-ranking layer **on top of**, not a replacement for, the two-phase pipeline (§3-4.3). Phase 1+2 still produce a geographically- and attribute-filtered candidate list ranked by the base formula (§4.3); a separate **personalization service** then re-orders only the top ~50-100 candidates — never the full candidate set, since re-ranking a small top-K is cheap but re-ranking thousands of per-query candidates is not — using per-user signals.

```
Phase 1 (geo, §4.1)  ->  Phase 2 (filter+rank, §4.2-4.3)  ->  top ~50-100 candidates
                                                                      |
                                                                      v
                                              Personalization Re-Rank Service
                                              (per-user signals: order history,
                                               cuisine/category affinity)
                                                                      |
                                                                      v
                                                          final top-20 to client
```

**Blending base score with personalization score**:

```java
package com.rutik.systemdesign.hld.case_studies.proximity;

/**
 * Re-ranks the top-K base-ranked candidates (§4.3) using a per-user
 * affinity score, blended with a configurable weight so personalization
 * never fully overrides geographic relevance.
 */
public class PersonalizationReRanker {

    private final double personalizationWeight; // e.g., 0.2 -> 20% of final score

    public PersonalizationReRanker(double personalizationWeight) {
        this.personalizationWeight = personalizationWeight;
    }

    /**
     * @param baseScore      Section 4.3's blended distance+rating+relevance
     *                       score, normalized to [0, 1]
     * @param affinityScore  per-user affinity for this business's
     *                       category/chain, normalized to [0, 1]
     *                       (0.5 = neutral / no signal, e.g., a new user)
     */
    public double finalScore(double baseScore, double affinityScore) {
        return (1 - personalizationWeight) * baseScore
             + personalizationWeight * affinityScore;
    }
}
```

**Cold-start handling**: a new user with no order history has no affinity signal, so `affinityScore` defaults to a neutral `0.5`. The blend formula then reduces to `finalScore = (1 - w) * baseScore + w * 0.5` — a small constant shift that does not change the *relative* ordering of candidates. Personalization therefore degrades gracefully to "pure base ranking" (§4.3) for new users rather than producing an undefined or effectively-random re-rank — an important property, since cold-start users are disproportionately likely to be evaluating the product for the first time.

**Why re-rank only the top-K, not the full candidate set**: Phase 2 (§4.2) already filters and ranks against the full attribute index, which can return hundreds to low-thousands of candidates within a search radius in dense areas (§4.4). Fetching a per-user affinity score for every one of those candidates — a lookup against a user-profile store, potentially involving a model-inference call — at the QPS levels in §2 would multiply the read load on the personalization service by the candidate-set size. Restricting re-ranking to the top ~50-100 candidates (already the most geographically- and attribute-relevant results from §4.3) bounds the personalization service's per-query cost to a small, constant number of lookups regardless of how many total candidates Phase 2 considered.

**A/B testing the personalization weight**: `personalizationWeight` is a natural A/B-test lever — cross-ref [`../consistent_hashing/README.md`](../consistent_hashing/README.md) for the consistent-hash-based experiment-bucketing technique (§11) used to assign users to weight variants (e.g., 0%, 10%, 20%) and measure the impact on downstream engagement metrics (click-through rate, conversion) without touching the geo-index or search-index infrastructure at all. Personalization re-ranking is deliberately isolated as a final, swappable stage precisely so that experiments here can't destabilize Phase 1/Phase 2 (§3).

---

## 5. Design Decisions & Tradeoffs

### Geo Backend: Redis GEO vs. PostGIS vs. Elasticsearch

| Dimension | Redis GEO | PostgreSQL + PostGIS | Elasticsearch `geo_point` |
|---|---|---|---|
| Distance accuracy | Geohash-approximate (52-bit, very precise in practice) + exact distance returned by `GEOSEARCH` | Exact (geography type, ellipsoidal calculations) | Geohash-bucketed for filtering, exact for sort/scoring |
| Attribute filtering | None natively — app-side post-filter on candidates | Full SQL `WHERE` — arbitrary joins, ranges, text via `pg_trgm`/`tsvector` | Native — combined `bool` query with geo + term + range + full-text |
| Full-text relevance ranking | None | Basic (`tsvector`/`ts_rank`), weaker than a dedicated search engine | Best-in-class — BM25 scoring, custom `function_score` |
| Write consistency | Immediate (single command) | Immediate (ACID transaction) | Eventually consistent (refresh interval, typically 1-5s) |
| Operational complexity | Low — single Redis cluster | Medium — standard relational DB ops + GiST index tuning | High — cluster/shard management, JVM tuning |
| QPS ceiling (single tier) | Very high (in-memory) — easily 100K+ QPS per well-sized cluster | Tens of thousands QPS per replica; scales via read replicas | High — scales horizontally via shards/replicas, designed for this load |
| Best fit in this design | Phase 1 candidate retrieval (§4.1); simple "nearest N" with no filters | System of record for listings (§4.6); admin/internal tools needing exact SQL filters | Phase 2 production search (§4.2) — the primary "search nearby with filters" backend |

### Fixed vs. Adaptive Geohash Precision

| Dimension | Fixed Precision | Adaptive Precision (§4.4) |
|---|---|---|
| Implementation complexity | Simple — one precision level for all queries | Moderate — requires a feedback loop (count candidates, adjust, re-query) |
| Dense-urban behavior | Over-large candidate sets (10-50x target) at coarse precision, or missed neighbors at fine precision if radius > cell size | Automatically narrows until candidate count is in the target range (§4.4) |
| Sparse-rural behavior | Empty results if precision is too fine for the data density | Automatically widens (ring expansion) until enough candidates are found |
| Query cost | One geo-index lookup | Up to ~4-5 geo-index lookups in the worst case — still cheap since Phase 1 is in-memory (§4.1) |
| Recommended for | Prototypes, or domains with genuinely uniform density (rare globally) | Production systems spanning both dense-urban and sparse-rural deployments |

### Dual-Write vs. CDC for Index Consistency

| Dimension | Dual-Write | CDC / Outbox (§4.6) |
|---|---|---|
| Consistency guarantee | None by default — partial-failure can silently desync the two stores | Strong — outbox table + DB transaction guarantees the event is emitted iff the write committed |
| Latency to searchable | Can be near-immediate (synchronous second write) | Seconds, bounded by CDC lag + Elasticsearch refresh interval |
| Failure handling | Requires manual reconciliation/backfill jobs to detect and fix drift | Built-in retry via Kafka consumer; drift is structurally much harder to introduce |
| Coupling | Listing Service must know about and call every downstream index directly | Listing Service only writes to its own DB — downstream consumers are decoupled, can be added/removed independently |
| Recommended for | Never as the sole mechanism for production-critical indexes; acceptable for low-stakes denormalization | Production default for this design (§4.6) |

---

## 6. Real-World Implementations

- **Yelp**: Yelp's search infrastructure is one of the most publicly documented examples of exactly this architecture — Elasticsearch indexes holding business documents with `geo_point` fields, combined with category/price/rating filters and a custom relevance-scoring pipeline (Yelp has written extensively about migrating from Solr to Elasticsearch and about their multi-signal ranking, which blends distance, rating, review recency, and personalization signals much like §4.3's formula).
- **Google Places API**: exposes "Nearby Search" and "Text Search" endpoints that accept `(location, radius)` plus type/keyword filters, returning results ranked by a combination of "prominence" (Google's relevance/popularity signal) and distance — functionally the same two-input ranking tradeoff as §4.3, exposed as a public API product. The underlying index is part of the same S2-based geospatial infrastructure described in [Design Google Maps](./design_google_maps.md) §4.1/§4.4.
- **Foursquare / Swarm**: Foursquare pioneered large-scale venue check-in and "nearby venues" search at a time when geohash-based candidate retrieval plus a separate venue-attributes store was a novel architecture; their venue database (tens of millions of venues) and category taxonomy became an industry-referenced dataset for "what counts as a place" classification, directly informing the category-filter design in §1/§4.3.
- **Uber Eats / DoorDash restaurant discovery**: both layer a proximity-search problem (restaurants within delivery range) on top of an *availability* constraint that changes much faster than restaurant attributes — "is this restaurant currently accepting orders" is functionally identical to this design's "open now" problem (§4.5), and both companies' discovery surfaces combine distance, rating, delivery-time estimates, and promoted placements into a ranking formula structurally similar to §4.3's.
- **Redis GEO commands**: `GEOADD`/`GEOSEARCH`/`GEORADIUS` (used by [Design Uber](./design_uber.md) §4 for driver-location indexing) are widely adopted as the Phase 1 building block (§4.1/§4.2) across many smaller-scale "nearby" features — their ubiquity is precisely because Phase 1's job (cheap radius candidate retrieval) is a narrow, well-solved problem that doesn't require a bespoke implementation.

---

## 7. Technologies & Tools

| Component | Representative Technologies | Notes |
|---|---|---|
| Geo-index (Phase 1) | Redis (`GEOADD`/`GEOSEARCH`), or a custom in-memory geohash-bucketed map | §4.1, §4.2 — ~1.2GB for 50M businesses at precision 6 |
| Search/attributes index (Phase 2) | Elasticsearch / OpenSearch with `geo_point` fields | §4.2 — primary production choice; ~100GB sharded (§2, §10) |
| System of record | PostgreSQL + PostGIS | §4.6 — strong-consistency store for listing writes; PostGIS used for admin tooling and as the CDC source |
| CDC / event propagation | Debezium-style CDC, or outbox table + poller | §4.6 — feeds Kafka |
| Message queue | Kafka (`listing_updates` topic) | §3, §4.6 — cross-ref [`../message_queues/README.md`](../message_queues/README.md) |
| Attributes cache (long TTL) | Redis / Memcached, TTL hours | §4.5 — cross-ref [`../caching/README.md`](../caching/README.md) |
| Status cache (short TTL) | Redis, TTL 30-60s, pub/sub invalidation | §4.5 |
| Status override store | Small key-value table (DynamoDB-style or a small Postgres table) | §4.5, §10 — ~2.5M entries |

### Build vs. Buy Considerations

| Component | Build | Buy / Open-Source | This Design's Choice |
|---|---|---|---|
| Phase 1 geo-index | Custom geohash-bucketed in-memory map | Redis GEO commands | Buy — Redis GEO is mature, fast, and the "build" version offers little advantage for a well-understood radius-query problem |
| Phase 2 search index | Custom inverted index + geo-filter integration | Elasticsearch / OpenSearch | Buy — building a competitive full-text + geo + scoring engine from scratch is a multi-year effort; Elasticsearch is the industry-standard choice for exactly this combination (§4.2, §6) |
| Ranking formula | Custom weighted-sum (§4.3), tuned via A/B testing | Elasticsearch `function_score` / learning-to-rank plugins | Build the *formula and weights* (business logic, A/B-tuned), but implement it via Elasticsearch's scoring primitives rather than post-processing in application code — keeps scoring co-located with filtering |
| CDC pipeline | Custom outbox poller | Debezium, managed CDC services | Either — the outbox *pattern* (§4.6) is the important part; the poller implementation is commodity |

---

## 8. Operational Playbook

### Key Metrics

| Metric | What It Measures | Alert Threshold (Illustrative) |
|---|---|---|
| **Search p99 latency** | End-to-end nearby-search response time | Page if p99 > 100ms sustained over 5 minutes (§1 NFR) |
| **Phase 1 candidate count distribution** | Are adaptive-precision expansions/contractions (§4.4) firing too often | Investigate if >10% of queries require >2 expansion/contraction iterations — may indicate the baseline precision needs retuning |
| **Elasticsearch indexing lag** | Time from listing-update event (Kafka) to document searchable in Elasticsearch | Page if p99 lag > 5 minutes (§1 listing-freshness NFR) |
| **Status cache hit rate** | Fraction of "open now" checks served from the 30-60s status cache vs. computed on read | Investigate if hit rate drops below ~80% — may indicate cache-sizing or TTL misconfiguration |
| **Status invalidation webhook failure rate** | Owner-toggle events that failed to invalidate the status cache | Page if >1% — directly threatens the "open now" freshness SLA (§1, War Story 2) |
| **Geo-index / search-index drift** | Periodic reconciliation job comparing a sample of businesses' coordinates between Redis and Elasticsearch | Investigate if drift rate exceeds ~0.1% of sampled businesses — indicates a CDC pipeline issue (§4.6) |

### Runbook: Search Latency Spike

1. Check whether the spike correlates with **Phase 1 candidate-count growth** (§4.4) — a sudden increase in average candidate set size (e.g., from a popular event drawing a crowd into one geohash cell) pushes more work into Phase 2's filter/rank step.
2. If isolated to a specific geographic region, check whether the **adaptive precision selector** (§4.4) is hitting its `MAX_CANDIDATES` throttle correctly — if not, Elasticsearch may be scoring 10-50x more documents than intended for that region's queries.
3. Check Elasticsearch cluster health (shard balance, JVM GC pauses, node CPU) — a single hot shard (often the shard covering a dense metro area) can dominate p99 latency even when cluster-wide averages look fine.
4. If the attributes cache (§4.5) hit rate has dropped, check for a recent mass listing-update event (e.g., a bulk CMS import) that may have invalidated a large fraction of cached entries simultaneously.

### Runbook: "Open Now" Freshness Alert

1. Check the **status invalidation webhook failure rate** (§8 metrics) first — a failing webhook means owner toggles aren't propagating, and affected businesses will show stale status until their 30-60s cache entry naturally expires (bounded, but check whether the failure is ongoing).
2. If webhooks are healthy but freshness is still degraded, check the **status cache TTL configuration** — verify it hasn't been accidentally changed to a longer value (this is exactly War Story 2's failure mode, §9).
3. Check the **status override store** (§4.5, §10) for write errors or replication lag — if owner toggles aren't reaching the store at all, both the webhook invalidation *and* the on-read recomputation will return stale data.
4. As an immediate mitigation, the status cache can be **flushed entirely** for the affected region — the cost is a brief spike in on-read status computations (cheap, since the override store is small, §4.5), trading a small latency blip for immediate correctness.

---

## 9. Common Pitfalls & War Stories

### War Story 1: A Geohash Cell Boundary Hides a Business From Its Own Neighbors — Broken, Then Fixed

**Broken**: An early version of the Phase 1 geo-index (§4.1) queried only the **single geohash cell** containing the search point — "find businesses near `(lat, lng)`" was implemented as "find businesses whose geohash starts with the same N-character prefix as `(lat, lng)`'s geohash," with no neighbor expansion.

**Impact**: Many cities are laid out on a grid, and geohash cell boundaries — being themselves axis-aligned lat/lng bisections — frequently fall **along streets**, not through the middle of blocks. A user standing on one side of a street (and therefore in one geohash cell) searching "coffee shops near me" would get **zero or near-zero results** for a coffee shop literally across the street, if that shop's coordinates happened to fall in the adjacent cell. This produced a steady stream of business-owner support tickets along the lines of "my shop doesn't show up in 'near me' searches even though my competitors two blocks away do" — and the pattern, once investigated, correlated with whether the business's coordinates happened to sit near a cell edge, which (because geohash cells are a fixed global grid) is essentially **random with respect to street layout**, making roughly 1 in 4-5 businesses near *some* boundary at any given precision level. [Design Google Maps](./design_google_maps.md) §9 War Story 1 documents the identical underlying failure mode for POI search there — the geohash boundary-discontinuity problem is not specific to any one system, it's a structural property of fixed-grid spatial indexes.

**Fixed**: The `GeoRangeQuery.findNearby()` implementation in §4.1 always queries the **center cell plus its 8 neighbors** (a 3x3 block), merges results, and applies the exact haversine post-filter to discard any candidate outside the true requested radius. This guarantees that any business within `radiusMeters` of the search point is found, regardless of which cell — center or neighbor — it happens to fall in, at the cost of a constant 9x more candidate-set lookups against an index that's cheap enough (§2: ~1.2GB, in-memory) for that multiplier to be negligible. Business-owner tickets about "not appearing in nearby search" dropped to near-zero after this fix shipped.

### War Story 2: A One-Hour "Open Now" Cache TTL Sends Customers to a Locked Door on a Holiday — Broken, Then Fixed

**Broken**: The initial caching design (§4.5) used a **single cache with a 1-hour TTL** for the entire business document — name, address, category, hours, *and* the `open_now` computed flag — on the reasoning that "most of this data barely changes, so a long TTL maximizes cache hit rate and minimizes load on the search index."

**Impact**: On a public holiday, a popular restaurant chain's locations were closed for the day — each location's owner (or the chain's central system) flipped a "temporarily closed today" flag well before opening hours. However, many of those locations had been searched (and therefore cached) **the previous evening**, with `open_now = true` baked into the cached document from that prior evaluation. With a 1-hour TTL computed from the *previous day's* last cache-write, and search traffic for "restaurants open now" being relatively low overnight (so the cache entries weren't refreshed), a meaningful fraction of locations continued showing `open_now = true` for **up to an hour into the holiday morning** — well past when the closure flag had been set. Customers who searched "open now" near them, saw a result claiming to be open, and traveled there arrived to a locked door. The chain's social-media accounts and review pages saw a spike of 1-star reviews that morning specifically citing "drove here, it was closed despite the app saying open" — a direct, measurable reputational cost from a caching decision that had nothing to do with the restaurants' actual operations.

**Fixed**: Split the single cache into the two-tier structure described in §4.5: an **Attributes Cache** (name, address, category, regular weekly hours — TTL hours, since these genuinely don't change overnight) and a separate **Status Cache** (the computed `open_now` flag and any temporary-closure override — TTL 30-60 seconds). Critically, the fix also added **active invalidation**: when a business owner (or the chain's central system) toggles a "temporarily closed" flag, that write triggers an immediate pub/sub invalidation of the Status Cache entry for that business (the `onStatusToggle` method in §4.5's `OpenNowStatusResolver`), rather than waiting for any TTL to expire. The combination — a short TTL as a safety net, plus active invalidation for the common "owner just changed something" case — means a closure announced at any time is reflected in search results within seconds, not up to an hour.

### War Story 3: A Hash-Sharded Index Turns Every Search Into a Cluster-Wide Fan-Out — Broken, Then Fixed

**Broken**: When the search index (§4.2) was first scaled past a single Elasticsearch node, the team sharded it the way they'd shard any other dataset — by `hash(business_id) % num_shards` across 20 shards, the same default partitioning scheme used for the unrelated user-accounts service this team had built previously. It was a reasonable-looking decision: hash-based sharding distributes data and write load evenly, which is exactly what you want for most datasets.

**Impact**: Every nearby-search query's geo-radius candidates (§4.1) are, by definition, geographically clustered — a 2km-radius search returns businesses whose IDs are essentially **random** with respect to `hash(business_id)`, meaning those candidates were now spread roughly evenly across **all 20 shards**. Every single search request — regardless of how small its candidate set was — had to fan out to all 20 shards, wait for all 20 to respond, and merge the results. At 500K QPS peak (§2), this meant **10M shard-queries/sec** cluster-wide (20x the per-query work) instead of the ~1-1.5M shard-queries/sec a geography-aware partitioning would require (§10) — a 6-10x amplification purely from the choice of shard key. p99 latency blew past the 100ms target (§1) specifically during peak hours, because the slowest of 20 shards (tail latency amplification — the classic "one slow shard out of N ruins the whole request" problem) determined every request's latency, and at 20-way fan-out, *some* shard was always having a bad millisecond.

**Fixed**: Re-sharded the search index by **geohash prefix** (§4.7) — each shard owns a contiguous range of geohash prefixes, corresponding to a geographic region. A 2km-radius search's 9-cell candidate block (§4.1) now maps to **1-3 shards** in the overwhelming majority of cases (only queries near a shard's geographic boundary touch more than one), reducing cluster-wide shard-queries from 10M/sec to the ~1-1.5M/sec figure used in §10's capacity planning — roughly a 7x reduction. The re-sharding itself required a full reindex (Elasticsearch doesn't support in-place re-sharding by a different key), done via a parallel "build the new index, then atomically swap an alias" approach to avoid downtime — the same alias-swap technique mentioned in §11 for bulk imports. The broader lesson: **the default "shard by hash of primary key" pattern, correct for point-lookup workloads, is actively harmful for any workload whose queries are inherently range-based or spatially clustered** — geo-search is one such workload, but so is time-series data (shard by time range, not `hash(event_id)`) and the lesson generalizes.

---

## 10. Capacity Planning

### Geo-Index (Phase 1) Sizing

- 50M businesses, ~24 bytes/entry (geohash string + business ID + lat/lng, §2) -> **~1.2GB** total — fits in a single Redis instance's memory with significant headroom, or a small Redis Cluster for redundancy
- At precision 6 (~1.2km x 0.6km cells), the number of distinct non-empty cells globally is far smaller than the theoretical grid size (most of the planet is ocean/uninhabited) — practically, on the order of a few million populated cells, each holding anywhere from 0 to several thousand businesses depending on density
- `GEOSEARCH` latency at this scale: sub-millisecond per query — Phase 1 is never the bottleneck; Phase 2 (Elasticsearch) and network/serialization overhead dominate end-to-end latency

### Search Cluster (Phase 2) Sizing

- ~100GB total index size (§2) — comfortably shardable across, say, **20 shards of ~5GB each**, with each shard further geo-bucketed so that a given shard predominantly serves queries for its geographic region (reduces cross-shard fan-out for most queries)
- At 500K QPS peak (§2), and assuming each query touches 2-3 shards on average (the 9-cell candidate region may span shard boundaries near dense city centers): **~1-1.5M shard-queries/sec** cluster-wide
- A well-tuned Elasticsearch shard handles on the order of **1,000-2,000 simple filtered queries/sec**; at the higher end (2,000/shard) and 1.5M shard-queries/sec, that's **~750 active shard-replicas** — with a typical replication factor of 2-3 for availability, this implies **20 primary shards x 3 replicas = 60 shard-instances**, distributed across roughly **15-20 nodes** (assuming each node hosts 3-4 shard-instances comfortably within its CPU/heap budget)
- Regional sharding matters here: a global 500K QPS peak is not uniform — it follows the sun (lunch/dinner peaks roll across time zones), so the cluster is provisioned per-region with headroom for each region's local peak rather than as one undifferentiated global pool

### Candidate-Set Size vs. Precision Tuning

| Geohash Precision | Cell Size | Typical Candidates (Dense Urban) | Typical Candidates (Suburban) | Typical Candidates (Rural) |
|---|---|---|---|---|
| 5 (~4.9km x 4.9km) | Large | 10,000-50,000 | 500-2,000 | 0-20 |
| 6 (~1.2km x 0.6km) | Medium | 1,000-5,000 | 50-300 | 0-5 |
| 7 (~153m x 153m) | Small | 50-300 | 5-30 | 0-1 |

The adaptive selector (§4.4) targets the `MIN_CANDIDATES=20` to `MAX_CANDIDATES=2000` band — for a 2km search radius, precision 6 is the right starting point in most suburban and many dense-urban contexts, but dense-urban queries will frequently trigger the dense-throttle (narrowing toward precision 7), and rural queries will frequently trigger the sparse-expansion (widening toward precision 5 or coarser).

### Cache Sizing and Hit-Rate Targets

- **Attributes Cache**: 50M businesses x ~2KB (full document, §2) = ~100GB if fully cached — in practice, cache only the "hot" subset (businesses that appear in recent search results), targeting **>95% hit rate** for a working set of perhaps the top 5-10M most-frequently-surfaced businesses (~10-20GB), with long-tail businesses falling back to Elasticsearch on miss
- **Status Cache**: only the ~2.5M businesses that ever toggle status (§2) need entries, each a tiny boolean+timestamp (~50 bytes) -> ~125MB total — trivially small, target **>80% hit rate** is easily achievable even with the short 30-60s TTL, since most "open now" checks for the same business cluster within a short time window during peak search hours

### Write-Path Throughput

- ~6 listing-updates/sec average (§2), bursting to ~50/sec — three to four orders of magnitude below the 100K-500K QPS read path
- Status toggles: ~0.6/sec average (§2) — the CDC/Kafka pipeline (§4.6) and the status-cache invalidation pipeline (§4.5) both operate at volumes that are trivial relative to the read path; neither write path is a capacity concern, only a **correctness/freshness** concern (§4.5, §4.6, §9)

### Regional Capacity Tiering

Mirroring the "follows the sun" observation in §10's Search Cluster sizing, traffic and business density both concentrate heavily by region — a flat per-region allocation would either starve dense metro regions or massively over-provision sparse ones. A three-tier model (the same tiering shape [Design Google Maps](./design_google_maps.md) §10 uses for its routing fleet, applied here to search shards):

| Tier | Example Regions | Businesses (of 50M) | Share of Search QPS | Search Shards per Region | Cache Working-Set per Region |
|---|---|---|---|---|---|
| Tier 1 (dense metro) | NYC, London, Tokyo, São Paulo, Mumbai | ~30% (~15M) | ~55% (~275K QPS peak) | 4-6 primary shards x 3 replicas | ~3-5GB hot attributes cache |
| Tier 2 (mid-size metro) | regional capitals, secondary cities | ~45% (~22.5M) | ~35% (~175K QPS peak) | 1-2 primary shards x 3 replicas | ~1-2GB |
| Tier 3 (suburban / rural) | remaining coverage | ~25% (~12.5M) | ~10% (~50K QPS peak) | shared shards across multiple adjacent regions | <500MB, often cold |

Two consequences worth calling out for an interview:

- **Tier 1 regions disproportionately drive the dense-throttle path of §4.4** — a Tier 1 region's queries are far more likely to hit `MAX_CANDIDATES` and trigger precision-narrowing, which is part of *why* Tier 1 gets more shards (more shards means each shard's geographic slice is smaller, which directly reduces the candidate count per query before the adaptive selector even has to act).
- **Tier 3 regions disproportionately drive the sparse-expansion path of §4.4** — and because Tier 3 query volume is low, sharing shards across multiple adjacent Tier 3 regions (rather than dedicating a full 3-replica shard set to each) is both cost-effective and low-risk, since even a brief latency increase from cross-region shard sharing affects a small fraction of total traffic.

### Summary Table

| Component | Sizing Basis | Estimated Footprint |
|---|---|---|
| Phase 1 geo-index (Redis) | 50M businesses x ~24 bytes | ~1.2GB, single instance + replicas |
| Phase 2 search index (Elasticsearch) | 50M businesses x ~2KB, 20 shards x 3 replicas | ~100GB total, ~15-20 nodes |
| Attributes Cache | Hot working-set of ~5-10M businesses x ~2KB | ~10-20GB |
| Status Cache | ~2.5M businesses x ~50 bytes | ~125MB |
| Status override store | ~2.5M entries, small KV table | Low single-digit GB |
| Write-path (listing updates + status toggles) | ~6.6/sec combined average | Negligible — CDC/Kafka throughput, not a sizing driver |

---

## 11. Interview Discussion Points

**Q: Why is this a "search" problem and not a "matching" problem like Uber's — what actually changes architecturally?**
A: The read/write ratio and filter complexity are inverted between the two. Uber's driver-location index is write-heavy (every driver updates location every few seconds) with a simple query (nearest available driver within a radius) and a tight freshness SLA — so it uses a lightweight in-memory geo-index (Redis GEO/H3) as the *entire* solution. This design is read-heavy (100K-500K QPS, §2) with rich filters (category, price, rating, "open now," free text) against slow-changing data — so it needs a two-phase architecture (§4.1) where a lightweight geo-index does cheap candidate retrieval and a heavier search index (Elasticsearch, §4.2) does the filtering and ranking that a pure geo-index can't. You'd never put Elasticsearch in Uber's hot path (indexing lag would violate its freshness SLA), and you'd never rely on Redis GEO alone for this design's filter+rank requirements.

**Q: Why query 9 geohash cells instead of just the 1 cell containing the search point?**
A: Because geohash cell boundaries are arbitrary fixed-grid lines that have no relationship to where businesses are actually located — a business 10 meters away, across a cell boundary, lives in a *different* cell with a completely different geohash prefix (§4.1, War Story 1). Querying only the center cell means any business near a boundary is invisible to searches from the other side of that boundary, which in a grid-laid-out city affects roughly 1 in 4-5 businesses. Querying the center cell plus its 8 neighbors (a 3x3 block) guarantees coverage for any point within the search radius, at the cost of a constant 9x more (cheap, in-memory) lookups — followed by an exact haversine post-filter (§4.1) to discard the false positives the 3x3 block inevitably includes near its corners.

**Q: How do you balance distance vs. rating in the ranking formula — what breaks if you weight distance too heavily?**
A: The ranking score (§4.3) is a weighted sum of normalized distance, rating, relevance, and boost signals. If `w_distance` dominates (say, 0.8+), the ranking degenerates into "sort by distance" — a terrible 2-star restaurant 50 meters away would outrank an excellent 4.8-star restaurant 800 meters away, which feels broken for anything but the most trivial "nearest X" query. If `w_distance` is too low, top results can be technically "the best match" but a 30-40 minute walk away, which also feels broken for a "near me" query. Production systems tune these weights via A/B testing against actual user behavior (click-through, "visited" conversion) and typically add a **hard distance cutoff** (e.g., never surface results beyond 1.5x the requested radius) as a backstop independent of the weighted score.

**Q: Why might Elasticsearch be preferred over PostGIS for this specific problem?**
A: PostGIS gives you exact distances and the full power of SQL for filtering, with strong consistency — genuinely better than Elasticsearch on those two axes (§5). But the core requirement here is **combining** geo-filtering with category/price/rating/text filters *and* a custom relevance-ranking formula, all in one query, at 100K-500K QPS (§1, §2). Elasticsearch's `bool` query with `geo_distance` filters, term/range filters, full-text `match` clauses, and `function_score` custom scoring (§4.2) does all of this natively in one request; replicating that in PostGIS means either many separate queries plus application-side merging, or increasingly baroque SQL that doesn't scale well past tens of thousands of QPS per replica. The common production pattern is **both**: PostGIS as the system of record (§4.6), Elasticsearch as the read-optimized search layer, connected by CDC.

**Q: A business is missing from "nearby" results even though it's well within the radius — what are the first three things you'd check?**
A: First, the geohash-boundary issue (§4.1, War Story 1) — confirm the search is querying the 3x3 cell block, not just the center cell, and that the haversine post-filter isn't accidentally over-aggressive (e.g., using the wrong radius units). Second, Elasticsearch indexing lag (§4.6, §8) — if the business was recently created or had its coordinates updated, check whether the CDC pipeline has propagated that change yet (normally seconds-to-low-minutes, but a backlog could extend this). Third, check whether the business is being filtered out by an *unintended* attribute filter — e.g., a stale "open now" status (§4.5, War Story 2) excluding it from an "open now" search when it's actually open, or a category mismatch from a recent re-categorization that hasn't propagated.

**Q: How does the adaptive precision selector decide when to widen vs. narrow the search?**
A: It starts from the geohash precision implied by the user's requested radius (§4.1's `choosePrecision`), runs Phase 1 (§4.1), and checks the candidate count against two thresholds (§4.4): if fewer than `MIN_CANDIDATES` (e.g., 20) are found, it doubles the effective radius and re-queries — up to a capped number of expansions — to handle sparse rural areas. If more than `MAX_CANDIDATES` (e.g., 2,000) are found, it halves the effective radius to avoid overloading Phase 2's filter/rank step with an unnecessarily huge candidate set, common in dense urban cores. Both adjustments are cheap because Phase 1 (the in-memory geo-index, §4.1) can absorb several extra lookups for low single-digit milliseconds total — this iteration would be far too expensive to do directly against Elasticsearch.

**Q: How do you keep the "open now" filter accurate without re-computing it on every search request from scratch?**
A: Split the cache into two tiers by how fast the underlying data changes (§4.5): a long-TTL (hours) cache for slow-changing attributes (name, category, regular weekly hours), and a short-TTL (30-60 second) cache specifically for the computed `open_now` boolean and any temporary-closure overrides. The short TTL alone bounds staleness to under a minute even in the worst case, but the more important mechanism is **active invalidation** — when a business owner toggles "temporarily closed," that write immediately invalidates (via pub/sub) the status-cache entry for that business, so the change is reflected in the next search within milliseconds rather than waiting up to 60 seconds. War Story 2 (§9) is the cautionary tale of what happens with a single long-TTL cache covering both kinds of data.

**Q: What's the failure mode if the CDC pipeline (listing updates -> geo-index + search-index) falls behind?**
A: New or moved businesses won't appear (or will appear at stale coordinates) in search results until the pipeline catches up — bounded by the "minutes" listing-freshness NFR (§1) in normal operation, but a backlog could extend this. Critically, this does **not** affect the "open now" status path (§4.5), which is a separate, fast, directly-invalidated cache — so a CDC backlog degrades "did this new restaurant show up yet" but not "is this restaurant marked as open." Operationally (§8), CDC/indexing lag is tracked as a first-class metric with its own alert threshold, and the geo-index/search-index reconciliation job catches drift that the pipeline might silently introduce (e.g., from a transient consumer failure that skipped an event).

**Q: How would you support a search radius that spans multiple geohash precision boundaries — e.g., a 50km radius search?**
A: A 50km radius is far larger than even a precision-5 cell (~4.9km x 4.9km, §10), so a naive 3x3-cell-at-precision-5 query (covering roughly 15km x 15km) wouldn't cover the full 50km radius. The adaptive selector (§4.4) handles this by choosing a coarser starting precision — `choosePrecision` picks the precision whose cell size is comparable to (roughly half) the requested radius, so a 50km radius would start at precision 3-4 (cell sizes in the tens-to-hundreds of km range, per [Design Google Maps](./design_google_maps.md) §4.1's table) — still using the same 3x3-neighbor-expansion-plus-haversine-filter approach, just at a coarser grid level. The haversine post-filter is what makes this correct regardless of how coarse the starting precision is: it's the final arbiter of "is this candidate actually within `radiusMeters`," independent of cell geometry.

**Q: How do you handle a business with no fixed physical location — e.g., a delivery-only kitchen or a mobile food truck?**
A: For a delivery-only business, the "location" used for proximity search is typically the kitchen/commissary's coordinates, but the *relevant* filter for the user isn't "is this within X km" — it's "does this business deliver to my location," which is a service-area polygon or radius check, not a symmetric nearby-search. This is modeled as an additional attribute filter in Phase 2 (§4.2) — e.g., `ST_Contains(delivery_area_polygon, user_location)` in a PostGIS-backed check, or a precomputed `delivery_radius_km` field compared against the user's distance from the commissary in Elasticsearch. A food truck with a genuinely time-varying location is closer to Uber's problem than this one — it would need the high-frequency location-update pipeline from [Design Uber](./design_uber.md) §4 layered on top of this design's attribute/category filtering, illustrating that real systems sometimes need *both* architectures for different listing types within the same product.

**Q: How does this design's write path differ from a system where listings are bulk-imported (e.g., a chain onboarding 5,000 new locations at once)?**
A: The per-listing CDC/outbox flow (§4.6) is designed for low-rate, individual updates (~6/sec average, §2) — a 5,000-location bulk import would be 5,000 individual outbox events, which the pipeline can handle but which would all land in the Elasticsearch indexing queue at once, causing a temporary lag spike (§8) for unrelated listing updates queued behind them. Production systems typically route bulk imports through a **separate batch-indexing path** — writing directly to a new Elasticsearch index version and then atomically swapping an alias, or using Elasticsearch's bulk API with a higher concurrency budget — so that a large onboarding event doesn't degrade the indexing-lag SLA for the steady trickle of individual listing edits happening concurrently.

**Q: Why does the Attributes Cache use a long TTL when the underlying Elasticsearch index is itself only eventually consistent?**
A: These are two different staleness budgets stacked on top of each other, and that's fine because both are well within the "minutes" listing-freshness NFR (§1). Elasticsearch's indexing lag (seconds, §4.6) determines how quickly a *write* becomes visible in the search index; the Attributes Cache's TTL (hours) determines how quickly a *read* re-fetches from that index after a previous read cached it. A business that changes its category will be searchable-by-new-category within seconds (Elasticsearch lag) — but a user who searched and cached that business's old category won't see the update until their cache entry expires (up to hours). This is acceptable specifically *because* category/price/hours changes are rare (§4.5) — the rare case of "user sees slightly stale category for a business that just changed it" is a far smaller cost than re-fetching from Elasticsearch on every cache read for the 99.99% of businesses whose attributes haven't changed in months.

**Q: What happens to search results during a regional Elasticsearch cluster outage?**
A: The Attributes Cache (§4.5, long TTL) continues serving cached business documents for recently-searched businesses, so users searching for popular/recently-active areas see largely-correct (if slightly stale) results. For cache misses — new searches in areas with no warm cache entries — the system has two options depending on how the outage is detected: degrade to **Phase-1-only results** (§4.1's Redis GEO candidates, sorted by distance with no attribute filtering or relevance ranking — strictly worse, but functional), or fail the request with a clear "search temporarily degraded" signal and let the client retry. The former (cross-ref [`../resilience_patterns/README.md`](../resilience_patterns/README.md) for graceful-degradation patterns) is generally preferred for a consumer-facing search product — "some nearby results, unranked" beats "no results" for user-perceived availability, even though it's a visibly worse experience than normal.

**Q: How would you A/B test a change to the ranking formula's weights (§4.3) without risking a regression in production?**
A: Run both the old and new weight configurations behind a feature flag, bucketed by user ID (consistent hashing, cross-ref [`../consistent_hashing/README.md`](../consistent_hashing/README.md), ensures a user sees a stable experience across sessions during the test). Both Phase 1 candidate retrieval and Phase 2 filtering are identical between arms — only the final scoring weights (`w_distance`, `w_rating`, `w_relevance`, `w_boost`) differ, applied as a post-processing step on the same candidate set, which keeps the infrastructure cost of the experiment low (no duplicate Elasticsearch clusters needed). Success metrics typically include click-through rate on results, "visited" conversion (if trackable), and — importantly — a guardrail metric on **average result distance**, since an aggressive relevance/rating weighting that pushes average result distance up significantly is a leading indicator of the "technically relevant but too far away" failure mode discussed in the distance-vs-rating tradeoff question above.

**Q: Two businesses have identical scores under the ranking formula (§4.3) — how do you break the tie deterministically?**
A: Without a deterministic tiebreaker, two equal-scoring businesses can swap positions between identical requests (e.g., due to floating-point ordering nondeterminism across Elasticsearch shards, or pagination returning the same item twice across pages), which is a subtle but noticeable bug — users scrolling past page boundaries see duplicates or skips. The standard fix is a final tiebreaker on a stable, unique field — typically `business_id` (lexicographic) — appended as the last sort key after the computed score, guaranteeing a total order. This is a small detail, but it's exactly the kind of correctness edge case ("why do I sometimes see the same restaurant twice when I scroll") that's easy to miss in a design discussion but causes real, hard-to-reproduce bug reports in production.

---

## Cross-References

- **Geo-index theory — geohash precision tables, S2/H3 tradeoffs, neighbor-expansion technique (§4.1, §9 War Story 1)** -> [`./design_google_maps.md`](./design_google_maps.md) §4.1, §4.4, §9 War Story 1
- **Contrast: proximity matching (write-heavy, simple radius, tight freshness) vs. proximity search (read-heavy, rich filters, looser freshness) (Intuition, §4.1, §11)** -> [`./design_uber.md`](./design_uber.md) §4
- **Elasticsearch `geo_point` and inverted-index internals for Phase 2 (§4.2, §5, §6)** -> [`../../database/search_engines/README.md`](../../database/search_engines/README.md)
- **Split-TTL caching strategy for the Attributes vs. Status caches (§4.5, §9 War Story 2)** -> [`../caching/README.md`](../caching/README.md)
- **Cache-aside and read-through patterns underlying the two-tier cache (§4.5, §8)** -> [`../../database/database_caching_patterns/README.md`](../../database/database_caching_patterns/README.md)
- **Outbox pattern and CDC propagation for the write path (§4.6, §5)** -> [`../distributed_transactions/README.md`](../distributed_transactions/README.md)
- **A/B test bucketing and consistent-hashing-based experiment assignment (§11)** -> [`../consistent_hashing/README.md`](../consistent_hashing/README.md)
- **Graceful degradation during a search-cluster outage (§11)** -> [`../resilience_patterns/README.md`](../resilience_patterns/README.md)
