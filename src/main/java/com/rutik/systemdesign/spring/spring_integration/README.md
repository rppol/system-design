# Spring Integration (Enterprise Integration Patterns)

> Spring's implementation of the Enterprise Integration Patterns (EIP) book: message
> channels, endpoints, and the routing/transformation/aggregation primitives that
> connect systems in-process and across protocols. How it works, when to reach for
> it over plain `spring_messaging` (Kafka/RabbitMQ), and the Java DSL you write today.
> Spring Integration 6.x / Spring Boot 3.x.

---

## 1. Concept Overview

Spring Integration (SI) is a framework for building **message-driven** applications
inside a Spring app, modeled directly on the *Enterprise Integration Patterns* (EIP)
catalogue by Hohpe & Woolf. Its core abstraction is the **message** — a payload plus
headers — flowing through **channels** between **endpoints**. You assemble a flow as a
pipeline: an inbound adapter brings data in, a series of endpoints
(filter → transform → route → aggregate) process it, and an outbound adapter sends it
out.

The decisive thing to understand is its scope. SI is *not* primarily a Kafka/RabbitMQ
client (that is `spring_messaging` / Spring Cloud Stream). SI is an **integration
orchestration layer**: it gives you the EIP vocabulary — splitter, aggregator,
router, content enricher, claim check, message store — and a uniform messaging model
that can sit *in front of* many transports (files, FTP, JMS, HTTP, TCP, MQTT, JDBC,
mail, and yes Kafka/AMQP via channel adapters). It is how you express
"read files from this directory, validate each, split into line items, enrich from a
DB, route by type, aggregate per order, and write to JMS" as a declarative flow
rather than hand-rolled glue code.

It descends conceptually from the same lineage as Apache Camel and Mule — an
EIP-based integration engine — but expressed in idiomatic Spring beans and a fluent
Java DSL.

---

## 2. Intuition

**One-line analogy.** Spring Integration is the conveyor-belt system in a factory:
messages are boxes on belts (channels); machines along the belt (endpoints) inspect,
relabel, split, merge, and divert boxes; loading docks (channel adapters) connect the
belts to the outside world (files, queues, HTTP).

**Mental model.** Think of a flow as Unix pipes for messages:
`inbound | filter | transform | route | aggregate | outbound`. Each stage does one
thing and hands a `Message<?>` to the next via a channel. The channel is the seam —
swap a `DirectChannel` for a `QueueChannel` and the same flow becomes asynchronous
without touching the endpoints.

**Why it matters.** Senior interviews probe whether you know the *right tool*: when
the problem is "move events through one broker at scale" you reach for Kafka
(`spring_messaging`); when it is "glue several heterogeneous systems together with
classic integration patterns," SI's EIP vocabulary saves you from reinventing
splitters, aggregators, and correlation logic — and getting them subtly wrong.

**Key insight.** The channel is a first-class, swappable component. Because endpoints
communicate only through channels (never call each other directly), you can change
threading, persistence, and back-pressure characteristics by changing the channel —
the business logic stays untouched. That decoupling *is* the framework.

---

## 3. Core Principles

1. **Everything is a `Message<T>`.** A payload plus a `MessageHeaders` map (id,
   timestamp, correlation id, reply channel, custom keys). Endpoints consume and
   produce messages; headers carry routing/correlation metadata out-of-band.

2. **Endpoints connect via channels, never directly.** This pipes-and-filters
   decoupling is what lets you change concurrency, persistence, and transport without
   touching business logic.

3. **Channels are typed by behavior.** `DirectChannel` (synchronous, caller's
   thread), `QueueChannel` (buffered, polled, asynchronous), `PublishSubscribeChannel`
   (fan-out), `ExecutorChannel` (hand off to a thread pool). The channel choice sets
   threading and back-pressure.

4. **EIP primitives are built in.** Filter, transformer, router, splitter,
   aggregator, service activator, content enricher, claim check — you compose them
   rather than implement them.

