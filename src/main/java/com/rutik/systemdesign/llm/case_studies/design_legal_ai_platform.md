# Case Study: Design a Legal AI Platform
## Intuition

> **Design intuition**: A legal AI platform is like a brilliant paralegal who has read every case in your jurisdiction, every contract your firm has ever drafted, and every regulatory update — and can be asked anything at 2am before a deal closes. The engineering challenge is not LLM quality but trust and isolation: every citation must be traceable to a source, every document must stay within its matter boundary, and the platform must never hallucinate a statute or case that does not exist.

**Key insight for this design**: Legal work is citation-grade, not chat-grade. A customer support chatbot can be slightly wrong and the user corrects it. A legal AI that cites a non-existent case or misquotes a statute creates malpractice liability. The entire platform architecture is shaped by this single constraint: every claim must be grounded in a retrievable source, and that source must be verifiable by a human lawyer in under 30 seconds. Every component — the per-matter vector store, the citation verifier, the privilege classifier, the conflict checker — exists to enforce this constraint at a different layer of the stack. Speed is secondary. Accuracy is primary. Auditability is non-negotiable.

---

## 1. Requirements Clarification

### Functional Requirements

- **Document review**: flag issues in contracts, highlight risky clauses (unlimited liability, unilateral termination, non-standard IP assignment), score risk level per clause
- **Legal research**: find relevant cases and statutes for a given legal question, with citations formatted in Bluebook or OSCOLA
- **Contract drafting and redlining**: generate first draft from a template and matter context; suggest edits with rationale referencing precedent or firm playbook positions
- **Matter-scoped Q&A**: ask questions about documents in a specific matter; answer must be grounded only in matter documents — no cross-matter contamination
- **Conflict-of-interest check**: before onboarding a new client, determine whether the firm currently represents any party adverse to the new client across all active matters
- **Citation formatting**: Bluebook (US), OSCOLA (UK), EU citation style; auto-format references in generated text
- **Multi-jurisdiction support**: US federal, US state-by-state, UK, EU; query routing to jurisdiction-specific document corpora

### Non-Functional Requirements

- **Citation accuracy**: 99.5%+ verified citations (hallucinated citations = malpractice risk)
- **Matter isolation**: no document from Matter A must appear in Matter B's context under any failure mode
- **Response latency**: Q&A < 10 s; document review < 60 s per contract; conflict check < 5 s
- **Compliance**: SOC 2 Type II; attorney-client privilege protection; GDPR (EU data residency); ABA Model Rules alignment
- **Data residency**: US tenants default to us-east-1; EU tenants default to eu-west-1; on-premise deployment option for firms with strict privilege policies
- **Audit trail**: immutable 7-year retention for all queries, retrieved documents, and generated responses (regulatory and malpractice defense requirement)
- **Availability**: 99.9% monthly uptime for Q&A and research; document review can tolerate 99.5% (offline-tolerant workflow)

### Out of Scope

- Court filing automation (legal e-filing systems like Tyler Technologies are a separate integration)
- Litigation strategy generation (crosses into unauthorized practice of law territory without licensed attorney oversight)
- Non-legal document types (HR documents, financial statements, medical records)
- Real-time court docket monitoring and alerts

---

## 2. Scale Estimation

### Traffic Estimates

```
Tenants (law firms):         500
Active lawyers:              10,000
Document reviews/day:        50,000 (50 per firm avg)
Q&A queries/day:             200,000 (20 per lawyer avg)
Conflict checks/day:         2,000 (4 per firm avg — new client onboarding)
Redlining requests/day:      5,000

Average contract size:       30 pages = 45,000 tokens
Per-document-review cost:    45K input (contract) + 5K input (instructions) + 5K output = 55K tokens
Per-Q&A cost:                5K input (query + retrieved context) + 500 output = 5.5K tokens

Daily token consumption:
  Document review: 50,000 requests × 55K tokens   = 2.75B tokens
  Q&A:            200,000 requests × 5.5K tokens   = 1.10B tokens
  Redlining:        5,000 requests × 30K tokens    = 0.15B tokens
  Total:                                             4.00B tokens/day
```

### LLM Cost Estimation

```
Input and output must be priced separately — input dominates this workload 10:1,
and blending them at the output rate overstates cost by ~4x.
  Input tokens/day:  2.50B (review) + 1.00B (Q&A) + 0.125B (redline) = 3.625B
  Output tokens/day: 0.25B (review) + 0.10B (Q&A) + 0.025B (redline) = 0.375B

Token cost (GPT-5.4, July 2026: $2.50/1M input, $15.00/1M output):
  Input:  3.625B × $0.0000025 = $9,063/day
  Output: 0.375B × $0.0000150 = $5,625/day
  Total:                        $14,688/day = $441,000/month LLM cost

Revenue model: $500/seat/month × 10,000 seats = $5M/month
Gross margin after LLM cost: ($5M - $441K) / $5M = 91%
(After infrastructure, external legal DB APIs at $360K/month, and eng: ~60% gross margin)

External legal DB API cost:
  Citation verification: 200K Q&A/day × 3 citations avg × $0.02/lookup = $12,000/day
  = $360,000/month for LexisNexis/Westlaw API calls (dominant variable cost after LLM)
```

### Storage Estimates

```
Document storage:
  500 firms × 10TB avg firm document corpus = 5PB total (S3 with per-firm encryption keys)

Embedding storage (derive from matters, not from raw bytes — the 5PB figure is
mostly scanned PDFs and images, whose extracted text is a small fraction of it):
  50,000 matters × 10,000 chunks/matter = 500M chunks
  Each embedding: 1536 dimensions × 4 bytes = 6KB
  Total: 500M × 6KB = 3TB of vectors (5.5TB with chunk text; see Section 10)

Audit log storage:
  4B tokens/day × 0.1KB/token (query + metadata log) = 400GB/day
  7-year retention: 400GB × 365 × 7 = 1PB audit log (S3 Glacier Deep Archive)

Vector collections (per-matter isolation):
  500 firms × 100 active matters avg = 50,000 Qdrant collections
  Avg collection: 10,000 documents × 5KB text + 6KB embedding = 110MB
  Total Qdrant storage: 50,000 × 110MB = 5.5TB on SSD
```

### Vector Search Scale

```
Q&A queries: 200,000/day / 86,400s = 2.3 QPS average; 23 QPS peak (10x spike)
Per query: 10 vector lookups (top-10 chunks retrieved)
Vector search QPS: 23 QPS × 10 lookups = 230 lookups/sec peak

Qdrant node capacity: low thousands of QPS for HNSW search on 1536-dim vectors
  (benchmark on your own data — throughput is dominated by ef_search and payload size)
→ 1 Qdrant node sufficient for search throughput at 230 lookups/sec
→ 16 Qdrant nodes needed for HNSW index memory:
    50,000 collections × 5MB RAM avg for HNSW graph = 250GB RAM
    16 nodes × 32GB each = 512GB available (2x headroom)
```

---

## 3. High-Level Architecture

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    CLIENT([Lawyer Browser / Word Add-in / REST API Client]) --> GW["API Gateway (mTLS)\nFirm API key auth\nJWT validation\nPer-firm rate limits\nTLS 1.3 termination"]
    GW --> MCE["Matter Context Enforcer\ninjects firm_id + matter_id\nvalidates JWT claims\nlogs every request to audit trail (Kafka)"]
    MCE --> DR["Document Review\nEngine"]
    MCE --> LR["Legal Research\nEngine"]
    MCE --> CR["Contract Redlining\nEngine"]
    MCE --> CC["Conflict Checker"]
    DR --> VS["Per-Matter Vector Store\n(Qdrant, collection per matter — no shared DB)"]
    LR --> VS
    CR --> VS
    VS --> CV["Citation Verifier\nNLI entailment check\nLexisNexis/Westlaw API\nStatute existence check"]
    CV --> RA["Response Assembler\nflags low-confidence\nformats citations\nattaches source links"]
    RA --> AL["Audit Logger\n(immutable Kafka → S3)\n7-year retention"]
    AL --> RESP([Lawyer Response])

    subgraph async["Supporting systems (async, off critical path)"]
        ING["Document Ingestion\nPDF/Word → OCR → chunk → embed → Qdrant"]
        PC["Privilege Classifier\ndual-model, human review for changes"]
        LCS["Legal Corpus Sync\nLexisNexis / CourtListener updates\nnightly sync to internal corpus"]
        EP["Eval Pipeline\nweekly citation accuracy check\nhallucination rate monitoring"]
    end

    class CLIENT,RESP io
    class GW,MCE req
    class DR,LR,CR,CC,RA train
    class VS base
    class CV mathOp
    class AL,ING,PC,LCS,EP frozen
