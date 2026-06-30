 VERG — Technical Specification
**Vectorised Electronic ReGistry**
`v1.0 — 2026` · Author: Sharath Prasad
 
> An open specification for a next-generation semantically searchable registry platform, designed as a Digital Public Good (DPG).
 
---
 
## Overview
 
VERG augments traditional structured search with vector embeddings, enabling natural language queries across heterogeneous registries. It preserves trust by design using Decentralised Identifiers (DID), Verifiable Credentials, and Schema-Driven Behavioural Control.
 
VERG is **not a product — it is a blueprint**. Any registry operator can implement it by annotating an OpenAPI schema with VERG extension fields. Those annotations drive automatic pipeline configuration with minimal or no custom code required.
 
---
 
## Problem Statement
 
Existing registry infrastructure has four critical gaps:
 
| # | Gap | Description |
|---|-----|-------------|
| 1 | **Accessibility / Digital Inclusion** | Interfaces require technical literacy. Rural, low-bandwidth, voice-first, and multilingual use cases remain unsolved |
| 2 | **No Cross Registry Search** | Each registry is an island. Queries cannot span crop, seed, livestock, or other registries without custom integration effort |
| 3 | **Data Quality Blind Spots** | Entry errors, spelling mistakes, and duplicates go undetected. No anomaly detection layer exists in current registries |
| 4 | **Keyword-Only Search** | Existing registries rely on exact match or fuzzy string match. Misspellings, synonyms, and conceptual queries return zero results |
 
---
 
## Core Capabilities
 
- Registry acting as a single source of truth
- Phygital-isation of every registry entry (physical + digital)
- RBAC-based discovery with consent
- Trust Triangle for Claim and Attest workflows
- Conceptual / semantic query enablement
- Data ingestion from legacy form-filling to document and image-driven ingestion
- Verifiable claims of registry records
- OpenAPI-based multi-tenant API management using API keys and secrets
---
 
## Architecture
 
### Design Philosophy
 
VERG fuses four specialised data store layers into a single coherent API surface:
 
| Layer | Role |
|-------|------|
| **Structured Store** | Source of truth for records |
| **Full-Text / Fuzzy Search** | Keyword and approximate matching |
| **Vector Store** | Semantic / conceptual similarity search |
| **In-Memory Cache** | Query-level caching for performance |
 
### Trust Model
 
VERG implements the W3C Verifiable Credentials trust triangle:
 
```
         Holder
        /      \
  Verifiable   Proof
  Credential       \
      /             \
  Issuer  ------>  Verifier
           Trust
```
 
---
 
## Technology Stack
 
### Data Layer
 
| Component | Purpose |
|-----------|---------|
| PostgreSQL | Structured records — reliable source of truth |
| Elasticsearch | Full-text and fuzzy search |
| Qdrant | Vector similarity search |
| Redis | In-memory cache |
 
### Microservices Layer
 
| Component | Purpose |
|-----------|---------|
| Spring Boot (Java 17) | Core microservices registry framework |
| OpenAPI 3.x | Schema definitions and VERG extensions |
| JWT / OAuth 2.0 | Authentication and authorisation |
| Kong | RBAC, rate limiting, API key and secrets management |
 
### Ops & Tooling
 
| Component | Purpose |
|-----------|---------|
| Docker / Kubernetes | Container orchestration |
| Embedding Models | Domain-specific choice by implementer |
| Prometheus / Grafana | Observability and metrics |
 
---
 
## Functional Verbs
 
### Ingestion Verbs
- **Enrol** — Register a new entity in the registry
- **Authenticate** — Verify identity of the actor
- **Attest** — Provide a witness or authoritative claim
- **Update** — Modify an existing registry record
### Discovery Verbs
- **Claim** — Assert ownership or association with a record
- **Consent** — Grant or revoke data access permissions
- **Discover** — Search and retrieve registry records
---
 
## Data Flow
 
### Ingestion Pipeline
 
```
Schema Registration
    → Record Ingestion
    → Field Validation
    → Vectorisation (GPU / CPU)
    → Store in PostgreSQL
    → Index to Elasticsearch + VectorDB
```
 
### Query Pipeline
 
```
Natural Language Query
    → Auth + Scope Check
    → Redis Cache Hit?
        ├── YES → Return cached result
        └── NO  →
              ├── Semantic path: Embed Query → Vector Search → Merge & Rank → Cache
              └── Keyword path:  Key/Fuzzy Query → ES Search → Fetch & Cache
```
 
> **Key Design Decision:** Vector behaviour is **disabled by default**. Vectorisation must be explicitly declared per field using the `x-verg-vector` extension in the OpenAPI schema. This keeps cost control in the hands of the implementer.
 
---
 
## Key Design Principles
 
| Principle | Description |
|-----------|-------------|
| **Semantic Search** | Search by meaning, not just keywords, across any registry domain |
| **Low Code Config** | Configure registries via schema — minimal or no custom implementation |
| **Privacy First** | Field-level access control with query scoping baked into schema |
| **Federation Ready** | Cross-semantic queries across distributed registry deployments |
| **Schema Driven** | OpenAPI schema controls behaviour of every field in the entity |
| **Scalability** | Query-based caching recommended in spec for high-throughput scenarios |
| **Cost Control** | Schema-driven controls define vector embedding behaviour per field |
| **Document Proof** | Tamper-proofing via document proof and verifiable credentialing |
 
---
 
## Success Metrics
 
| Metric | Target | Measurement | Owner |
|--------|--------|-------------|-------|
| P95 Latency | < 300ms | Load test against registry implementation | DPG Infra Team |
| Semantic Relevance Score | > 85% | Human eval or active feedback loop | Product Team |
| Operator Onboarding Time | — | Time to first live query, new registry | Spec Team |
| Scalability | 350 TPS | Load testing activity | DPG Infra Team |
| Availability | 99.9% | Uptime monitoring | DPG Infra Team |
| Cross Registry Accuracy | — | Federated query benchmarking | DPG Infra Team |
 
---
 
## VERG Schema Extension Reference
 
VERG behaviour is declared at the field level via OpenAPI extension annotations:
 
```yaml
# Example VERG-annotated OpenAPI field
properties:
  farmerName:
    type: string
    x-verg-vector: true          # Enable semantic vectorisation for this field
    x-verg-searchable: true      # Include in full-text index
    x-verg-access-scope: public  # Field-level RBAC scope
    x-verg-pii: false            # PII classification for privacy control
```
 
> Vector is **opt-in per field**. Fields without `x-verg-vector: true` are never vectorised.
 
---
 
## Specification Goals
 
- **Semantic Search** — Natural language queries of registries without technical literacy
- **Digital Inclusive Registries** — Voice-first access without mandating technical knowledge
- **Privacy First, Federation Ready** — Designed for distributed, consent-driven deployment as a Digital Public Good
---
 
## Contributing
 
Contributions are welcome — implementation guides, schema extensions, new registry domain profiles, and federation patterns.
 
See [CONTRIBUTING.md](CONTRIBUTING.md) to get started.
 
---
 
## License
 
MIT License — see [LICENSE](LICENSE) for details.
 
---
 
*VERG Spec v1.0 — 2026 · Sharath Prasad*