5. **Adapters bridge protocols.** Inbound/outbound *channel adapters* (one-way) and
   *gateways* (request-reply) connect the messaging model to files, FTP, JMS, AMQP,
   HTTP, TCP/UDP, MQTT, JDBC, mail, Kafka, etc.

6. **Poller-driven where needed.** Pollable sources (files, JDBC, `QueueChannel`)
   advance under a configurable poller (rate, batch size, transaction) — the
   back-pressure and scheduling knob.

---

## 4. Types / Architectures / Strategies

### Channel types

| Channel | Threading | Semantics | Use when |
|---------|-----------|-----------|----------|
| `DirectChannel` (default) | Sender's thread | Point-to-point, synchronous | Simple in-process flow, transaction spans flow |
| `QueueChannel` | Polled by consumer | Buffered, async, point-to-point | Decouple producer/consumer rate; needs a poller |
| `ExecutorChannel` | Thread pool | Async point-to-point | Parallelism without an explicit poller |
| `PublishSubscribeChannel` | Sender or executor | Fan-out to all subscribers | Broadcast / parallel branches |
| `PriorityChannel` | Polled | Ordered by priority header | Priority handling |

### Endpoint (EIP) types

| Endpoint | EIP | Does |
|----------|-----|------|
| Filter | Message Filter | Drops messages failing a predicate |
| Transformer | Message Translator | Converts payload/headers |
| Router | Content-Based Router | Sends to one of N channels by content |
| Splitter | Splitter | One message → many (e.g. order → line items) |
| Aggregator | Aggregator | Many correlated messages → one (with release strategy) |
| Service Activator | Service Activator | Invokes a bean method on the message |
| Enricher | Content Enricher | Adds data (e.g. DB lookup) to the message |
| Bridge | Messaging Bridge | Connects two channels |

### Adapters vs gateways

- **Channel adapter** — one-way (inbound: external → flow; outbound: flow → external).
- **Gateway** — request-reply; the *messaging gateway* hides messaging behind a plain
  Java interface so callers see a normal method call.

---

## 5. Architecture Diagrams

### Pipes-and-filters: endpoints decoupled by channels

```
  [inbound      ]   ch1   +--------+  ch2  +-----------+ ch3 +-----------+ ch4 [outbound ]
  [file adapter ]-------->| filter |------>|transformer|---->| router    |---->[ JMS      ]
                          +--------+       +-----------+     +-----+-----+     [ adapter  ]
   reads *.csv            drop invalid     CSV -> Order            |  \
                                                                   v   v
                                                              chA(EU)  chB(US)
  Endpoints never call each other; they only read/write channels.
  Swap ch2 DirectChannel -> QueueChannel => same flow, now async, zero endpoint changes.
```

### Splitter + aggregator: scatter and re-gather by correlation id

```
                 splitter                         aggregator
  Order{3 items} ----> Item A (correlationId=order-7, seq 1/3) --\
                       Item B (correlationId=order-7, seq 2/3) ----> [ release when
                       Item C (correlationId=order-7, seq 3/3) --/    3/3 collected ]
                                                                          |
                                                                          v
                                                                   OrderResult{3 priced}
  Aggregator groups by correlation id, buffers in a MessageStore, and applies a
  release strategy (here: size = sequenceSize) + a timeout for missing parts.
```

The aggregator is the subtle one: it must correlate, buffer, decide *when* a group is
complete (release strategy), and handle parts that never arrive (group timeout). This
is exactly the logic SI gives you so you do not hand-roll it.

### Channel choice sets the threading/back-pressure profile

```
  DirectChannel   : producer --(same thread, blocks)--> consumer   (sync, in-TX)
  ExecutorChannel : producer --> [thread pool] --> consumer         (async, pool-bounded)
  QueueChannel    : producer --> [ buffer ] <-poller- consumer      (async, buffered, rate-limited)
```