```

Every request passes the Matter Context Enforcer before fanning out to the four engines; the three generation paths re-converge on the per-matter vector store and must clear the synchronous Citation Verifier (~280 ms overhead) before the Audit Logger emits the immutable 7-year record.

### Multi-Region Topology

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    r53@{ icon: "logos:aws-route53", form: "square", label: "Route 53<br/>Latency-Based Routing", pos: "b", h: 44 }

    subgraph US["us-east-1 (US firms, default)"]
        USGW["API Gateway"]
        USMCE["Matter Context Enforcer"]
        USLLM["LLM Router<br/>(GPT-5.4)"]
        USQ["Qdrant cluster<br/>(US data)"]
        uspg@{ icon: "logos:postgresql", form: "square", label: "PostgreSQL<br/>primary", pos: "b", h: 44 }
        USLN["LexisNexis<br/>API endpoint"]
        uss3@{ icon: "logos:aws-s3", form: "square", label: "S3<br/>firm docs, encrypted", pos: "b", h: 44 }
        uskafka@{ icon: "logos:kafka", form: "square", label: "Kafka<br/>audit logs", pos: "b", h: 44 }
    end

    subgraph EU["eu-west-1 (EU firms, GDPR)"]
        EUGW["API Gateway"]
        EUMCE["Matter Context Enforcer"]
        EULLM["LLM Router<br/>(GPT-5.4 EU)"]
        EUQ["Qdrant cluster<br/>(EU data)"]
        eupg@{ icon: "logos:postgresql", form: "square", label: "PostgreSQL<br/>replica", pos: "b", h: 44 }
        EULN["LexisNexis<br/>EU endpoint"]
        eus3@{ icon: "logos:aws-s3", form: "square", label: "S3<br/>eu-west-1, EU only", pos: "b", h: 44 }
        eukafka@{ icon: "logos:kafka", form: "square", label: "Kafka<br/>audit logs EU", pos: "b", h: 44 }
    end

    r53 --> US
    r53 --> EU

    class USGW,USMCE,USLLM,EUGW,EUMCE,EULLM req
    class USQ,EUQ base
    class USLN,EULN frozen
```

Data residency guarantee: EU firm documents NEVER leave eu-west-1, EU matter Qdrant collections are ONLY provisioned in eu-west-1, and audit logs replicate to S3 within the same region only.

See also: [Tenant Isolation Patterns](./cross_cutting/tenant_isolation_patterns.md) for matter-level isolation at the vector DB layer.

---

## 4. Component Deep Dives

### 4.1 Matter-Scoped RAG with Architectural Isolation

The retrieval layer is the highest-risk component for matter isolation. The tempting implementation uses a single shared Qdrant collection with a `matter_id` metadata filter — this is **broken** and creates a catastrophic failure mode.

```python
# BROKEN: shared collection with metadata filter
# A bug in the filter logic — or a Qdrant version with a filter regression —
# causes all matters to leak into each other's retrieval results.
# The application layer is the ONLY enforcement boundary; one bug = privilege breach.

class NaiveMatterRetriever:
    def __init__(self, client: QdrantClient, collection: str) -> None:
        self._client = client
        self._collection = collection  # one shared collection for all matters

    def retrieve(self, query_embedding: list[float], matter_id: str, top_k: int = 10):
        # CATASTROPHIC: if matter_id filter is absent or malformed, ALL matters leak
        return self._client.query_points(
            collection_name=self._collection,
            query=query_embedding,
            query_filter=Filter(
                must=[FieldCondition(key="matter_id", match=MatchValue(value=matter_id))]
            ),
            limit=top_k,
        ).points
        # One missing must=[...] and every firm's documents are accessible.
        # One Qdrant filter bug and matter isolation silently breaks.
```

```python
# FIX: collection-per-matter design — cross-matter retrieval is architecturally impossible
# There is no filter to forget. There is no flag to misset.
# Matter A's collection does not physically contain Matter B's documents.

from __future__ import annotations
from dataclasses import dataclass
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct


@dataclass
class LegalDocument:
    chunk_id: str
    matter_id: str
    firm_id: str
    text: str
    source_file: str
    page_number: int
    privilege_level: str   # "UNPRIVILEGED", "WORK_PRODUCT", "PRIVILEGED"
    section_type: str      # "RECITAL", "DEFINITION", "OBLIGATION", "WARRANTY", "INDEMNITY"
    embedding: list[float]


@dataclass
class RetrievalResult:
    document: LegalDocument
    similarity_score: float
    collection_name: str   # included in audit log for forensic tracing


class MatterVectorStore:
    """
    One Qdrant collection per matter. Collection name encodes firm_id and matter_id.
    Cross-matter retrieval is physically impossible — not policy-enforced.
    """

    VECTOR_DIM = 1536   # text-embedding-3-large truncated via dimensions=1536
                        # (its native output is 3072 — do not assume 1536 by default)

    def __init__(self, client: QdrantClient) -> None:
        self._client = client

    def _collection_name(self, firm_id: str, matter_id: str) -> str:
        # Deterministic, human-readable, includes firm boundary.
        # A lawyer from firm_A cannot guess firm_B's collection name (UUID suffix).
        return f"firm_{firm_id}_matter_{matter_id}"

    def create_matter_collection(self, firm_id: str, matter_id: str) -> None:
        """Called once when a new matter is opened in the platform."""
        name = self._collection_name(firm_id, matter_id)
        self._client.create_collection(
            collection_name=name,
            vectors_config=VectorParams(size=self.VECTOR_DIM, distance=Distance.COSINE),
        )

    def ingest(self, doc: LegalDocument) -> None:
        """Ingest a document chunk into its matter-specific collection."""
        name = self._collection_name(doc.firm_id, doc.matter_id)
        self._client.upsert(
            collection_name=name,
            points=[PointStruct(
                id=doc.chunk_id,
                vector=doc.embedding,
                payload={
                    "text": doc.text,
                    "source_file": doc.source_file,
                    "page_number": doc.page_number,
                    "privilege_level": doc.privilege_level,
                    "section_type": doc.section_type,
                },
            )],
        )

    def retrieve(
        self,
        query_embedding: list[float],
        firm_id: str,
        matter_id: str,
        top_k: int = 10,
        exclude_privilege_levels: list[str] | None = None,
    ) -> list[RetrievalResult]:
        """
        Retrieve top_k chunks from this matter's collection only.
        exclude_privilege_levels allows filtering out PRIVILEGED docs
        in contexts where they should not be surfaced (e.g., e-discovery production).
        """
        name = self._collection_name(firm_id, matter_id)
        # `query_points()` returns a response object whose `.points` holds the hits.
        results = self._client.query_points(
            collection_name=name,
            query=query_embedding,
            limit=top_k,
            with_payload=True,
        ).points
        output = []
        for r in results:
            if exclude_privilege_levels and r.payload.get("privilege_level") in exclude_privilege_levels:
                continue
            doc = LegalDocument(
                chunk_id=str(r.id),
                matter_id=matter_id,
                firm_id=firm_id,
                text=r.payload["text"],
                source_file=r.payload["source_file"],
                page_number=r.payload["page_number"],
                privilege_level=r.payload["privilege_level"],
                section_type=r.payload["section_type"],
                embedding=[],  # not returned to caller
            )
            output.append(RetrievalResult(doc, r.score, name))
        return output
```

The collection-per-matter design adds real operational cost, and it fights the vendor's own guidance: Qdrant explicitly recommends a single collection with payload-based partitioning for multitenancy, warns that "it is not recommended to create hundreds and thousands of collections per cluster", and Qdrant Cloud caps a cluster at 1,000 collections. 50,000 matters therefore cannot live in one cluster. Two supported ways to get physical isolation at this scale: (a) shard firms across ~50 clusters at 1,000 collections each — the route this design takes, with the sharding plan in Section 10; or (b) use Qdrant's tiered multitenancy (v1.16+), which gives large tenants dedicated shards inside one collection and pools the rest, with its own recommended ceiling of ~1,000 dedicated shards per cluster. The tradeoff is still worth making — a matter isolation breach is a law firm's existential event — but "just make 50,000 collections" is not a configuration Qdrant supports.

