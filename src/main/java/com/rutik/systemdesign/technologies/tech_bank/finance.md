# Payments & finance - technology bank

<!-- tech-bank tier: finance -->

The 117 tools whose PRIMARY role, the first and best-weighted one, sits in
the **Payments & finance** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### 360T
**Short:** Multi-dealer FX trading platform where corporates and banks request quotes for spot, forwards and swaps from many liquidity providers.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1, finance/trading-and-market-data @2

### Adyen
**Short:** Payment platform that runs gateway, risk, processing and merchant acquiring as one stack for online and in-store payments.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Adyen for Platforms
**Short:** Adyen's product for marketplaces and platforms that onboard sub-merchants, split payments and pay out their balances.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Anchorage Digital
**Short:** Crypto platform for institutions that offers qualified custody, trading and staking of digital assets.
**Kind:** tech
**Lang:** *
**Roles:** finance/digital-asset-custody @1

### Anrok
**Short:** Sales tax and VAT platform for SaaS companies that calculates tax on invoices from the billing system and tracks registrations.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Artio
**Short:** Real Logic's open-source FIX and FIXP engine for Java, built on Aeron for low-latency session handling.
**Kind:** tech
**Lang:** java
**Roles:** finance/trading-and-market-data @1

### Avalara AvaTax
**Short:** Avalara's tax calculation API that resolves addresses to jurisdictions, applies product tax codes and rates, and manages exemption certificates.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Bitcoin Core
**Short:** Reference Bitcoin full node and wallet, used to validate blocks, track unspent outputs and broadcast transactions.
**Kind:** tech
**Lang:** cpp
**Roles:** finance/digital-asset-custody @1

### BitGo
**Short:** Digital-asset custody and wallet platform for institutions, known for its multisignature wallets.
**Kind:** tech
**Lang:** *
**Roles:** finance/digital-asset-custody @1

### BlackLine
**Short:** Finance close software for account reconciliation, transaction matching and journal entry management.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Braintree
**Short:** PayPal's payment gateway and processing service for cards, PayPal and digital wallets.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Braintree Hosted Fields
**Short:** Braintree's per-field card input iframes that tokenize card data, so the merchant's page never handles the raw card number.
**Kind:** tech
**Lang:** js
**Roles:** finance/payments-and-acquiring @1, security/privacy-and-compliance @2

### Chainalysis
**Short:** Blockchain analytics for tracing funds and screening crypto addresses and transactions for sanctions and illicit-activity risk.
**Kind:** tech
**Lang:** *
**Roles:** finance/digital-asset-custody @1, finance/fraud-and-financial-compliance @2

### Chargebacks911
**Short:** Outsourced chargeback management service that prepares and files representments for merchants.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Chargebee
**Short:** Subscription billing and revenue management platform that runs plans, invoices and dunning on top of several payment gateways.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Chargeflow
**Short:** Automated chargeback service that collects evidence and files dispute responses for online merchants.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Checkout.com
**Short:** Global payment service provider and acquirer with card acquiring, local payment methods and payouts behind one API.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Clearstream
**Short:** Deutsche Boerse Group's international central securities depository and custody business for settling and holding securities.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### CLSSettlement
**Short:** CLS's payment-versus-payment service that settles both currency legs of an FX trade together to remove settlement risk.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1, finance/trading-and-market-data @2

### CME Clearing
**Short:** CME Group's central counterparty for its futures and options, which margins positions with SPAN and SPAN 2.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### CME CORE
**Short:** CME's margin calculator that estimates SPAN and SPAN 2 requirements for a portfolio before the trades are placed.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### CME Globex
**Short:** CME Group's electronic trading platform, where each product has its own configured matching algorithm.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### CME MDP 3.0
**Short:** CME's market data protocol: SBE-encoded messages over UDP multicast with market-by-order and market-by-price books.
**Kind:** spec
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Coinbase Exchange
**Short:** Coinbase's crypto spot venue with a price-time priority order book and four self-trade prevention modes.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1, finance/digital-asset-custody @3

### Coinbase Prime
**Short:** Coinbase's platform for institutions that combines digital-asset custody, trading and financing.
**Kind:** tech
**Lang:** *
**Roles:** finance/digital-asset-custody @1