One line of DSL (`.channel(c -> c.queue(500))`) changes the whole concurrency and
back-pressure behavior of the seam without touching either endpoint.

---

## 6. How It Works — Detailed Mechanics

### 6.1 A flow with the Java DSL

The modern way to define flows is `IntegrationFlow` beans (Spring Integration 6.x):

```java
@Configuration
@EnableIntegration
class OrderIngestFlow {

    @Bean
    IntegrationFlow ordersFromFiles() {
        return IntegrationFlow
            // inbound channel adapter: poll a directory for new files
            .from(Files.inboundAdapter(new File("/in/orders"))
                       .patternFilter("*.csv"),
                  e -> e.poller(Pollers.fixedDelay(Duration.ofSeconds(5))))
            .transform(Files.toStringTransformer())              // file -> String
            .filter((String csv) -> !csv.isBlank())              // Message Filter
            .transform(this::parseOrder)                         // Message Translator
            .split(Order.class, Order::getItems)                 // Splitter: order -> items
            .channel(c -> c.executor(taskExecutor()))            // parallelize per item
            .<LineItem, LineItem>transform(this::priceItem)      // enrich/price
            .aggregate(a -> a                                     // Aggregator
                .correlationStrategy(m ->
                    ((Message<?>) m).getHeaders().get("orderId"))
                .releaseStrategy(g -> g.size() == g.getSequenceSize()))
            .handle(Jms.outboundAdapter(connectionFactory())     // outbound adapter
                       .destination("pricedOrders"))
            .get();
    }
}
```

Each `.method(...)` is an endpoint; the implicit channels between them are
`DirectChannel`s until you override one (as with `.channel(c -> c.executor(...))`).

### 6.2 The messaging gateway: hide messaging behind an interface

A gateway lets callers invoke a flow as if it were a normal method — the framework
wraps the argument in a `Message`, sends it, and unwraps the reply:

```java
@MessagingGateway
public interface OrderGateway {
    @Gateway(requestChannel = "ordersIn", replyChannel = "ordersOut")
    OrderResult submit(Order order);     // looks synchronous to the caller
}

// caller — no messaging API in sight:
OrderResult result = orderGateway.submit(order);
```

Behind the interface, `submit` becomes "send a `Message<Order>` to `ordersIn`, await
the reply on `ordersOut`." This is the clean boundary between application code and the
integration layer.

### 6.3 Channels and threading

```java
@Bean MessageChannel sync()  { return new DirectChannel(); }      // caller's thread
@Bean MessageChannel async() { return new QueueChannel(500); }    // buffered, needs poller
@Bean MessageChannel pool()  { return new ExecutorChannel(taskExecutor()); }
@Bean MessageChannel fanout(){ return new PublishSubscribeChannel(); }
```

A `QueueChannel` requires a *polling consumer* downstream; a `DirectChannel` invokes
the next endpoint inline on the sender's thread, so an exception propagates straight
back and the whole flow can run in one transaction.

### 6.4 Aggregator correlation, release, and timeout

```java
.aggregate(a -> a
    .correlationStrategy(m -> m.getHeaders().get("orderId"))   // group key
    .releaseStrategy(group -> group.size() == group.getSequenceSize())  // complete?
    .groupTimeout(Duration.ofSeconds(30).toMillis())           // give up on missing parts
    .sendPartialResultOnExpiry(true)                           // emit what we have
    .messageStore(jdbcMessageStore()))                          // survive restarts
```

The `MessageStore` is what makes the aggregator durable: without it, an in-flight
group lives only in memory and is lost on restart. With a `JdbcMessageStore`, partial
groups survive a crash. This mirrors the saga/outbox durability theme — buffered state
must be persisted to survive failures.

### 6.5 Error handling

Each flow has an error channel; failures are wrapped in a `MessagingException` (which
carries the failed `Message`) and routed there:

```java
@Bean
IntegrationFlow errorFlow() {
    return IntegrationFlow.from("errorChannel")
        .handle(m -> {
            MessagingException ex = (MessagingException) m.getPayload();
            log.error("flow failed for {}", ex.getFailedMessage(), ex);
            // route to a dead-letter destination, alert, etc.
        })
        .get();
}
```

For polled, transactional sources you can also configure retry/advice chains on the
poller so a transient failure rolls back and is re-polled.

---

## 7. Real-World Examples

- **Spring Integration in batch/ETL pipelines** — file/FTP inbound adapters +
  splitter/aggregator are a classic for ingesting partner data feeds (CSV/XML drops)
  and normalizing them into internal events; pairs naturally with Spring Batch for
  the heavy chunk processing.
- **Spring Cloud Stream's foundation** — Spring Cloud Stream is built on Spring
  Integration's channel/adapter model; the binder abstraction sits on SI's messaging
  core, which is why the two share concepts.
- **Legacy-system bridging** — enterprises use SI's JMS/AMQP/TCP/mail adapters to put
  a uniform message flow in front of mainframe/MQ systems, applying EIP routing and
  translation between old and new.
- **IoT/MQTT ingestion** — SI's MQTT inbound adapter + router + aggregator collects
  and correlates device telemetry into per-device or per-window aggregates.
- **Apache Camel / MuleESB** — the broader EIP-engine category SI belongs to; Camel
  is the most common alternative when teams want a standalone integration runtime
  rather than an in-Spring library.

---

## 8. Tradeoffs

| Dimension | Spring Integration | spring_messaging / Spring Cloud Stream | Plain code |
|-----------|--------------------|-----------------------------------------|------------|
| Strength | EIP orchestration, many protocols, in-process flows | High-throughput single-broker (Kafka/Rabbit) streaming | Total control |
| Routing/aggregation | Built-in primitives | DIY (or Kafka Streams) | DIY |
| Protocol breadth | Very broad (files, FTP, JMS, TCP, MQTT, JDBC, mail…) | Broker-centric | Whatever you write |
| Throughput ceiling | Good, not Kafka-scale | Very high | Depends |
| Learning curve | Steep (EIP vocabulary, channels) | Moderate | Low |
| Best for | Heterogeneous integration glue | Event streaming backbone | Trivial one-off flows |

| Channel choice | Buys | Costs |
|----------------|------|-------|
| DirectChannel | Simplicity, single transaction across flow | No async/back-pressure; sender blocks |
| QueueChannel | Decoupling, buffering, rate control | Needs poller; buffered state can be lost without a store |
| ExecutorChannel | Parallelism | Loses single-transaction guarantee; ordering not preserved |

---

## 9. When to Use / When NOT to Use

**Use Spring Integration when** you must connect several heterogeneous systems
(files + JMS + HTTP + DB) and apply classic integration patterns — content routing,
splitting/aggregating, enrichment, correlation — inside a Spring app, and you want
those patterns provided rather than hand-built. It excels at in-process orchestration
and protocol bridging.

**Avoid it when** your need is high-throughput event streaming through a single
broker — use Kafka via `spring_messaging`/Spring Cloud Stream (or Kafka Streams) for
that; SI adds an abstraction layer you do not need. Avoid it for a trivial one-hop
"consume from queue, call service" flow where a plain `@KafkaListener`/`@RabbitListener`
is simpler. And be cautious if the team does not know EIP — the abstraction is
powerful but has a real learning curve, and misused channels create hidden threading
bugs.

**Rule of thumb:** reach for SI when the hard part is *orchestration across systems*;
reach for `spring_messaging` when the hard part is *throughput through one broker*.

---

## 10. Common Pitfalls

1. **Confusing SI with a Kafka client.** Teams adopt SI expecting Kafka-scale
   streaming and find an orchestration layer instead. *Fix:* use SI for integration
   patterns; use Spring Cloud Stream/`spring_messaging` for raw broker throughput.