See also: [Tenant Isolation Patterns](./cross_cutting/tenant_isolation_patterns.md) for the isolation hierarchy (hardware → process → collection → row-level) and when each is required.

### 4.2 Citation-Grade Retrieval and Verification

Every factual claim in a legal AI response must be verifiable by a human lawyer in under 30 seconds. The citation verifier runs synchronously before returning any response — adding 280 ms per response is correct; presenting an unverified citation is not.

```python
from __future__ import annotations
from dataclasses import dataclass, field
from enum import Enum
import httpx


class CitationStatus(Enum):
    VERIFIED = "verified"         # NLI entailment >= 0.85 AND external API confirms source exists
    UNVERIFIED = "unverified"     # entailment < 0.85 OR external API returned 404
    FLAGGED_FOR_REVIEW = "flagged" # entailment 0.70-0.84: possible but uncertain
    HALLUCINATED = "hallucinated"  # statute/case reference does not exist in LexisNexis/Westlaw


@dataclass
class CitationResult:
    claim: str
    cited_source: str          # source_file + page or case citation
    entailment_score: float    # 0.0-1.0 from NLI model
    external_verified: bool    # did LexisNexis/Westlaw confirm the source exists?
    status: CitationStatus
    flagged_reason: str | None = None


class CitationVerifier:
    """
    Two-stage verification:
    Stage 1: NLI entailment — does the cited source text actually support this claim?
             Uses cross-encoder NLI model (e.g., cross-encoder/nli-deberta-v3-large).
             Latency: ~80ms per claim on a CPU inference pod.
    Stage 2: External source validation — does the cited case/statute actually exist?
             LexisNexis API for case citations; Westlaw API for statute citations.
             Latency: ~200ms per citation (network call).
    Total overhead: ~280ms per claim. Acceptable for legal research (lawyers expect minutes).
    """

    NLI_ENTAILMENT_THRESHOLD = 0.85
    NLI_REVIEW_THRESHOLD = 0.70

    def __init__(self, nli_endpoint: str, lexisnexis_api_key: str, westlaw_api_key: str) -> None:
        self._nli_endpoint = nli_endpoint
        self._lexisnexis_key = lexisnexis_api_key
        self._westlaw_key = westlaw_api_key

    def verify(self, claim: str, cited_source_text: str, citation_reference: str) -> CitationResult:
        """Run both stages in parallel; return CitationResult with status and flagged_reason."""
        # Stage 1: NLI entailment (80 ms) — does source text actually support the claim?
        score = self._nli_entailment(claim, cited_source_text)
        # Stage 2: External validation (200 ms) — does the citation exist at all?
        exists = self._validate_external_citation(citation_reference)

        if not exists:
            return CitationResult(claim, citation_reference, score, False,
                CitationStatus.HALLUCINATED, "Not found in LexisNexis/Westlaw")
        if score >= self.NLI_ENTAILMENT_THRESHOLD:
            return CitationResult(claim, citation_reference, score, True, CitationStatus.VERIFIED)
        if score >= self.NLI_REVIEW_THRESHOLD:
            return CitationResult(claim, citation_reference, score, True,
                CitationStatus.FLAGGED_FOR_REVIEW, f"Entailment {score:.2f} below 0.85")
        return CitationResult(claim, citation_reference, score, True,
            CitationStatus.UNVERIFIED, f"Entailment {score:.2f} too low")

    def _nli_entailment(self, hypothesis: str, premise: str) -> float:
        """cross-encoder/nli-deberta-v3-large; ~80 ms on CPU pod."""
        resp = httpx.post(self._nli_endpoint,
            json={"premise": premise, "hypothesis": hypothesis}, timeout=2.0)
        resp.raise_for_status()
        return resp.json()["entailment_score"]

    def _validate_external_citation(self, citation_reference: str) -> bool:
        """LexisNexis primary; Westlaw fallback on timeout/429. ~200 ms.
        Endpoint paths below are illustrative — both vendors gate their citation
        APIs behind contracted, per-customer endpoints, not a public URL."""
        try:
            r = httpx.get("https://api.lexisnexis.com/v1/validate",
                params={"citation": citation_reference},
                headers={"Authorization": f"Bearer {self._lexisnexis_key}"}, timeout=1.0)
            return r.status_code == 200 and r.json().get("valid", False)
        except (httpx.TimeoutException, httpx.HTTPStatusError):
            r = httpx.get("https://api.westlaw.com/v1/check",
                params={"cite": citation_reference},
                headers={"Authorization": f"Bearer {self._westlaw_key}"}, timeout=1.0)
            return r.status_code == 200
```

Concrete performance budget: an average legal research response contains 5 citations. NLI check: 5 × 80 ms = 400 ms (parallelizable to 80 ms with concurrent NLI calls). External validation: 5 × 200 ms = 1,000 ms (parallelizable to 200 ms). Total citation verification overhead: ~280 ms when both stages run in parallel across claims. The 10-second Q&A latency budget comfortably accommodates this.

### 4.3 Document Ingestion and Privilege Classification

Ingestion is a multi-stage pipeline that runs asynchronously after document upload. The privilege classifier runs first — mislabeling a privileged document as unprivileged has caused real multi-million-dollar malpractice claims (see Section 9).

```python
from __future__ import annotations
from dataclasses import dataclass
from enum import Enum
import re


class PrivilegeLevel(Enum):
    PRIVILEGED = "PRIVILEGED"      # attorney-client communication; never producible
    WORK_PRODUCT = "WORK_PRODUCT"  # attorney mental impressions; qualified protection
    UNPRIVILEGED = "UNPRIVILEGED"  # ordinary business documents; freely producible


@dataclass
class PrivilegeClassification:
    doc_id: str
    level: PrivilegeLevel
    confidence: float              # 0.0-1.0
    rationale: str
    requires_human_review: bool    # True if confidence < 0.90 or models disagree


class PrivilegeClassifier:
    """
    Dual-model classification for defense-in-depth:
    Model 1: rule-based signals (attorney email domains, 'privileged and confidential' header, draft markers)
    Model 2: LLM classifier (frontier model with privilege rubric)
    Agreement → confidence 0.95. Disagreement → more protective label, human review queue, confidence 0.60.
    CRITICAL: any status change (UNPRIVILEGED → PRIVILEGED or reverse) always routes to human review.
    """

    ATTORNEY_EMAIL_DOMAINS: frozenset[str] = frozenset()  # firm's attorney directory

    STRIP_SUFFIXES = re.compile(r"\b(privileged and confidential|attorney.client)\b", re.IGNORECASE)

    def classify(self, doc_id: str, raw_text: str, author: str | None, recipients: list[str]) -> PrivilegeClassification:
        rule_level = self._rule_signals(raw_text, author, recipients)
        llm_level = self._llm_classify(raw_text)    # frontier model with privilege rubric
        if rule_level == llm_level:
            return PrivilegeClassification(doc_id, rule_level, 0.95, "Both models agree", False)
        # Disagreement: use more protective label, flag for human review
        order = [PrivilegeLevel.UNPRIVILEGED, PrivilegeLevel.WORK_PRODUCT, PrivilegeLevel.PRIVILEGED]
        protective = max(rule_level, llm_level, key=lambda l: order.index(l))
        return PrivilegeClassification(
            doc_id, protective, 0.60,
            f"Model disagreement: rule={rule_level.value}, llm={llm_level.value}",
            requires_human_review=True,
        )

    def _rule_signals(self, text: str, author: str | None, recipients: list[str]) -> PrivilegeLevel:
        t = text.lower()
        has_header = "privileged and confidential" in t or "attorney-client" in t
        is_draft = bool(re.search(r"\bdraft\b", t))
        author_atty = author is not None and any(author.lower().endswith(d) for d in self.ATTORNEY_EMAIL_DOMAINS)
        recip_atty = any(r.lower().endswith(tuple(self.ATTORNEY_EMAIL_DOMAINS)) for r in recipients)
        if (has_header or (author_atty and recip_atty)):
            return PrivilegeLevel.PRIVILEGED
        if is_draft and author_atty:
            return PrivilegeLevel.WORK_PRODUCT
        return PrivilegeLevel.UNPRIVILEGED

    def _llm_classify(self, text: str) -> PrivilegeLevel:
        raise NotImplementedError  # frontier-model call; returns PrivilegeLevel enum value
```