### ComplyAdvantage
**Short:** Risk data and screening API for sanctions, PEP and adverse media checks at onboarding and on payments.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Dow Jones Risk and Compliance
**Short:** Dow Jones data on sanctioned persons, PEPs and adverse media, delivered as feeds for screening programs.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### DTC
**Short:** The Depository Trust Company, the DTCC subsidiary that is the central securities depository for US securities and settles by book entry.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### EPN
**Short:** Electronic Payments Network, The Clearing House's ACH Operator and the private-sector alternative to FedACH.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Ethoca Alerts
**Short:** Mastercard-owned alert service that tells a merchant about a confirmed fraud or dispute, so it can refund before a chargeback.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Eurex Clearing
**Short:** Deutsche Boerse Group's central counterparty for Eurex derivatives and other products.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Euroclear
**Short:** International central securities depository group that settles and safekeeps European and international securities.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### EuroCTP
**Short:** The EU consolidated tape provider for shares and ETFs, which publishes one post-trade view across EU trading venues.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### fairCT
**Short:** Ediphy's consolidated tape for EU bonds, the provider that ESMA selected for the bond tape.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### FedACH
**Short:** The Federal Reserve Banks' ACH Operator, which clears and settles ACH files and publishes the Same Day ACH processing schedule.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### FedNow Service
**Short:** The Federal Reserve's instant payment service, which runs 24x7x365 and settles each payment in Federal Reserve master accounts.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Fedwire Funds Service
**Short:** The Federal Reserve's real-time gross settlement wire system, whose transfers are immediate, final and irrevocable.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Feedzai
**Short:** Risk platform for banks and processors that scores payments for fraud and supports AML transaction monitoring.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Finix
**Short:** Payments infrastructure provider that lets a software platform become a payment facilitator or run card acceptance through it.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Fireblocks
**Short:** Digital-asset custody and transfer platform with MPC key signing and a transaction policy engine.
**Kind:** tech
**Lang:** *
**Roles:** finance/digital-asset-custody @1

### Fiserv
**Short:** US financial technology company that provides merchant acquiring and processing, issuer processing and core banking systems.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Formance Ledger
**Short:** Open-source programmable ledger with a scripting language for money movements that post to many accounts at once.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1, data-access/transactions-and-consistency @2

### Forter
**Short:** Real-time fraud decision service for online merchants that approves or declines orders and can cover chargebacks on approved ones.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Geth
**Short:** Go Ethereum, an Ethereum execution client used to run a node, index chain data and broadcast transactions.
**Kind:** tech
**Lang:** go
**Roles:** finance/digital-asset-custody @1

### Joda-Money
**Short:** Java library whose Money and BigMoney types pair an amount with a currency and require a rounding mode on every scale change.
**Kind:** tech
**Lang:** java
**Roles:** finance/ledger-billing-and-treasury @1

### Jumio
**Short:** Identity verification service that checks ID documents and runs selfie and liveness matching for KYC.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Kount
**Short:** Fraud and identity trust platform that scores card-not-present transactions and account events.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Kyriba
**Short:** Treasury management platform for cash positioning, liquidity forecasting, bank connectivity and payments.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Lago
**Short:** Open-source usage metering and billing engine that aggregates events into invoices and drops duplicate events by transaction id.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### LCH
**Short:** LSEG's clearing house group, the central counterparty for swaps, repo and other products.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### LexisNexis Bridger Insight XG
**Short:** LexisNexis screening software that checks names against sanctions, PEP and other watchlists with a configurable matcher.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Lithic
**Short:** Card issuing platform and issuer processor that lets a company launch and control card programs through an API.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### LSEG World-Check
**Short:** LSEG's risk intelligence database of sanctioned persons, PEPs and adverse media, used for customer screening.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Marqeta
**Short:** Card issuing processor with an API for card programs, spend controls and just-in-time funding of each authorization.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Mastercard Decision Intelligence
**Short:** Mastercard's network-side transaction risk score, passed to issuers during authorization.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Mastercard Identity Check
**Short:** Mastercard's EMV 3-D Secure program for authenticating cardholders in online card payments.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1, security/authentication-and-identity @2

### Mastercard MDES
**Short:** Mastercard Digital Enablement Service, which issues and manages network tokens for Mastercard cards.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Mastercard Move
**Short:** Mastercard's portfolio of domestic and cross-border money movement services, including push payments to cards.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Mastercom
**Short:** Mastercard's platform where issuers and acquirers file and answer chargebacks and other dispute cases.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Modern Treasury
**Short:** Payment operations API that connects to banks to send ACH and wires, keep ledgers and reconcile payments.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1, finance/ledger-billing-and-treasury @2