2. **DirectChannel surprise threading.** A `DirectChannel` runs the consumer on the
   *sender's* thread, so a "fire and forget" send actually blocks the caller and runs
   the whole flow inline. *War story:* an HTTP request thread executed a long file
   flow synchronously and timed out. *Fix:* insert a `QueueChannel`/`ExecutorChannel`
   to hand off.

3. **Aggregator memory leak / lost groups.** Using the default in-memory message store
   meant incomplete groups (parts that never arrived) accumulated and were lost on
   restart. *Fix:* set a `groupTimeout` (+ `sendPartialResultOnExpiry`) and a durable
   `MessageStore` (JDBC) for groups that must survive crashes.

4. **No poller on a pollable source.** A `QueueChannel` or file adapter with no poller
   configured silently never consumes. *Fix:* configure a poller (default or explicit)
   on every pollable endpoint.

5. **Losing the transaction across an async channel.** Switching a channel to
   `ExecutorChannel`/`QueueChannel` moves work to another thread, so the original
   transaction no longer wraps the downstream work — a rollback no longer undoes it.
   *Fix:* keep transactional steps on a `DirectChannel`, or make downstream
   idempotent and transact per-segment.

6. **Header loss across transformers.** A custom transformer that builds a new
   `Message` without copying headers drops correlation/reply headers, breaking
   aggregation or request-reply. *Fix:* use `MessageBuilder.withPayload(...)
   .copyHeaders(...)` or return the payload and let SI preserve headers.

7. **Overusing SI for trivial flows.** Wrapping a one-line "consume and call service"
   in a full SI flow adds ceremony with no benefit. *Fix:* reserve SI for genuinely
   multi-step, multi-system integration.

---

## 11. Technologies & Tools

| Concern | Tools |
|---------|-------|
| Core | `spring-integration-core`, `@EnableIntegration`, `IntegrationFlow` Java DSL |
| Adapters | `spring-integration-file`, `-ftp`/`-sftp`, `-jms`, `-amqp`, `-http`, `-ip` (TCP/UDP), `-mqtt`, `-jdbc`, `-mail`, `-kafka` |
| Message stores | `JdbcMessageStore`, `RedisMessageStore`, `MongoDbMessageStore` (durable groups/claim check) |
| Higher layers | Spring Cloud Stream (binder model on SI), Spring Batch (pairs for chunk work) |
| Testing | `spring-integration-test`, `MockIntegration`, `@SpringIntegrationTest` |
| Alternatives | Apache Camel, Mule ESB |
| Monitoring | Micrometer integration metrics, `IntegrationGraphServer` (flow graph) |

---

## 12. Interview Questions with Answers

**What is Spring Integration and how is it different from spring_messaging / Kafka?**
Spring Integration is an in-app implementation of the Enterprise Integration Patterns
— a messaging model (messages, channels, endpoints) plus built-in routing,
transformation, splitting, and aggregation primitives, and adapters for many
protocols (files, FTP, JMS, HTTP, TCP, MQTT, JDBC, mail, Kafka). `spring_messaging`
and Spring Cloud Stream are about high-throughput streaming through a specific broker
like Kafka or RabbitMQ. The mental model: SI is for *orchestrating integration across
heterogeneous systems*; Kafka tooling is for *throughput through one broker*.

**What are the core abstractions in Spring Integration?**
Messages (payload + headers), channels (the pipes connecting components), and
endpoints (the components: filters, transformers, routers, splitters, aggregators,
service activators, adapters). Endpoints never call each other directly — they only
read from and write to channels, which is the pipes-and-filters decoupling. Adapters
and gateways bridge this messaging model to external protocols.

**Explain the main channel types and how they affect threading.**
`DirectChannel` (the default) invokes the next endpoint synchronously on the sender's
thread, so the whole flow runs inline and can share one transaction. `QueueChannel`
buffers messages and requires a downstream poller, decoupling producer and consumer
rates asynchronously. `ExecutorChannel` hands off to a thread pool for parallelism.
`PublishSubscribeChannel` fans a message out to all subscribers. Choosing the channel
sets the concurrency, transaction boundary, and back-pressure behavior of that seam.