Legal-section-aware chunking is critical. A standard fixed-token chunker splits a 200-word Indemnity clause across two chunks — the first chunk misses the liability cap sentence, the second misses the trigger conditions. Legal chunking uses section boundary detection:

```
Legal document chunking strategy:
  1. Detect section headers: "RECITALS", "DEFINITIONS", "OBLIGATIONS",
     "WARRANTIES", "INDEMNIFICATION", "LIMITATION OF LIABILITY", etc.
  2. Each section = one or more chunks; never split a clause across chunks
  3. Minimum chunk size: 100 tokens; maximum: 800 tokens
  4. If a section exceeds 800 tokens, split at paragraph boundaries (blank line)
  5. Overlap: 50 tokens from adjacent section for context continuity
  Result: avg 200 clauses per 30-page contract, each clause is a coherent retrieval unit
```

### 4.4 Conflict-of-Interest Checker

Conflict checking is synchronous and must complete in under 5 seconds — it runs during client intake, with a lawyer waiting. A missed conflict discovered 6 months later can require the firm to withdraw from both matters and face an ethics investigation.

```python
from __future__ import annotations
from dataclasses import dataclass
import re
from difflib import SequenceMatcher
import psycopg2


@dataclass
class Party:
    name: str
    aliases: list[str]    # subsidiaries, trade names, former names
    role: str             # "client", "adverse_party", "counterparty"


@dataclass
class Conflict:
    existing_matter_id: str
    existing_matter_name: str
    conflicting_party_name: str
    new_engagement_party_name: str
    conflict_type: str     # "direct_adverse", "substantially_related", "positional"
    match_method: str      # "exact", "alias", "fuzzy_0.85"
    confidence: float


class ConflictChecker:
    """
    Three-tier entity matching to catch subsidiary aliasing failures (Section 9 war story):
    Tier 1: Exact string match (normalized: lowercase, strip Ltd/LLC/Inc)
    Tier 2: Alias expansion — check all known subsidiaries and trade names
    Tier 3: Fuzzy match with edit distance ≥ 0.85 (catches typos, punctuation variants)

    Data source: PostgreSQL matter_parties table (all firm matters + their parties)
    Runs in < 5s for 50,000 active matters via indexed query + application-side fuzzy match.
    """

    FUZZY_THRESHOLD = 0.85
    STRIP_SUFFIXES = re.compile(
        r"\b(ltd|llc|inc|corp|plc|gmbh|sa|nv|bv|ag|co|limited|incorporated|corporation)\b",
        re.IGNORECASE,
    )

    def __init__(self, db_conn: psycopg2.extensions.connection) -> None:
        self._db = db_conn

    def check_new_engagement(
        self, new_client: str, counterparties: list[str]
    ) -> list[Conflict]:
        """
        Check if the firm represents any party adverse to new_client or counterparties
        across all active matters.
        """
        new_parties = [new_client] + counterparties
        new_parties_normalized = [self._normalize(p) for p in new_parties]

        # Fetch all active matter parties (indexed on firm_id, status='active')
        existing_parties = self._fetch_active_parties()
        conflicts = []

        for new_party, new_norm in zip(new_parties, new_parties_normalized):
            for existing in existing_parties:
                conflict = self._match(new_party, new_norm, existing)
                if conflict:
                    conflicts.append(conflict)

        return conflicts

    def _normalize(self, name: str) -> str:
        name = name.lower().strip()
        name = self.STRIP_SUFFIXES.sub("", name)
        return re.sub(r"\s+", " ", name).strip()

    def _match(self, new_party: str, new_norm: str, existing: Party) -> Conflict | None:
        existing_norm = self._normalize(existing.name)

        # Tier 1: exact normalized match
        if new_norm == existing_norm:
            return self._make_conflict(new_party, existing, "exact", 1.0)

        # Tier 2: alias expansion — subsidiary / trade name match
        for alias in existing.aliases:
            alias_norm = self._normalize(alias)
            if new_norm == alias_norm:
                return self._make_conflict(new_party, existing, "alias", 0.99)

        # Tier 3: fuzzy match (Ratcliff/Obershelp similarity)
        ratio = SequenceMatcher(None, new_norm, existing_norm).ratio()
        if ratio >= self.FUZZY_THRESHOLD:
            return self._make_conflict(new_party, existing, f"fuzzy_{ratio:.2f}", ratio)

        return None

    def _make_conflict(
        self, new_party: str, existing: Party, method: str, confidence: float
    ) -> Conflict:
        return Conflict(
            existing_matter_id=existing.role,  # simplified; real impl joins matter table
            existing_matter_name="",
            conflicting_party_name=existing.name,
            new_engagement_party_name=new_party,
            conflict_type="direct_adverse",
            match_method=method,
            confidence=confidence,
        )

    def _fetch_active_parties(self) -> list[Party]:
        """
        SELECT name, aliases, role FROM matter_parties
        WHERE matter_status = 'active'
        -- indexed on matter_status; returns in < 2s for 50,000 matters × 10 parties avg
        """
        raise NotImplementedError
```

Concrete scale: 50,000 active matters × 10 parties avg = 500,000 party records. PostgreSQL `ILIKE` index on normalized name handles exact/alias lookup in < 500 ms. Fuzzy matching 500,000 records in Python takes ~3 s for 23 chars avg string — fits within the 5 s budget. For firms exceeding 200,000 active matters, move fuzzy match to pgvector with name embeddings.

### 4.5 Redlining Engine

The redlining engine generates a structured diff between the submitted contract and the firm's standard playbook positions. Output is not prose — it is a structured list of redlines, each with original text, proposed revision, rationale, and a precedent citation.

```python
from __future__ import annotations
from dataclasses import dataclass
import concurrent.futures


@dataclass
class PlaybookPosition:
    clause_type: str        # "INDEMNIFICATION", "LIMITATION_OF_LIABILITY", etc.
    firm_position: str      # "we never accept unlimited liability"
    fallback_position: str  # "if client insists, cap at 2x contract value"
    precedent_citation: str # "See Matter 2023-042 (negotiated cap 2x contract)"


@dataclass
class RedlineItem:
    clause_type: str
    original_text: str
    proposed_text: str      # LLM-generated revision
    rationale: str          # "Clause contradicts firm position: unlimited liability"
    precedent_source: str   # citation or matter reference
    risk_level: str         # "HIGH", "MEDIUM", "LOW"


class RedliningEngine:
    """
    Processes each contract clause against the firm playbook in parallel.
    10 concurrent LLM calls; 30-page contract (200 clauses, ~50 playbook matches) reviewed in 60 s.
    Output: structured RedlineItem list usable directly in Word Track Changes.
    """

    PARALLEL_BATCH = 10

    def redline(self, contract_text: str, positions: list[PlaybookPosition]) -> list[RedlineItem]:
        clauses = self._parse_clauses(contract_text)
        position_map = {p.clause_type: p for p in positions}
        results: list[RedlineItem] = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=self.PARALLEL_BATCH) as pool:
            futures = {
                pool.submit(self._redline_clause, c, position_map[c["type"]]): c
                for c in clauses if c["type"] in position_map
            }
            for f in concurrent.futures.as_completed(futures):
                item = f.result()
                if item:
                    results.append(item)
        return sorted(results, key=lambda r: r.original_text)  # caller re-sorts by clause order

    def _redline_clause(self, clause: dict, position: PlaybookPosition) -> RedlineItem | None:
        proposed, rationale = self._llm_redline(clause["text"], position)  # frontier-model call
        return RedlineItem(
            clause_type=clause["type"],
            original_text=clause["text"],
            proposed_text=proposed,
            rationale=rationale,
            precedent_source=position.precedent_citation,
            risk_level="HIGH" if "unlimited" in clause["text"].lower() else "MEDIUM",
        )

    def _parse_clauses(self, text: str) -> list[dict]: raise NotImplementedError
    def _llm_redline(self, text: str, pos: PlaybookPosition) -> tuple[str, str]: raise NotImplementedError
```