### Modern Treasury Ledgers
**Short:** Modern Treasury's hosted double-entry ledger API, whose transactions must balance per currency.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1, data-access/transactions-and-consistency @2

### MoldUDP64
**Short:** Nasdaq's sequenced UDP framing protocol for multicast market data, with a re-request server for gap recovery.
**Kind:** spec
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Moneta
**Short:** Reference implementation of the Java Money and Currency API from JSR 354, with currency units, monetary amounts and rounding.
**Kind:** tech
**Lang:** java
**Roles:** finance/ledger-billing-and-treasury @1

### Moov ACH
**Short:** Open-source Go library and HTTP service that reads, writes and validates NACHA ACH files.
**Kind:** tech
**Lang:** go
**Roles:** finance/payments-and-acquiring @1

### Moov Watchman
**Short:** Open-source Go search service over OFAC and other sanctions lists for name screening.
**Kind:** tech
**Lang:** go
**Roles:** finance/fraud-and-financial-compliance @1

### Nasdaq TotalView-ITCH
**Short:** Nasdaq's binary market-by-order data feed, with fixed-layout messages for each order add, execution and cancel.
**Kind:** spec
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Netcetera 3DS Server
**Short:** Netcetera's commercial 3-D Secure server for PSPs and merchants, sold beside its ACS product for issuers.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1, security/authentication-and-identity @2

### NICE Actimize
**Short:** Financial crime platform for transaction monitoring, case management and SAR workflows at banks.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Notabene
**Short:** Travel rule network that exchanges originator and beneficiary data between crypto-asset service providers.
**Kind:** tech
**Lang:** *
**Roles:** finance/digital-asset-custody @1, finance/fraud-and-financial-compliance @2

### NSCC
**Short:** National Securities Clearing Corporation, the DTCC subsidiary that is the central counterparty for most US broker-to-broker equity and bond trades.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Onfido
**Short:** Identity verification service for document and biometric checks during customer onboarding.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### OnixS
**Short:** Vendor of FIX engines, market data handlers and venue SDKs, including CME iLink 3 order entry.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Open Banking Standard
**Short:** The UK Open Banking API and security specification that banks expose to account information and payment initiation providers.
**Kind:** spec
**Lang:** *
**Roles:** finance/payments-and-acquiring @1, apis-frameworks/data-formats-and-api-contracts @2

### OpenSanctions
**Short:** Open dataset that merges sanctions lists and PEP data into one entity graph with the relationships between entities.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Orchimate
**Short:** FIX Trading Community's browser for the FIX Latest dictionary of tags, messages and code sets.
**Kind:** tech
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Plaid Auth
**Short:** Plaid product that returns a user's bank account and routing numbers after bank login, in place of micro-deposit checks.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### QuickFIX
**Short:** Open-source C++ FIX engine with session management, message stores and data-dictionary validation.
**Kind:** tech
**Lang:** cpp
**Roles:** finance/trading-and-market-data @1

### QuickFIX/J
**Short:** Open-source Java FIX engine with session management, message stores and data-dictionary validation.
**Kind:** tech
**Lang:** java
**Roles:** finance/trading-and-market-data @1

### QuickFIX/n
**Short:** Open-source .NET FIX engine that follows the QuickFIX design.
**Kind:** tech
**Lang:** csharp
**Roles:** finance/trading-and-market-data @1

### Rapid Dispute Resolution
**Short:** Visa service that resolves eligible disputes automatically with merchant-set refund rules before they become chargebacks.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Recurly
**Short:** Subscription billing platform with plans, invoicing and dunning for consumer and B2B subscriptions.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Riskified
**Short:** E-commerce fraud decision service that approves orders and covers approved orders against fraud chargebacks.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### RTP network
**Short:** The Clearing House's instant payment network, open 24/7, with final settlement and request-for-payment messages.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Signifyd
**Short:** Commerce protection service that decides orders and covers approved ones against fraud chargebacks.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Simple Binary Encoding
**Short:** FIX Trading Community binary encoding with fixed-layout fields, repeating groups and variable-length data, plus a codec generator.
**Kind:** spec
**Lang:** *
**Roles:** finance/trading-and-market-data @1, apis-frameworks/data-formats-and-api-contracts @2