**Why is the channel being a first-class, swappable component important?**
Because endpoints communicate only through channels, you can change threading,
buffering, persistence, and transport by swapping the channel without modifying any
business logic. For example, replacing a `DirectChannel` with a `QueueChannel` turns a
synchronous flow asynchronous, and the endpoints on either side are unchanged. That
decoupling is the central design benefit of the framework.

**What does a splitter and an aggregator do, and what makes the aggregator tricky?**
A splitter turns one message into many (e.g. an order into its line items); an
aggregator collects many correlated messages back into one. The aggregator is tricky
because it must correlate messages into groups (correlation strategy), decide when a
group is complete (release strategy), buffer partial groups somewhere, and handle
parts that never arrive (group timeout). Getting correlation, release, and timeout
right — and persisting the buffer so groups survive restarts — is exactly the logic
SI provides so you do not hand-roll it.

**How do you make an aggregator durable across restarts?**
Configure a persistent `MessageStore` (e.g. `JdbcMessageStore`) so in-flight groups
are written to a database instead of living only in memory, plus a `groupTimeout` so
groups missing parts are eventually released or expired rather than leaking. Without a
durable store, a crash loses all partially-aggregated groups. This mirrors the saga/
outbox principle that buffered intermediate state must be persisted to survive
failure.

**What is a messaging gateway?**
A gateway exposes a flow behind a plain Java interface: you annotate an interface with
`@MessagingGateway` and the framework implements it so a normal method call is turned
into "send a message to the request channel and await the reply on the reply
channel." It gives callers a clean, synchronous-looking API with no messaging code,
cleanly separating application logic from the integration layer. A channel adapter,
by contrast, is one-way (no reply).

**What is the difference between a channel adapter and a gateway?**
A channel adapter is one-directional — an inbound adapter brings external data into a
flow, an outbound adapter sends flow messages to an external system — with no reply
semantics. A gateway is request-reply: it sends a message and correlates a response
back, used when the caller needs an answer (inbound gateway: external request →
flow → response; messaging gateway: Java call → flow → return value).

**What is a poller and when do you need one?**
A poller drives pollable sources — `QueueChannel`s, file/JDBC inbound adapters — by
periodically pulling messages at a configured rate, batch size, and (optionally)
within a transaction. You need one on any pollable endpoint; without it, the source
silently never consumes. The poller is also your back-pressure and scheduling knob:
it controls how fast and in what batch size work enters the flow.

**How does error handling work in a Spring Integration flow?**
Failures are wrapped in a `MessagingException` that carries the failed `Message` and
routed to an error channel (the default `errorChannel`, or a flow-specific one). You
attach a flow to that channel to log, alert, or dead-letter the failure. For
transactional polled sources you can add retry/advice on the poller so transient
failures roll back and are re-polled. The failed message being preserved is what lets
you recover or DLQ it.

**Why can switching a channel to async break your transaction?**
A transaction is bound to a thread; when you move work onto another thread via an
`ExecutorChannel` or a polled `QueueChannel`, the downstream endpoints run outside the
original transaction, so a rollback upstream no longer undoes that downstream work.
Keep steps that must be atomic on a synchronous `DirectChannel`, or make the
asynchronous segment idempotent and transact it independently. This is a common,
subtle correctness bug when "just making it async."

**When would you choose Spring Integration over plain @KafkaListener or writing the glue yourself?**
Choose SI when the hard part is orchestration across multiple systems and you need EIP
primitives — content-based routing, splitting/aggregating with correlation, content
enrichment, claim check — that are error-prone to hand-build, and when you integrate
several protocols (files + JMS + HTTP + DB) under one model. For a single-hop "consume
from one broker, call a service," a plain `@KafkaListener`/`@RabbitListener` is simpler
and SI's abstraction is overkill.