Concrete performance: average 30-page contract has 200 clauses; ~50 match playbook positions requiring redlines; 50 / 10 parallel = 5 LLM batch rounds × ~12 s each = 60 s total. Matches the 60 s NFR for document review.

---

## 5. Key Design Decisions

| Decision | Chosen Approach | Alternative Considered | Rationale |
|----------|----------------|------------------------|-----------|
| Matter isolation mechanism | Collection-per-matter in Qdrant, sharded across ~50 clusters (1,000 collections each) | Shared collection with matter_id metadata filter | Post-filter can fail silently; collection boundary is physically enforced by Qdrant. A matter isolation breach is an existential event. Cost: Qdrant caps a cluster at 1,000 collections and recommends payload partitioning instead, so this design pays for cluster sprawl to buy a physical boundary |
| Citation verification timing | Synchronous (blocks response) | Async (fire-and-forget, flag later) | An unverified citation returned to a lawyer will be used. By the time the async check flags it, it may be in a court filing. Synchronous is mandatory despite 280 ms overhead |
| LLM choice | Current frontier general model (GPT-5.4) with retrieval grounding | Legal-fine-tuned open-weight model | Fine-tuned models outperform on core US/UK law but underperform on rare jurisdictions, multilingual EU law, and emerging regulatory areas. Broader pretraining coverage wins when grounded with good retrieval |
| Privilege classification | Dual-model (rule + LLM) with human review on disagreement | Single LLM classifier | Single model mispredictions silently reclassify documents. Dual-model disagreement triggers human review — defense-in-depth for a consequence that can result in mistrial |
| Conflict check entity matching | Three-tier: exact → alias expansion → fuzzy 0.85 | Exact match only | "ACME Holdings Ltd" vs "ACME Holdings" is a real failure mode (Section 9). Exact-only matching is a well-known source of missed conflicts |
| External legal DB | LexisNexis primary, Westlaw fallback | Internal corpus only | Internal corpus covers uploaded documents only; citation verification requires authoritative external validation of case existence. Without it, the platform cannot detect hallucinated citations |
| Deployment topology | Regional per-firm with data residency enforcement | Single global cluster | GDPR requires EU data to stay in EU. US BigLaw requires data to stay in US. Single global cluster cannot satisfy both without complex routing that introduces failure modes |

---

## 6. Real-World Implementations

**Harvey AI** (founded 2022; $200M growth round at an $11B valuation co-led by GIC and Sequoia in March 2026, taking total capital raised to ~$1B on roughly $190M ARR):
Frontier models fine-tuned and orchestrated on legal data, focusing on M&A due diligence, contract review, and litigation research. Used by A&O Shearman, PwC, and other large firms. Per-seat pricing is not published — figures circulating in the press are estimates. Known for integration with deal management tools (Datasite, Intralinks). Harvey's competitive moat is training data: access to proprietary legal datasets from partner firms produces better legal reasoning than an ungrounded general model for core US/UK law. Weakness: limited jurisdiction coverage outside English-speaking common law systems.

**Hebbia** (Matrix product, $700M valuation 2024, $130M Series B):
Multi-document analysis engine for complex deal rooms. Loads 1,000+ documents simultaneously into a structured analysis grid; answers questions across the entire document set with precise source citations per cell. Strong in private equity due diligence (analyze 500 data room documents in parallel) and financial covenant analysis. Differentiator: structured output (table format with one answer per document per question) rather than conversational output — designed for diligence workflows, not chat. Architecture relies on long-context LLMs (128K+ context) to avoid retrieval quality issues on complex cross-document questions.

**Robin AI** (UK-focused, Series B 2024):
Contract review and negotiation engine fine-tuned on English law. Integrates redlining directly into Microsoft Word as a native add-in. Used by both law firms and in-house legal teams at FTSE 100 companies. Focus on speed: standard NDA reviewed in under 60 seconds. Revenue model: flat monthly subscription per team rather than per-seat — makes it accessible to in-house teams with variable usage. Jurisdiction limitation: optimized for English law; performance degrades on Scottish law and non-UK jurisdictions.

**Spellbook** (in-Word drafting; ARR not publicly disclosed):
Lives inside Microsoft Word as a sidebar. Context-aware drafting suggestions that understand the surrounding contract text. Uses OpenAI API (not fine-tuned). Fastest to integrate for existing workflows — no document upload, no matter management, just a Word add-in. Dominates the drafting workflow at small-to-mid law firms. Does not compete on research or review; wins purely on "the lawyer never leaves Word." Limitation: no matter isolation, no citation verification — suited for drafting assistance, not research grounding.

**Thomson Reuters CoCounsel** (2023, backed by the Westlaw content library that West Publishing has been building since 1872):
The most conservative product in the category. Every answer is grounded exclusively in Westlaw sources — 40,000+ databases including primary law, secondary sources, law reviews, and treatises. Westlaw citation validation is built-in: CoCounsel cannot cite a source that is not in Westlaw's corpus. Trusted by BigLaw for research because the liability is bounded: if Westlaw says the case exists, it exists. Weakness: Westlaw database coverage is US-heavy; international law research is limited. Pricing is bundled with a Westlaw subscription and negotiated per firm — Thomson Reuters publishes no rate card, and it sits at the top of the market. Thomson Reuters' competitive moat is content, not model quality.

**Leya** (Sweden, EU-focused):
Swedish and EU law specialist. Regulatory compliance focus (GDPR, AI Act, ESG reporting). Strong multilingual support (Swedish, German, French legal documents). Architecture: jurisdiction-specific fine-tuned models routing to jurisdiction-specific corpora. Illustrates the market fragmentation: no single legal AI dominates across all jurisdictions; regional specialists serve local markets better than global generalists.

---

## 7. Technologies and Tools

### Vector Database Comparison for Per-Matter Isolation

| Capability | Qdrant (collections) | Pinecone (namespaces) | Weaviate (multi-tenancy) | pgvector (RLS) |
|------------|---------------------|-----------------------|--------------------------|----------------|
| Isolation mechanism | Separate collection per matter | Namespace within index | Multi-tenancy per class | Row-level security per user |
| Max collections/namespaces | 1,000 per cluster (Qdrant Cloud); vendor advises against hundreds+ | 10,000 namespaces per serverless index (100 on Starter; million-scale on request) | Unlimited tenants | Unlimited (table rows) |
| Isolation strength | Physical (separate HNSW graph) | Logical (shared index, filtered search) | Physical (separate HNSW per tenant) | Logical (shared B-tree) |
| Query latency at 50K collections | 5-15 ms | 5-10 ms (but filter risk) | 5-15 ms | 20-50 ms (JOIN overhead) |
| Cost at 5.5TB storage | ~$4,000/month (managed) | ~$8,000/month | ~$5,500/month | ~$1,500/month (RDS) |
| Suitable for legal? | Yes — physical isolation | No — namespace is logical filter, same isolation bug as shared collection | Yes — strongest isolation | Only for small deployments |

**Winner for legal**: Qdrant (collection-per-matter, sharded across clusters) or Weaviate (tenant-per-matter, which scales to far more tenants per cluster). Pinecone namespaces are a logical partition inside one index, so they carry the same class of risk as the broken approach in Section 4.1 — namespace scale is not the constraint, isolation strength is.

### Legal Data Sources

| Source | Coverage | API Available | Cost/Query | Update Frequency |
|--------|----------|--------------|-----------|-----------------|
| Westlaw (Thomson Reuters) | US primary law, 40K+ databases, law reviews | Yes (CoCounsel API) | $0.05-$0.15 | Daily |
| LexisNexis | US + international, 2M+ sources | Yes (LexisNexis+ API) | $0.02-$0.08 | Daily |
| CourtListener (Free Law Project) | US federal + state courts (PACER mirror) | Yes (REST API, free) | $0.00 | Daily |
| Casetext (acquired by TR 2023) | US case law + statutes | Via CoCounsel only | Bundled | Daily |
| EUR-Lex | EU official law, regulations, directives | Yes (SPARQL/REST) | Free | Continuous |
| BAILII | UK/Ireland, Commonwealth | Limited | Free | Weekly |

**Recommendation**: LexisNexis primary (breadth + API) + CourtListener fallback (cost) + EUR-Lex for EU compliance matters. Westlaw for premium US research tiers.