### SoupBinTCP
**Short:** Nasdaq's lightweight sequenced TCP session protocol for delivering and replaying feeds such as ITCH and order entry such as OUCH.
**Kind:** spec
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Sovos
**Short:** Tax compliance software for tax determination, e-invoicing mandates and information reporting.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### SPAN 2
**Short:** CME's margin methodology that replaces SPAN for covered products, such as energy and equity futures and options.
**Kind:** concept
**Lang:** *
**Roles:** finance/trading-and-market-data @1

### Spreedly
**Short:** Payment orchestration platform with a card vault that routes tokenized payments to many gateways.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Stripe
**Short:** Payment service provider with APIs for online payments, billing and payouts, and an idempotency key on every POST request.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Stripe Billing
**Short:** Stripe's subscription and invoicing product with prorations, usage meters, Smart Retries and credit notes.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Stripe Connect
**Short:** Stripe's product for marketplaces and platforms that onboard connected accounts and route funds to them.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Stripe Disputes API
**Short:** Stripe API for listing disputes, submitting evidence and accepting or contesting chargebacks in code.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Stripe Elements
**Short:** Stripe's prebuilt card input components in iframes that return a payment method token to the merchant page.
**Kind:** tech
**Lang:** js
**Roles:** finance/payments-and-acquiring @1, security/privacy-and-compliance @2

### Stripe Radar
**Short:** Stripe's built-in fraud scoring with machine-learning risk scores, rules and per-merchant risk settings.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Stripe Reporting API
**Short:** Stripe API for payout reconciliation and balance reports, itemized per balance transaction.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Stripe Tax
**Short:** Stripe's tax calculation and registration monitoring for payments, invoices and subscriptions.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### SWIFT gpi
**Short:** SWIFT's global payments innovation service that tracks a cross-border payment end to end with a shared UETR.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### SWIFTNet
**Short:** SWIFT's secure messaging network that banks and corporates use for payment instructions and account statements.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1, finance/ledger-billing-and-treasury @2

### TaxJar
**Short:** Sales tax calculation, economic-nexus tracking and filing service for US online sellers.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### TigerBeetle
**Short:** Open-source financial transactions database built around double-entry accounts and transfers, with pending, post and void transfers.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1, data-access/transactions-and-consistency @2

### Trintech Cadency
**Short:** Trintech's financial close platform for account reconciliation, transaction matching and close tasks.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### TRM Labs
**Short:** Blockchain intelligence platform for tracing funds and screening crypto addresses for risk.
**Kind:** tech
**Lang:** *
**Roles:** finance/digital-asset-custody @1, finance/fraud-and-financial-compliance @2

### TrueLayer
**Short:** Open banking platform for payment initiation and account data across the UK and Europe.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Unit21
**Short:** Transaction monitoring and case management platform with rules for fraud and AML programs at fintechs.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Verifi Order Insight
**Short:** Visa-owned Verifi service that shares order details with issuers, so a cardholder question can close before it becomes a dispute.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Vertex O Series
**Short:** Vertex's enterprise indirect tax engine for sales tax and VAT calculation, often embedded in ERP systems.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### VIES
**Short:** The European Commission's VAT Information Exchange System for checking EU VAT identification numbers.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1

### Visa Account Updater
**Short:** Visa service that supplies new card numbers and expiry dates for credentials that merchants store on file.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Visa Advanced Authorization
**Short:** Visa's network-side risk score, sent to issuers with each authorization request.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Visa Consumer Authentication Service
**Short:** Visa's hosted access control server for issuers that run 3-D Secure without their own ACS.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1, security/authentication-and-identity @2

### Visa Direct
**Short:** Visa's push-payment service that sends funds to eligible cards and accounts.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Visa Resolve Online
**Short:** Visa's platform where issuers and acquirers file and answer each stage of a dispute.
**Kind:** tech
**Lang:** *
**Roles:** finance/fraud-and-financial-compliance @1

### Visa Secure
**Short:** Visa's EMV 3-D Secure program for authenticating cardholders in online card payments.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1, security/authentication-and-identity @2

### Visa Token Service
**Short:** Visa's network tokenization service that replaces stored card numbers with tokens and per-transaction cryptograms.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Worldpay
**Short:** Merchant acquirer and payment processor for in-store and online card acceptance.
**Kind:** tech
**Lang:** *
**Roles:** finance/payments-and-acquiring @1

### Zuora
**Short:** Subscription management platform for pricing, billing, collections and revenue recognition.
**Kind:** tech
**Lang:** *
**Roles:** finance/ledger-billing-and-treasury @1