**What is the relationship between Spring Integration and Spring Cloud Stream?**
Spring Cloud Stream is built on top of Spring Integration's channel/adapter model; its
binder abstraction sits on SI's messaging core, which is why they share concepts like
channels and message handlers. Spring Cloud Stream specializes SI for broker-backed
streaming (Kafka/Rabbit) with binders and partitioning, whereas SI is the general EIP
engine underneath. Knowing SI helps you understand what Spring Cloud Stream does under
the hood.

**How does Spring Integration relate to Apache Camel?**
Both are EIP-based integration engines implementing the same pattern catalogue
(routers, transformers, aggregators, adapters). Spring Integration is a Spring-native
library expressed in beans and a Java DSL, ideal when you are already in a Spring app.
Apache Camel is a broader, standalone integration runtime with its own DSLs and an
enormous component set, often chosen when integration is the primary concern or you
want a framework-agnostic engine. They overlap heavily; the choice is mostly ecosystem
fit.

**What are message headers used for, and what is the risk when transforming messages?**
Headers carry out-of-band metadata — message id, timestamp, correlation id, sequence
number, reply channel, and custom keys — that drive routing, aggregation, and
request-reply correlation. The risk is that a custom transformer which constructs a
brand-new `Message` without copying headers silently drops correlation/reply headers,
breaking aggregation or gateways. The fix is to use `MessageBuilder.copyHeaders(...)`
or simply return the payload and let SI preserve the existing headers.

**How do you test a Spring Integration flow?**
Use `spring-integration-test` with `@SpringIntegrationTest`, which lets you mock
inbound adapters and capture/assert on channel traffic; `MockIntegration` provides
mock message sources and handlers, and you can send test messages directly to input
channels and verify what arrives on output channels. The decoupled channel model
makes flows testable in isolation — you exercise an endpoint by feeding its input
channel and inspecting its output channel without standing up the real adapters.

---

## 13. Best Practices

- **Use the Java DSL (`IntegrationFlow`)** for readable, refactorable flows over XML.
- **Pick channels deliberately** — `DirectChannel` for in-transaction synchronous
  steps, `Queue`/`Executor` only where you genuinely want async + back-pressure.
- **Always configure a poller** on pollable sources; treat a missing poller as a bug.
- **Give aggregators a `groupTimeout` and a durable `MessageStore`** so groups neither
  leak nor vanish on restart.
- **Preserve headers** across transformers (`copyHeaders`) to keep correlation and
  reply routing intact.
- **Route failures to an explicit error channel** and dead-letter/alert, rather than
  swallowing exceptions.
- **Do not async across a transaction boundary** unless the downstream is idempotent.
- **Reach for Spring Cloud Stream/Kafka** when the requirement is broker throughput,
  not EIP orchestration — pick the right tool.

---

## 14. Case Study

### Partner-feed ingestion: heterogeneous files into a priced-order event stream

**Problem.** A retailer receives nightly order feeds from dozens of partners as CSV
and XML files dropped on SFTP. Each file holds many orders; each order has many line
items that must be priced (a DB + pricing-service lookup), then the priced orders must
be published to an internal JMS topic and, for EU partners, also to a compliance HTTP
endpoint. The original solution was a tangle of cron jobs, ad-hoc file parsing, and
hand-rolled "wait for all line items" code that occasionally published incomplete
orders.

**Requirements.**
- Ingest multiple file formats from SFTP reliably, one poll at a time.
- Split orders → line items, price items in parallel, re-aggregate per order.
- Never publish an order with missing line items; survive a restart mid-batch.
- Route EU partners to an extra compliance endpoint.

**Design.**

1. **Inbound + format routing.** An SFTP inbound channel adapter polls the drop
   directory; a content-based **router** sends `*.csv` and `*.xml` to format-specific
   transformers that both produce a canonical `Order`.