### LLM Options

| Model | Citation Accuracy (internal benchmark) | Context Window | Cost (input/output per M tokens) | Jurisdiction Coverage |
|-------|---------------------------------------|---------------|-----------------------------------|----------------------|
Citation-accuracy figures are this platform's own internal benchmark on its golden set, not vendor or third-party published results; prices are July 2026 list, per 1M tokens.

| Model | Citation Accuracy (internal benchmark) | Context Window | Cost (input/output per M tokens) | Jurisdiction Coverage |
|-------|---------------------------------------|---------------|-----------------------------------|----------------------|
| GPT-5.4 | 94% with RAG grounding | 400K tokens | $2.50 / $15 | Global, strong |
| Claude Opus 4.8 | 93% with RAG grounding | 1M tokens | $5 / $25 | Global, strong |
| Harvey fine-tuned (not public) | 97% on US/UK law | Not published | Not available | US/UK strong; others weak |
| Llama legal fine-tune | 88% on US law | 128K tokens | $0.20 / $0.60 (self-hosted) | US only |
| Gemini 3.1 Pro | 92% with RAG grounding | 1M tokens | $2 / $12 | Global |

**Recommendation**: a current frontier model (GPT-5.4 here) as primary LLM with RAG grounding. Legal fine-tuned models outperform on core jurisdictions but degrade unpredictably outside training distribution; broader pretraining wins when compensated with strong retrieval. Use Claude Opus 4.8 for long-document analysis (1M context avoids chunking for full contracts).

---

## 8. Operational Playbook

### Eval Pipeline

Weekly citation accuracy check runs every Sunday at 02:00 UTC using 100 known legal Q&As with verified citations from LexisNexis. Any model configuration change or RAG pipeline update triggers an immediate out-of-band run.

```mermaid
pie title Weekly eval set composition (100 questions)
    "US federal case law" : 30
    "US statute interpretation" : 20
    "UK case law" : 20
    "Contract clause analysis" : 15
    "Multi-jurisdiction (cross-border M&A)" : 15
```

Each slice is verified against its authoritative source: LexisNexis for US case law, the USC for statute questions, BAILII for UK case law, and the firm playbook for contract clause questions.

```
Pass criteria:
  - Citation accuracy >= 99.5% (verified by CitationVerifier + human spot-check)
  - Hallucination rate < 0.5% (LLM cites non-existent source)
  - P95 response latency < 10s (Q&A), < 60s (document review)
  - Matter isolation: 0 cross-matter retrievals (tested by adversarial cross-matter queries)
```

Alert thresholds:
- Hallucination rate > 0.5%: PagerDuty P2 alert (legal risk)
- Citation accuracy < 99.0%: PagerDuty P1 alert (SLA breach risk)
- P95 Q&A latency > 15 s: Slack alert (performance degradation)

See also: [LLM Eval Harness in Production](./cross_cutting/llm_eval_harness_in_production.md) for the LLM-as-judge rubric for legal citation accuracy assessment.

### Observability

Every request produces an OpenTelemetry trace with legal-specific attributes:

```
Trace: legal_ai_request (trace_id: abc123)
  +-- Span: api_gateway.auth          (3 ms)   firm_id, lawyer_id_hash, matter_id_hash
  +-- Span: matter_context_enforcer   (1 ms)   isolation_check=pass, collection=firm_X_matter_Y
  +-- Span: retrieval.matter_scoped   (12 ms)  top_k=10, top_score=0.89, privileged_excluded=2
  +-- Span: llm.generate             (4,200 ms)
  |     gen_ai.provider.name=openai, gen_ai.request.model=gpt-5.4
  |     gen_ai.usage.input_tokens=4312, gen_ai.usage.output_tokens=487
  |     legal.jurisdiction=US_FEDERAL, legal.query_type=research
  +-- Span: citation_verifier         (280 ms)
  |     citation.claims_count=4, citation.verified=3, citation.flagged=1
  |     citation.hallucinated=0, citation.lexisnexis_latency_ms=210
  +-- Span: audit_logger.emit         (2 ms)   kafka_offset, audit_record_id
```

See also: [OpenTelemetry for LLM Apps](./cross_cutting/opentelemetry_for_llm_apps.md) for full `gen_ai.*` semantic convention mapping and legal-specific attribute extensions.

### Incident Runbooks

**Runbook 1 — Citation Hallucination Spike**

Symptoms: `citation.hallucinated_count` counter > 0 for > 2% of requests in a 15-minute window; PagerDuty P1 alert fires.

Diagnosis:
1. Check if the LLM model version changed in the last 24 hours (a provider can repoint an undated alias)
2. Check if the retrieval top_score distribution shifted downward (poor retrieval → LLM fills gaps with hallucinations)
3. Check if the spike is jurisdiction-specific (new regulatory area not covered by internal corpus)

Mitigation (immediate, < 10 minutes):
1. Enable strict citation threshold: change NLI_ENTAILMENT_THRESHOLD from 0.85 to 0.90 (reduces VERIFIED responses, routes more to human review — conservative but safe)
2. Add "I cannot verify this citation" fallback response for any claim with entailment score < 0.70
3. Route affected query types to human review queue with 30-minute SLA

Resolution (within 24 hours):
1. If the LLM model changed: pin the API call to a dated snapshot rather than an undated alias
2. If retrieval quality degraded: check Qdrant HNSW index health; rebuild if ef_construction drifted
3. If jurisdiction gap: ingest new regulatory corpus and re-index before lowering threshold

**Runbook 2 — Matter Isolation Breach Attempt**

Symptoms: audit log shows a retrieval query referencing `matter_id=X` but the lawyer's JWT contains only `matter_id=Y`; Matter Context Enforcer fired `isolation_violation` event.

Mitigation (immediate, < 5 minutes):
1. Terminate the lawyer's session immediately (invalidate JWT)
2. Send automated alert to firm's general counsel and platform security team
3. Freeze the firm's API access pending investigation
4. Confirm in Qdrant query logs whether any documents from Matter X were actually returned

Resolution (within 4 hours):
1. Forensic audit: review full OTel trace for the session; confirm whether actual data crossed matter boundary
2. If data crossed: regulatory notification may be required (GDPR Article 33 for EU firms; ABA Model Rule 1.6 notification for US)
3. If no data crossed (attempt blocked by enforcer): document incident, root cause why JWT contained wrong matter_id, patch auth flow

**Runbook 3 — Conflict Check False Negative**

Symptoms: post-engagement, opposing counsel discovers the firm represents an adverse party; ethics committee opens investigation.

Diagnosis: retrieve the original conflict check audit record. Identify failure mode: exact match miss, alias not in index, fuzzy threshold miss, or subsidiary not mapped.

Mitigation (within 2 hours): add missing entity alias to conflict index; re-run conflict checks for all engagements opened in the same 30-day window; notify affected firm.

Resolution: add entity resolution pipeline querying OpenCorporates API to populate subsidiary graph at intake time. If fuzzy threshold too high, run precision/recall analysis and consider lowering to 0.80 for shorter entity names. Integrate D&B corporate structure database for comprehensive subsidiary coverage.

**Runbook 4 — LexisNexis API Outage**

Symptoms: `citation.lexisnexis_latency_ms` > 2,000 ms or 503 errors for > 5 minutes.

Mitigation (immediate): auto-switch to Westlaw fallback (pre-configured in CitationVerifier). If both unavailable, mark citations as "CITATION_UNVERIFIED — external database temporarily unavailable" and display in-app banner advising manual verification before use.

Resolution: monitor LexisNexis status page; re-enable as primary when restored. If outage > 2 hours, activate internal corpus-only validation (lower accuracy; acceptable for non-statute research). Negotiate SLA for < 4-hour RTO with contractual remedies for extended outages.

---

## 9. Common Pitfalls and War Stories

**Air Canada chatbot liability parallel (February 2024)**: Air Canada's AI chatbot gave a customer incorrect bereavement fare policy information; the BC Civil Resolution Tribunal ruled Air Canada liable for the chatbot's statements (Moffatt v. Air Canada, 2024). The legal AI parallel is higher-stakes by an order of magnitude. A chatbot giving wrong travel policy costs hundreds of dollars. A legal AI citing a non-existent statute in a court brief costs the client's case and exposes the attorney to bar discipline. The architectural lesson: legal AI must present hallucinated or low-confidence responses to lawyer review — never to the client's brief — until verified. Every FLAGGED_FOR_REVIEW citation is a human review queue item, not a suppressed response.

**Citation misquoting — the "case exists but does not say that" failure**: Legal AI systems reliably produce citations to real cases whose holdings are paraphrased beyond what the opinion supports. This class of error is well documented in courts' own sanctions opinions against filings containing AI-assisted citations; the specific vendor demos sometimes cited for it are second-hand accounts, so treat it as a category, not an attributed event. Firm deployment protocols respond by requiring lawyer review of every cited source. The lesson: citation verification must check not just that the source exists (external API) but that the specific claim is supported by the source text (NLI entailment). A case can exist and still not support the claim made about it.

**Privilege log contamination (illustrative composite, not a reported public incident)**: A privilege classifier bug marked 12 documents tagged PRIVILEGED as UNPRIVILEGED due to a regex error in the rule-based tier. The LLM's dual-model verification tier was bypassed by a deployment that hot-patched the rule model without re-running classifier agreement checks. The 12 documents were included in an e-discovery production set. Opposing counsel received attorney-client communications. The firm faced a privilege clawback motion — the kind of dispute that routinely runs into seven figures of legal fees. The architectural fix: any document changing privilege status (PRIVILEGED → anything, WORK_PRODUCT → UNPRIVILEGED) is always routed to human review before the status change is persisted. No deployment can bypass this gate.

**Multi-matter context window injection (illustrative composite)**: A lawyer's browser session retained context from a previous query about a different client. The query "based on the previous analysis, what are the risks for our client?" included the previous client's matter context in the prompt. The LLM incorporated both contexts and generated an answer that referenced confidential information from Matter B in a response about Matter A. A professional conduct complaint was filed. The fix: the system prompt injects a strict matter boundary instruction at every LLM call: "You may only use information from the documents explicitly provided in this request context. You have no prior conversation context. Matter identifier: [matter_id]." The Matter Context Enforcer validates that no prior session state persists across matter boundaries at the application layer — not the browser layer.

**Conflict check entity aliasing failure (illustrative composite of a well-known failure mode)**: A firm's conflict system searched for "ACME Holdings Ltd" when the new engagement counterparty was listed as such. The firm already represented "ACME Holdings" (registered without "Ltd" suffix) in a directly adverse matter. The conflict check returned no results because exact-match-only search missed the suffix variation. Both matters proceeded simultaneously for 6 months until opposing counsel identified the conflict. The state bar opened an ethics investigation; the firm withdrew from both matters. The fix is the three-tier matching in Section 4.4. Suffix stripping alone reduces both names to "acme holdings", so tier 1 catches it outright; if the suffix list misses a variant, tier-3 fuzzy matching still clears the 0.85 threshold — `SequenceMatcher(None, "acme holdings ltd", "acme holdings").ratio()` is 2*13/30 = 0.867, not the 0.96 that a naive character-overlap intuition suggests. That margin is thin: pick the threshold against a real name corpus, not by intuition. The subsidiary alias expansion (tier 2) catches trade name variations. This scenario is a widely-discussed failure mode in conflicts practice, described here as an illustrative composite rather than a specific reported matter.

See also: [Red Team Eval Harness](./cross_cutting/red_team_eval_harness.md) for adversarial legal prompt testing including cross-matter injection attempts, citation hallucination probes, and privilege bypass attacks.

---

## 10. Capacity Planning

### Vector Store Sizing Formula

```
vector_collections = num_firms x avg_active_matters_per_firm
                   = 500 x 100 = 50,000 collections

collection_storage_per_matter =
  avg_documents_per_matter x (avg_text_bytes + embedding_bytes)
  = 10,000 docs x (5,000 bytes text + 6,144 bytes embedding)
  = 10,000 x 11,144 bytes = 111 MB per collection

Total Qdrant storage:
  50,000 collections x 111 MB = 5.55 TB (SSD-backed)

HNSW index RAM (m=16, ef_construction=200, approx 5MB RAM per 10K vectors):
  10,000 docs/collection x 50,000 collections = 500M vectors total
  500M vectors x 0.5 KB RAM/vector (HNSW graph) = 250 GB RAM needed
  → 16 Qdrant nodes x 32 GB each = 512 GB available (2x headroom)
  → Qdrant cluster: 16 nodes, 32-core CPU, 32 GB RAM, 1 TB NVMe each
```

### Query Throughput Sizing

```
Q&A peak QPS: 200,000 queries/day x 10x peak factor / 86,400s = 23 QPS average peak
Per query: 10 Qdrant lookups (top-10 retrieval)
Qdrant lookups/sec at peak: 23 x 10 = 230 lookups/sec

Single Qdrant node capacity: ~10,000 QPS HNSW search
→ 1 node sufficient for throughput; 16 nodes needed for RAM
→ Reads distributed across 16 nodes: 230 / 16 = 14.4 QPS per node (well within capacity)
```

### LLM Inference Sizing

```
Document review:
  50,000 reviews/day / 86,400 s = 0.58 reviews/sec average
  Each review: 60 s LLM wall time, 55K tokens
  Concurrent reviews: 0.58 x 60 = 35 concurrent reviews at peak
  Provider limits are expressed as requests/min and tokens/min, not a concurrency
  cap; 35 concurrent 55K-token reviews is ~1.9M input tokens/min, which needs an
  enterprise-tier TPM allocation but is not close to a hard ceiling

Q&A queries:
  200,000/day / 86,400 s = 2.3 queries/sec average, 23 QPS peak
  Each query: ~5K tokens, ~8 s LLM time
  Concurrent queries at peak: 23 x 8 = 184 concurrent queries
  → At peak this pressures the primary provider's TPM allocation; mitigate with:
    (a) Claude Opus 4.8 as overflow (1M context, separate provider rate-limit pool)
    (b) Queue depth monitoring with exponential backoff retry

Citation verification:
  200,000 Q&A/day x 4 claims avg = 800,000 NLI calls/day = 9.3 NLI calls/sec
  NLI pod (deberta-v3-large on 2xA10G): ~50 NLI calls/sec per pod
  → 1 NLI pod handles average load; 2 pods for peak headroom
```

### Growth Projection

```
Year 1: 500 firms, 50K collections, 5.5 TB Qdrant, $441K/month LLM cost
        Qdrant Cloud caps a cluster at 1,000 collections, so 50K collections is
        already ~50 clusters on day one — sharding is a launch requirement here,
        not a Year-2 milestone.
Year 2: 2,000 firms, 200K collections → ~200 clusters at the 1,000-collection cap
         Storage: 22 TB Qdrant; $1.76M/month LLM cost at 4x volume, same pricing
         Revenue: 40,000 seats x $500 = $20M/month; gross margin holds at ~91%
         before infra (LLM cost scales linearly with seats, so margin is flat,
         not improving — the leverage comes from infra and eng, not tokens)

Qdrant sharding (from launch, not Year 2):
  Hash firm_id to N clusters, each holding <= 1,000 collections
  Year 1: 50 clusters x 1,000 collections; Year 2: ~200 clusters
  Alternative: Qdrant tiered multitenancy (v1.16+) — dedicated shards for the
  largest firms plus a shared shard for the long tail, ~1,000 dedicated shards
  per cluster recommended, which cuts cluster count by an order of magnitude
  Cross-shard queries: conflict check only (reads multiple firms' matter indices)
  → Conflict checker queries all 4 shards in parallel; 4x latency increase mitigated by
     partitioned PostgreSQL party index (SQL query, not vector search, for conflict check)
```

---

## 11. Interview Discussion Points

**Q: Why use a collection-per-matter design instead of a shared collection with a matter_id metadata filter?**