2. **Split → parallel price → aggregate.** A **splitter** emits line items (tagged
   with `orderId` and a sequence size); an `ExecutorChannel` prices them in parallel;
   an **aggregator** re-groups by `orderId`, releasing only when `size ==
   sequenceSize`, backed by a `JdbcMessageStore` and a 30-minute `groupTimeout`.

3. **Outbound fan-out.** A **publish-subscribe channel** sends every priced order to a
   JMS outbound adapter; a **filter** on a second branch passes only EU partners to an
   HTTP outbound gateway.

```java
.from(Sftp.inboundAdapter(sessionFactory()).remoteDirectory("/feeds"),
      e -> e.poller(Pollers.fixedDelay(Duration.ofMinutes(1))))
.route("headers['fileExt']", r -> r
    .subFlowMapping("csv", sf -> sf.transform(csvToOrder()))
    .subFlowMapping("xml", sf -> sf.transform(xmlToOrder())))
.split(Order.class, Order::getItems)
.channel(c -> c.executor(pricingPool()))
.transform(this::priceItem)
.aggregate(a -> a
    .correlationStrategy(m -> m.getHeaders().get("orderId"))
    .releaseStrategy(g -> g.size() == g.getSequenceSize())
    .groupTimeout(Duration.ofMinutes(30).toMillis())
    .messageStore(jdbcMessageStore()))      // durable -> survives restart
.publishSubscribeChannel(p -> p
    .subscribe(f -> f.handle(Jms.outboundAdapter(cf()).destination("pricedOrders")))
    .subscribe(f -> f.filter((Order o) -> o.isEu())
                     .handle(Http.outboundGateway("https://compliance/api/orders"))));
```

**Broken → fixed (the incomplete-order publish bug):**

```java
// BROKEN: default in-memory aggregator, no group timeout.
// If pricing one item failed/was slow, the group never completed; on the nightly
// restart the in-memory partial groups were LOST -> some orders silently dropped,
// and a race occasionally released a group early -> order published missing items.
.aggregate()   // defaults: in-memory store, no timeout

// FIXED: explicit release on full sequence, durable JDBC store, and a timeout that
// emits partial + alerts rather than dropping.
.aggregate(a -> a
    .releaseStrategy(g -> g.size() == g.getSequenceSize())
    .groupTimeout(Duration.ofMinutes(30).toMillis())
    .sendPartialResultOnExpiry(true)         // surface incomplete orders, don't drop
    .messageStore(jdbcMessageStore()))
```

**Outcomes (measured).**
- Incomplete/dropped orders: **several per week → 0**; partial groups now persist
  across the nightly restart and either complete or are flagged on timeout.
- The hand-rolled "wait for items" code (≈400 lines, the source of the race) was
  deleted in favor of the built-in aggregator.
- Parallel pricing via the `ExecutorChannel` cut a large partner's file processing
  time roughly in half versus the previous sequential loop.
- EU compliance routing became a one-line filter branch instead of a separate cron
  job, eliminating a class of "forgot to send compliance copy" incidents.

**Tradeoffs accepted.** The team took on SI's learning curve and the JDBC message
store's extra DB load, and accepted that this is orchestration-scale (dozens of files/
night), not Kafka-scale streaming — the right fit for a heterogeneous, pattern-heavy
integration rather than a high-throughput event backbone.

---

## See Also

- [spring_messaging](../spring_messaging/README.md) — Kafka/RabbitMQ at throughput;
  the "broker-centric" alternative to SI's EIP orchestration.
- [spring_events_and_scheduling](../spring_events_and_scheduling/README.md) —
  in-process `ApplicationEvent` and `@Scheduled`, a lighter-weight decoupling.
- [../../backend/event_driven_fundamentals/](../../backend/event_driven_fundamentals/) —
  choreography vs orchestration and EIP at the architecture level.
- [../../java/microservices_patterns/](../../java/microservices_patterns/) — outbox,
  saga, and the same "persist buffered state" durability theme as the aggregator.