The metadata filter approach has a single point of failure: if the filter is absent, malformed, or contains a bug, documents from every matter in the database are returned to every query. In a legal context this is a privilege breach — confidential communications from one client are exposed to another. The collection-per-matter design makes cross-matter retrieval physically impossible. A query on `firm_acme_matter_42` cannot return results from `firm_acme_matter_43` because those documents are not in that collection. The operational cost is real and larger than it first looks: Qdrant Cloud caps a cluster at 1,000 collections and its own multitenancy guidance recommends payload-based partitioning instead, so 50,000 matters means roughly 50 clusters (or Qdrant's tiered multitenancy, v1.16+). You are buying a physical boundary with cluster sprawl. A matter isolation breach is an existential event for a law firm and a platform, so the trade is still worth making — but answer it as a cost, not as a free win.

**Q: How does citation verification prevent the Air Canada chatbot pattern in a legal context?**

The system runs two checks before a citation reaches the lawyer. First, NLI entailment: a cross-encoder model scores whether the cited source text actually supports the specific claim made. A score below 0.85 routes the claim to FLAGGED_FOR_REVIEW rather than VERIFIED — the lawyer sees the flag, not a confident assertion. Second, external validation: LexisNexis or Westlaw confirms the cited case or statute reference actually exists. If the source does not exist, the status is HALLUCINATED and the response includes a visible warning. Together these catch two distinct failure modes: sources that are fabricated (external validation) and sources that exist but do not support the claim (NLI entailment). The 280 ms overhead is mandatory; presenting an unverified citation to a lawyer who will use it in a court filing without further review is an acceptable failure mode for a chatbot but not for legal AI.

**Q: How do you handle the privilege classification edge case where a document is both work-product and contains factual information needed for e-discovery production?**

This is the "dual-nature document" problem in privilege law. An attorney's memo that contains both the attorney's mental impressions (work-product) and non-privileged factual summaries is not fully protected. The platform classifies the document as WORK_PRODUCT and routes it to a human review queue. The reviewing attorney can redact the protected portions and produce the factual portions. The platform never auto-produces a WORK_PRODUCT document — that decision always requires attorney judgment. In the ingestion pipeline, dual-nature documents flagged by the privilege classifier's rationale field (which includes "contains both protected and factual content" in the LLM classification) get a PARTIAL_WORK_PRODUCT label that triggers mandatory human review before any production workflow proceeds.

**Q: How does the conflict checker scale when the firm has 50,000 active matters and 500,000 party records?**

The conflict checker uses a two-stage approach. Stage 1 is a PostgreSQL query on the `matter_parties` table with an index on `normalized_name` and `matter_status='active'` — this returns all active parties in under 500 ms. Stage 2 is Python-side fuzzy matching with SequenceMatcher across 500,000 records — at 23 characters average name length this completes in approximately 3 seconds, within the 5-second budget. For firms exceeding 200,000 active matters (where Stage 2 would exceed 5 seconds), the fuzzy match moves to pgvector: entity names are embedded with a lightweight model (all-MiniLM-L6-v2), stored in a vector column, and approximate nearest-neighbor search retrieves candidate matches in under 200 ms. Tier-1 exact and alias matching always runs in PostgreSQL regardless of scale.

**Q: Why is citation verification synchronous rather than async, given the 280 ms overhead?**

The async alternative — return the response and flag citations as unverified later — assumes the lawyer will wait for the verification result before using the citation. Lawyers under deal pressure do not wait. If the response arrives with a citation that looks confident, it goes into the brief. By the time the async verification flags it as hallucinated, the brief is filed. The only safe architecture is synchronous: the response does not leave the system until every citation has a verification status. The 280 ms overhead is well within the 10-second Q&A budget and invisible to the user. For document review (60-second budget) with 50+ citations, all citations are verified in parallel — total overhead is still under 500 ms. The business stakes determine the architecture: synchronous verification is mandatory.

**Q: How do you detect if the LLM misquotes a case even though the case exists?**

External validation (LexisNexis/Westlaw API) confirms the case exists but does not check whether the quoted holding is accurate. The NLI entailment check addresses this: it tests whether the specific claim in the response is entailed by the retrieved source text (the actual case text from the vector store). If the LLM says "In Smith v. Jones, the court held that X" but the retrieved chunk from Smith v. Jones says the court held Y, the entailment score is low and the citation is flagged. This is the "case exists but does not support the claim" failure mode described in Section 9, and the NLI check is the direct fix. The system must retrieve the actual source text and run entailment against it, not just verify the citation reference string.

**Q: What happens when LexisNexis is down and a lawyer needs urgent research results before a 2am filing deadline?**

Three-tier fallback activates. Tier 1: switch to Westlaw API (pre-configured backup, different API endpoint, < 30 ms to switch). Tier 2: if Westlaw also unavailable, use internal corpus-only validation — the platform verifies that cited sources exist in the firm's uploaded document corpus or in the internal legal corpus (CourtListener mirror for US federal courts), and marks responses as "CITATION_UNVERIFIED — external database temporarily unavailable." Tier 3: if both external APIs are down for > 30 minutes, the platform displays an in-app alert advising lawyers to verify citations manually using native Westlaw or LexisNexis interfaces. The platform continues returning responses — it never blocks legal work — but changes the verification badge from VERIFIED to CITATION_UNVERIFIED with a visible timestamp. The audit log records which verification tier was used for each response.

**Q: How do you handle a multi-jurisdiction query where the answer requires synthesizing US and UK law?**

The query is routed to both the US federal/state corpus and the UK corpus simultaneously. Each corpus retrieval returns the top-10 most relevant chunks from its jurisdiction. The system prompt instructs the LLM to clearly delineate US and UK holdings, cite each separately with jurisdiction labels, and explicitly note where the two systems diverge. Citation verification runs separately for each jurisdiction's citations: LexisNexis for US references, BAILII for UK references. The response includes a jurisdiction header per section ("Under US law (SDNY): ..."; "Under English law (CA): ..."). Lawyers working on cross-border M&A transactions are the primary users of multi-jurisdiction queries; the structured separation of jurisdictions is more useful than a blended synthesis.

**Q: Why is a legal-fine-tuned open-weight model not always better than a general frontier model for legal applications?**

Fine-tuned models are optimized for the distribution of their training data. Harvey's fine-tune performs best on US M&A and corporate law — the domains with abundant training data — but degrades on rare jurisdictions, emerging regulatory areas (EU AI Act, new state privacy statutes), and non-English legal systems where training data is sparse. A frontier model's broader pretraining captures these areas reasonably well. When grounded with strong retrieval (good chunking, high similarity threshold, NLI re-ranking), its citation accuracy approaches fine-tuned models on core domains and exceeds them on rare domains. The correct architecture is retrieval-first: the quality of retrieved context matters more than the base model for citation accuracy. A fine-tuned model with poor retrieval produces confidently wrong citations; a general model with good retrieval produces accurate grounded citations.

**Q: What does the bar exam benchmark measure, and what does it not measure about legal AI quality?**

Less than the headline suggests — OpenAI's widely quoted "90th percentile on the UBE" for GPT-4 (March 2023) was benchmarked against February Illinois test-takers, a pool skewed toward repeat takers who failed in July. Martínez, "Re-evaluating GPT-4's bar exam performance" (Artificial Intelligence and Law, 2024), re-scored it at roughly the 68th percentile against July takers, 62nd against first-time takers, and 48th against those who actually passed — 15th percentile on the essay component. The benchmark measures general legal knowledge and reasoning in a largely multiple-choice format. It does not measure: citation accuracy on real cases (bar exam is closed-book, no citations required); jurisdiction-specific document drafting quality; ability to apply law to novel facts in a complex transaction (bar exam facts are simplified); privilege classification accuracy; conflict detection precision; or latency under production load. A legal AI platform could score in the 95th percentile on the bar exam and still be unusable in practice if its citation verification fails, its matter isolation has a bug, or its document review takes 10 minutes per contract. The bar exam benchmark is useful for marketing and as a lower bound on legal reasoning capability — it is not a production quality metric.

**Q: How do you prevent a lawyer from inadvertently querying across matter boundaries through prompt injection?**

The Matter Context Enforcer injects a matter boundary instruction into every LLM system prompt: "You are a legal research assistant for matter [matter_id] at firm [firm_id]. You may only reference information from the documents provided in this context. You have no knowledge of any other matters or clients." The matter_id is extracted from the verified JWT — not from the user's query text. The Qdrant retrieval is scoped to the matter collection (Section 4.1) — even if a user types "search all matters" in the query, the retrieval layer queries only the authorized collection. The two-layer defense (JWT-enforced matter_id at retrieval + system prompt instruction at LLM) means that cross-matter injection requires both a retrieval bypass and an LLM system prompt injection simultaneously — substantially harder than single-layer enforcement.
