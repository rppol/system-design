# Structured Outputs and Instructor — Deep Dive

---

## 1. Concept Overview

Structured outputs refers to the pattern of forcing LLM responses to conform to a defined schema — a Pydantic model, TypedDict, or JSON schema — rather than parsing free-form text. Instructor is the de facto Python library for this pattern, wrapping the OpenAI/Anthropic SDK to validate and retry structured outputs automatically.

Two mechanisms exist for structured outputs: (1) native structured output APIs (OpenAI `response_format={"type": "json_schema", ...}`, Anthropic [tool use](../agents_and_tool_use/README.md)), which constrain the model at the token sampling level; and (2) prompt-based parsing (ask the model to output JSON, parse the response), which is less reliable.

**Current versions**: instructor 1.x (2024), openai 1.x JSON mode / structured outputs
**Production adoption signal**: Instructor has 8K+ GitHub stars as of 2024. The pattern is used in virtually every production LLM application that extracts structured information.

---

## 2. Intuition

**One-line analogy**: Instructor is to LLM outputs what Pydantic is to API inputs — it validates that the LLM's response conforms to your schema and retries automatically if it doesn't.

**Mental model**: You want an LLM to extract a resume into structured fields: name, email, years_experience, skills. Without structured outputs, you get free text and write fragile regex parsers. With Instructor, you define a `Resume` Pydantic model and call `client.chat.completions.create(response_model=Resume, ...)`. You get back a validated `Resume` object. If the model outputs invalid JSON or missing required fields, Instructor retries with the validation error message.

**Why it matters**: Production LLM applications almost always need structured outputs: extraction pipelines (parse invoices, contracts, resumes), classification systems (classify customer intent), entity extraction (extract entities from documents), and API integrations (LLM fills a request body). Reliable structured outputs are the difference between a working production system and a fragile one.

**Key insight**: Native structured outputs (OpenAI JSON schema mode) are more reliable than prompt-based approaches because they constrain token sampling — the model cannot physically produce tokens that violate the schema. Use native structured outputs when available; use prompt-based parsing only for models that don't support it.

---

## 3. Core Principles

**Schema-first design**: Define the output structure as a Pydantic model before writing the prompt. The model definition IS the specification. Use `Field(description="...")` to guide the LLM toward correct values.

**Validation as retry**: If the LLM outputs a value that fails Pydantic validation, Instructor sends the validation error back to the LLM with a message asking it to fix the output. This turns one-shot extraction into a retry loop with feedback.

**Mode selection**: Instructor supports multiple extraction modes: `TOOLS` (OpenAI function calling), `JSON` (OpenAI JSON mode), `JSON_SCHEMA` (OpenAI structured outputs with strict schema), `ANTHROPIC_TOOLS` (Anthropic tool use), `GEMINI_JSON`. Choose based on model capabilities.

**Partial extraction**: Instructor supports `Partial[MyModel]` for streaming — as tokens arrive, the partially-populated model is available. This enables streaming progress bars or early validation of partial fields.

**Nested and recursive models**: Pydantic handles nested models naturally. An `Invoice` model can contain `List[LineItem]`, each with its own validators. Instructor handles arbitrary nesting.

---

## 4. Types / Architectures / Strategies

### Extraction Modes

| Mode | Mechanism | Reliability | Model Support |
|------|-----------|------------|---------------|
| `TOOLS` | OpenAI function calling | High | GPT-4, GPT-3.5, GPT-4o |
| `JSON_SCHEMA` | OpenAI structured outputs (strict) | Highest | GPT-4o, GPT-4o-mini |
| `JSON` | OpenAI JSON mode (prompt + parse) | Medium | GPT-4, GPT-3.5-turbo |
| `ANTHROPIC_TOOLS` | Anthropic tool use | High | Claude 3+ |
| `GEMINI_JSON` | Gemini response_mime_type | High | Gemini 1.5+ |
| `MD_JSON` | Markdown code block extraction | Low | Any model |

### Schema Patterns

1. **Simple extraction**: flat Pydantic model, string/int/enum fields
2. **Nested extraction**: models containing other models
3. **List extraction**: `List[Item]` for extracting multiple entities
4. **Union types**: `Union[InvoiceItem, ExpenseItem]` for polymorphic extraction
5. **Classification**: Pydantic `Literal` type for enum-like classification
6. **Validation with custom validators**: `@field_validator` for business rules

---

## 5. Architecture Diagrams

### Instructor Flow

```
Developer code:
  resume = client.chat.completions.create(
      response_model=Resume,
      messages=[{"role": "user", "content": "Extract from: " + raw_text}]
  )

Instructor internal flow:
  1. Generate tool/function definition from Resume Pydantic model
  2. Add to OpenAI API call as function
  3. Call OpenAI API
  4. Model returns function call JSON
  5. Pydantic validates the JSON against Resume model
  6. If validation fails:
       - Create validation error message
       - Append to conversation: "Validation failed: field X must be positive"
       - Retry API call (up to max_retries=3)
  7. If validation succeeds: return Resume instance
  8. If max_retries exceeded: raise InstructorRetryException
```

### OpenAI Structured Outputs (Native)

```
Client code:
  completion = openai.beta.chat.completions.parse(
      model="gpt-4o",
      response_format=Resume,  # Pydantic class directly
      messages=[...]
  )
  resume = completion.choices[0].message.parsed  # type: Resume

OpenAI server-side:
  1. Convert Pydantic model to JSON Schema
  2. Constrain token sampling to only produce tokens valid per the schema
  3. Model cannot deviate from schema — guaranteed valid JSON
  4. No retries needed for structural validity (only semantic validation may fail)
```

### Validation-Retry Loop

```
Attempt 1:
  Prompt: "Extract resume fields from: John Doe, 5 years exp..."
  Model output: {"name": "John Doe", "years": "five"}  ← wrong type for int field
  Pydantic: ValidationError: years_experience must be integer

Attempt 2 (retry):
  Add to messages: "Validation Error: years_experience must be int, got 'five'"
  Model output: {"name": "John Doe", "years_experience": 5}
  Pydantic: valid ✓
  Return: Resume(name="John Doe", years_experience=5)
```

---

## 6. How It Works — Detailed Mechanics

### Basic Instructor Usage

```python
import instructor
from openai import OpenAI
from pydantic import BaseModel, Field
from typing import List

client = instructor.from_openai(OpenAI())

class Person(BaseModel):
    name: str = Field(description="Full name of the person")
    age: int = Field(description="Age in years", gt=0, lt=150)
    email: str = Field(description="Email address")

# Instructor patches the OpenAI client; response_model replaces response_format
person = client.chat.completions.create(
    model="gpt-4o",
    response_model=Person,
    messages=[
        {"role": "user", "content": "Extract info: John Doe, 32 years old, john@example.com"}
    ]
)
print(person.name)   # "John Doe"
print(person.age)    # 32
print(person.email)  # "john@example.com"
```

### Complex Nested Extraction

```python
from pydantic import BaseModel, Field, field_validator
from typing import List, Optional
from enum import Enum

class InvoiceStatus(str, Enum):
    PAID = "paid"
    PENDING = "pending"
    OVERDUE = "overdue"

class LineItem(BaseModel):
    description: str = Field(description="Item description")
    quantity: int = Field(description="Number of units", gt=0)
    unit_price: float = Field(description="Price per unit in USD", gt=0)

    @property
    def total(self) -> float:
        return self.quantity * self.unit_price

class Invoice(BaseModel):
    invoice_number: str = Field(description="Invoice ID (e.g., INV-2024-001)")
    vendor_name: str = Field(description="Name of the vendor or supplier")
    total_amount: float = Field(description="Total invoice amount in USD", gt=0)
    status: InvoiceStatus = Field(description="Payment status")
    line_items: List[LineItem] = Field(description="Individual line items", min_length=1)
    due_date: Optional[str] = Field(None, description="Due date in YYYY-MM-DD format")

    @field_validator("invoice_number")
    @classmethod
    def validate_invoice_number(cls, v: str) -> str:
        if not v.startswith("INV-"):
            raise ValueError("Invoice number must start with INV-")
        return v

invoice = client.chat.completions.create(
    model="gpt-4o",
    response_model=Invoice,
    messages=[{
        "role": "user",
        "content": f"Extract invoice data:\n{invoice_text}"
    }],
    max_retries=3
)

print(f"Invoice {invoice.invoice_number}: ${invoice.total_amount}")
for item in invoice.line_items:
    print(f"  {item.description}: {item.quantity} × ${item.unit_price}")
```

### Classification with Literal Types

```python
from typing import Literal

class CustomerIntent(BaseModel):
    intent: Literal["billing", "technical_support", "product_inquiry", "cancellation", "other"]
    confidence: Literal["high", "medium", "low"]
    extracted_topic: str = Field(description="Specific topic mentioned by customer")
    suggested_queue: str = Field(description="Which support queue to route to")

    class Config:
        # Ensure the response strictly matches one of the Literal values
        use_enum_values = True

intent = client.chat.completions.create(
    model="gpt-4o-mini",   # cheaper model fine for classification
    response_model=CustomerIntent,
    messages=[{"role": "user", "content": f"Classify: {customer_message}"}]
)

print(intent.intent)       # "billing"
print(intent.confidence)   # "high"
print(intent.suggested_queue)  # "billing-team"
```

### Streaming with Partial Models

```python
from instructor import Partial

class AnalysisReport(BaseModel):
    summary: str = Field(description="Executive summary")
    findings: List[str] = Field(description="List of key findings")
    recommendations: List[str] = Field(description="Actionable recommendations")
    risk_level: Literal["low", "medium", "high"]

# Stream partial model as tokens arrive
for partial_report in client.chat.completions.create_partial(
    model="gpt-4o",
    response_model=AnalysisReport,
    messages=[{"role": "user", "content": f"Analyze: {document}"}],
    stream=True
):
    # partial_report is populated as fields become available
    if partial_report.summary:
        update_ui_summary(partial_report.summary)
    if partial_report.findings:
        update_ui_findings(partial_report.findings)
```

### Anthropic Support

```python
import instructor
from anthropic import Anthropic

client = instructor.from_anthropic(Anthropic())

class Summary(BaseModel):
    title: str
    key_points: List[str] = Field(min_length=3, max_length=5)
    sentiment: Literal["positive", "negative", "neutral"]

summary = client.messages.create(
    model="claude-3-5-sonnet-20241022",
    response_model=Summary,
    max_tokens=1024,
    messages=[{"role": "user", "content": f"Summarize: {article}"}]
)
```

### OpenAI Native Structured Outputs (no Instructor needed)

```python
from openai import OpenAI
from pydantic import BaseModel

client = OpenAI()

class CalendarEvent(BaseModel):
    name: str
    date: str  # YYYY-MM-DD
    participants: List[str]

# beta.chat.completions.parse handles Pydantic models natively
completion = client.beta.chat.completions.parse(
    model="gpt-4o-2024-08-06",  # structured outputs requires this version+
    messages=[{"role": "user", "content": "Alice and Bob have a meeting tomorrow at 9am"}],
    response_format=CalendarEvent,
)

event = completion.choices[0].message.parsed
# Guaranteed to be a valid CalendarEvent — no retries needed
print(event.name)  # "Meeting"
print(event.participants)  # ["Alice", "Bob"]
```

### Multi-Extract (List of Objects)

```python
from typing import List

class NamedEntity(BaseModel):
    name: str
    entity_type: Literal["person", "organization", "location", "date"]
    context: str = Field(description="The surrounding text where this entity appears")

class EntityExtraction(BaseModel):
    entities: List[NamedEntity]
    source_language: str = Field(description="Detected language of the source text")

extraction = client.chat.completions.create(
    model="gpt-4o",
    response_model=EntityExtraction,
    messages=[{"role": "user", "content": f"Extract all named entities from:\n{text}"}]
)

for entity in extraction.entities:
    print(f"{entity.entity_type}: {entity.name}")
```

### Async Support

```python
import instructor
from openai import AsyncOpenAI
import asyncio

async_client = instructor.from_openai(AsyncOpenAI())

async def extract_concurrently(texts: list[str]) -> list[Person]:
    tasks = [
        async_client.chat.completions.create(
            model="gpt-4o",
            response_model=Person,
            messages=[{"role": "user", "content": f"Extract: {text}"}]
        )
        for text in texts
    ]
    return await asyncio.gather(*tasks)

people = asyncio.run(extract_concurrently(text_list))
```

---

## 7. Real-World Examples

**Invoice processing at scale**: Finance companies extract line items, vendor names, totals, and due dates from PDF invoices. Instructor with `Invoice` model replaces fragile regex. With GPT-4o Vision + Instructor: extract from scanned PDFs with 94% accuracy vs 71% with regex.

**Resume screening**: ATS (Applicant Tracking Systems) use Instructor to extract structured candidate profiles: skills (list), years_experience (int), education (nested model). Pydantic validators enforce business rules ("at least one skill required").

**Legal contract extraction**: Extract parties, effective dates, termination clauses, and payment terms. Nested models handle the hierarchical nature of contracts. Custom validators enforce date formats and clause completeness.

**Medical record structuring**: Extract ICD codes, medications, dosages, diagnoses from clinical notes. HIPAA compliance: extraction runs on-premise with Ollama (Llama-3), not cloud APIs.

**E-commerce catalog enrichment**: Extract product attributes (dimensions, weight, materials, colors) from unstructured descriptions. Used by Shopify merchants to standardize catalog data.

---

## 8. Tradeoffs

| Approach | Reliability | Speed | Cost | Flexibility |
|----------|------------|-------|------|-------------|
| Instructor (TOOLS mode) | High | Medium (1-3 retries) | Low-Medium | High |
| OpenAI Structured Outputs (native) | Highest | Fast (no retries) | Same as TOOLS | Medium (JSON schema limits) |
| Prompt + manual JSON parse | Low (5-15% failure rate) | Fast | Low | Highest |
| Fine-tuned model for extraction | Very High | Fast | Training cost | Medium |
| LangChain PydanticOutputParser | Medium | Medium | Medium | High |

**Instructor vs LangChain output parsers:**

| Aspect | Instructor | LangChain PydanticOutputParser |
|--------|-----------|-------------------------------|
| Retry on validation | Automatic, with error feedback | Manual |
| Streaming support | `Partial[Model]` | Limited |
| Provider support | OpenAI, Anthropic, Gemini, Ollama | LangChain LLMs |
| Dependencies | Minimal (just instructor + pydantic) | Full LangChain |
| Complexity | Low | Medium |

---

## 9. When to Use / When NOT to Use

**Use structured outputs / Instructor when:**
- Extracting structured data from unstructured text (invoices, resumes, contracts)
- Building classification pipelines that downstream systems consume programmatically
- Any LLM output that feeds into code (not just displayed to users)
- Need type safety and validation at extraction time, not later
- Downstream errors from malformed LLM output are costly (financial data, medical data)

**Use OpenAI native structured outputs when:**
- Using GPT-4o (2024-08-06+) — guaranteed valid JSON, no retries
- Schema is simple enough to express in JSON Schema (no custom validators)
- Need maximum reliability with minimum latency

**Do NOT use structured outputs when:**
- Output is purely for human reading (creative writing, explanations) — adds unnecessary schema overhead
- Free-form reasoning is valuable (chain-of-thought) — schema constraints can reduce reasoning quality
- Model doesn't support function calling and the task is simple

---

## 10. Common Pitfalls

**Pitfall 1: Over-constraining the schema**
Adding too many `Field` constraints causes frequent validation failures and excessive retries.
```python
# BAD: over-constrained, causes 30% retry rate
class Product(BaseModel):
    price: float = Field(gt=0, lt=10000, description="Price in USD")
    sku: str = Field(pattern=r"^[A-Z]{3}-\d{6}$")  # exact regex

# BETTER: validate business rules separately from extraction
class Product(BaseModel):
    price: float = Field(gt=0, description="Price in USD")
    sku: str = Field(description="SKU code like 'ABC-123456'")

# Validate strict rules after extraction
if not re.match(r"^[A-Z]{3}-\d{6}$", product.sku):
    flag_for_human_review(product)
```

**Pitfall 2: Not handling InstructorRetryException**
After `max_retries` attempts, Instructor raises `InstructorRetryException`. Production code must handle this.
```python
import instructor
from instructor.exceptions import InstructorRetryException

try:
    result = client.chat.completions.create(
        model="gpt-4o",
        response_model=MyModel,
        messages=[...],
        max_retries=3
    )
except InstructorRetryException as e:
    # Log and fall back to manual processing
    logger.error(f"Extraction failed after retries: {e}")
    return fallback_extraction(raw_text)
```

**Pitfall 3: Expensive models for simple classification**
Using GPT-4o for binary classification (spam/not-spam) when GPT-4o-mini costs 20x less with identical accuracy on simple tasks. Benchmark your classification task on both models; use the cheaper one unless GPT-4o is demonstrably better.

**Pitfall 4: Missing Optional for truly optional fields**
```python
# BAD: if due_date is not in the document, model guesses or fails
class Invoice(BaseModel):
    due_date: str  # required!

# GOOD: truly optional fields use Optional
class Invoice(BaseModel):
    due_date: Optional[str] = Field(None, description="Due date if mentioned, else null")
```

**Pitfall 5: Ignoring field descriptions**
Field descriptions are part of the prompt. Vague descriptions → wrong extractions.
```python
# BAD: model doesn't know what format to use
class Event(BaseModel):
    date: str

# GOOD: explicit format guidance in description
class Event(BaseModel):
    date: str = Field(description="Event date in ISO 8601 format: YYYY-MM-DD")
```

**Pitfall 6: Schema changes breaking production**
Changing a Pydantic model field name or type is a breaking change for any persisted extraction results. Production incident: renamed `years_exp` to `years_experience` in a model, broke all downstream code that had been saving extracted data to a database column named `years_exp`. Treat Pydantic model changes like database schema migrations: backward-compatible additions only; use versioned models for major changes.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `instructor` | Pydantic + LLM extraction | Supports OpenAI, Anthropic, Gemini, Ollama |
| `pydantic` v2 | Schema definition + validation | Required for instructor; v2 for performance |
| `openai` SDK | Native structured outputs | `beta.chat.completions.parse` for GPT-4o+ |
| `anthropic` SDK | Anthropic tool use | `instructor.from_anthropic()` |
| `marvin` | Alternative to instructor | Higher-level abstraction |
| `outlines` | Constrained generation | Model-side constraint (for local models) |
| `lm-format-enforcer` | Grammar-constrained generation | For vLLM / Hugging Face |
| `jsonschema` | Schema validation without Pydantic | Lightweight alternative |

**Version notes:**
- Instructor 0.x: legacy API (`instructor.patch()`)
- Instructor 1.x (2024): `instructor.from_openai()`, multi-provider support, streaming Partial
- OpenAI structured outputs: requires `gpt-4o-2024-08-06` or newer; `openai>=1.40.0`

---

## 12. Interview Questions with Answers

**Q: What are structured outputs in LLM applications and why do they matter?**
Structured outputs means constraining LLM responses to conform to a defined schema (JSON, Pydantic model, TypedDict). They matter because downstream code — databases, APIs, other services — cannot handle free-form text. Without structured outputs, teams write brittle regex parsers that break when the model changes phrasing. With structured outputs, you get a validated Python object directly. Failure modes without them: missing required fields, wrong data types, extra fields that break parsers, inconsistent formats across requests.

**Q: What is Instructor and how does it work?**
Instructor is a Python library that wraps LLM clients (OpenAI, Anthropic, Gemini) to enforce structured Pydantic outputs. It works by: (1) generating a function/tool definition from the Pydantic model, (2) passing it to the LLM API alongside the user's messages, (3) asking the model to call the function with the extracted data, (4) validating the model's JSON output against the Pydantic model, (5) if validation fails, appending the error message to the conversation and retrying (up to `max_retries`). The caller receives a fully-validated Pydantic object or an exception after max retries.

**Q: What is the difference between Instructor's TOOLS mode and OpenAI's native structured outputs?**
`TOOLS` mode uses OpenAI function calling: the model generates a function call JSON which Instructor validates. If invalid, Instructor retries with the error. Native structured outputs (`response_format={"type": "json_schema", ...}` or `openai.beta.chat.completions.parse`) constrain the token sampling process server-side — the model physically cannot produce tokens that violate the JSON schema. Native structured outputs are more reliable (no validation failures possible for structural issues) and faster (no retries). Trade-off: native structured outputs have JSON Schema limitations (no `oneOf` with discriminators, limited validators); Instructor supports arbitrary Pydantic validators.

**Q: How do you handle optional fields in extraction schemas?**
Use `Optional[type] = None` for fields that may not be present in the source document. Example: `due_date: Optional[str] = Field(None, description="Due date if mentioned, else null")`. Without `Optional`, the model is forced to produce a value even if not present, leading to hallucination. Include `"else null"` in the field description to explicitly tell the model to output null when the field is absent. For lists: `items: List[Item] = Field(default_factory=list)` for potentially empty lists.

**Q: How do you use Pydantic validators with Instructor?**
Add `@field_validator` methods to enforce business rules. Instructor calls Pydantic validation on each extraction attempt. If a validator raises `ValueError`, Instructor catches it and retries with the error message. Example: validating that extracted dates are in the past, that prices are positive, that invoice numbers match a pattern. Important: prefer validation for business rules; don't use validators to enforce formatting that the LLM might reasonably interpret differently — this causes unnecessary retries.

**Q: What is the `Partial` model in Instructor and when do you use it?**
`Partial[Model]` enables streaming extraction — as the LLM generates tokens, Instructor progressively populates the model with fields as they become complete. Use it for: (1) long-running extractions where you want to show progress; (2) UIs that stream partial results (show summary while recommendations are still generating); (3) early validation — detect extraction errors before the full response completes. Implement with `client.chat.completions.create_partial(response_model=Partial[MyModel], stream=True)`, iterate over the partial instances.

**Q: How do you design Pydantic schemas for complex nested document extraction?**
Start from the document's logical structure, not the LLM's limitations. Design nested models that mirror the document hierarchy: `Invoice` contains `List[LineItem]`, `Vendor`, and `ShippingAddress`. Use `Field(description="...")` extensively — descriptions are injected into the prompt. For lists of items, add `min_length`/`max_length` constraints. For polymorphic extraction: use `Union[TypeA, TypeB]` with a discriminator field. Test schema complexity: complex schemas increase prompt length and can confuse smaller models; use GPT-4o for complex nested extraction.

**Q: How do you handle extraction failures gracefully in production?**
Three-layer approach: (1) `max_retries=3` in Instructor — handles transient model failures and most validation errors; (2) `try/except InstructorRetryException` — catch extraction failures after all retries; route to human review queue or fallback logic; (3) Validation post-extraction — even with successful Instructor extraction, run domain-specific business validation (is the extracted total consistent with line items?). For high-stakes data (financial, medical): require human review of all extractions above a confidence threshold. Log all failures with the raw input for analysis.

**Q: When should you use a cheaper model vs GPT-4o for extraction?**
Use GPT-4o-mini when: binary or multi-class classification, simple flat schemas with few fields, source documents are well-structured (form data, templated documents). Use GPT-4o when: complex nested schemas (3+ levels), unstructured source documents (handwritten notes, informal emails), cross-field reasoning required (derive total from line items), or when GPT-4o-mini error rate on validation is >5%. Benchmark both: extract 100 documents with each model; compare accuracy (human-verified) and cost. GPT-4o costs 10-20x more than GPT-4o-mini — only pay that premium for tasks where it demonstrably matters.

**Q: How do Instructor and OpenAI structured outputs handle missing fields differently?**
In Instructor (TOOLS mode): if the model doesn't include a required field, Pydantic raises `ValidationError: field required`. Instructor retries with the error message. Usually resolved in 1-2 retries. In OpenAI native structured outputs: the schema is enforced at token generation — the model is constrained to include all required fields. Missing required fields are impossible (the model can only sample tokens that satisfy the schema). With `Optional` fields: both approaches handle null correctly. For production with high-volume extraction: native structured outputs eliminate retry latency for structural issues.

**Q: How do you extract lists of items from documents?**
Use `List[ItemModel]` in your schema. Example: `line_items: List[LineItem]`. The LLM will generate an array. Add constraints: `min_length=1` to ensure at least one item is extracted, `max_length=50` to prevent runaway generation. For documents where the number of items varies greatly (0 to 100+): use `Optional[List[ItemModel]] = None` and handle null in downstream code. Performance consideration: extracting a 50-item list in one call is faster and cheaper than 50 individual calls, but error recovery is coarser (if one item fails validation, the whole extraction fails).

**Q: What is constrained generation (outlines, lm-format-enforcer) and how does it differ from Instructor?**
Constrained generation works at the token sampling level of a local model: only tokens that could be part of a valid next token in the schema are sampled. This is mathematically guaranteed to produce valid output — no retries needed. `outlines` and `lm-format-enforcer` implement this for Hugging Face/vLLM models. Instructor is different: it works with API models (OpenAI, Anthropic) where you can't access token sampling; it relies on the model's instruction following + retry loop. For local model deployments: constrained generation is superior (guaranteed valid, no retries). For API models: Instructor or native structured outputs.

**Q: How do you version extraction schemas in production?**
Three-level versioning: (1) Schema versioning — use versioned Pydantic models (`InvoiceV2`, `InvoiceV3`); maintain backward compatibility by keeping old models; (2) Prompt versioning — store prompt templates in a registry (Langfuse/LangSmith); version alongside schema versions (see [Prompt Management & PromptOps](../prompt_management_and_promptops/README.md)); (3) Output versioning — store the schema version with extracted data so you can re-run extraction with a newer schema without losing historical data. Migration: when adding required fields to an existing schema, add as `Optional` first; backfill historical records; then make required after backfill. Breaking changes (renaming, removing fields) require explicit migration scripts.

**Q: How do you extract structured data from PDFs and images (multimodal)?**
Use GPT-4o Vision or Claude 3.5 Sonnet (both multimodal). Pass the image as a base64-encoded data URL in the messages. Instructor works identically — the model processes the image and extracts according to the schema. Pattern: encode PDF page as PNG → send to multimodal LLM with extraction schema → get structured output. LlamaParse is a managed alternative for complex PDFs with tables. Performance: vision extraction is slower (2-5s per page) and more expensive than text extraction; cache extracted results and only re-extract when the document changes.

**Q: How do you handle schema evolution when your structured output format changes in production?**
Schema evolution requires backward-compatible changes to avoid breaking existing consumers. Strategies: (1) add new optional fields with defaults — existing consumers ignore them; (2) never remove or rename required fields in the same version; (3) version your schemas (v1, v2) and support both during migration; (4) use Pydantic's model validators to transform v1 responses into v2 format. For Instructor specifically: maintain separate Pydantic models per version and route based on client version header. Test schema changes against a replay dataset of 100+ real queries to verify the model can still generate valid outputs with the new schema — schema changes that the model struggles with cause silent quality regression.

---

## 13. Best Practices

1. **Use `Field(description="...")` for every field** — descriptions are part of the prompt; vague descriptions cause extraction errors.
2. **Use `Optional` for fields not always present** — prevents hallucination of missing fields.
3. **Set `max_retries=3`** — handles most transient failures; cap to prevent infinite retry loops.
4. **Catch `InstructorRetryException`** — have a fallback (human review queue, partial extraction) for persistent failures.
5. **Use GPT-4o-mini for simple classification** — 20x cheaper, comparable accuracy for simple schemas.
6. **Use native structured outputs (GPT-4o 2024-08-06+) for high-volume production** — no retry latency for structural issues.
7. **Test schema against diverse documents** — extract from 20-50 real documents before deploying; find edge cases early.
8. **Version schemas alongside prompts** — never break existing extraction pipelines silently.
9. **Log raw inputs for failed extractions** — enables debugging and improving the schema.
10. **Validate post-extraction with business rules** — Pydantic validates structure; you must validate semantics (is total consistent with line items?).

---

## 14. Case Study: Invoice Processing Pipeline

**Scenario**: A mid-sized company receives 500 invoices/month from 200+ vendors. Manual data entry takes 3 hours/day. Build an automated extraction pipeline with >95% accuracy.

### Schema Design

```python
from pydantic import BaseModel, Field, field_validator
from typing import List, Optional
from datetime import date

class LineItem(BaseModel):
    description: str = Field(description="Item or service description")
    quantity: float = Field(description="Number of units", gt=0)
    unit_price: float = Field(description="Price per unit in USD", gt=0)
    amount: float = Field(description="Total amount for this line (quantity × unit_price)")

    @field_validator("amount")
    @classmethod
    def validate_amount(cls, v: float, info) -> float:
        # Soft validation: flag if amount doesn't match, but don't fail
        # (OCR/parsing errors sometimes introduce small rounding differences)
        expected = info.data.get("quantity", 0) * info.data.get("unit_price", 0)
        if abs(v - expected) > 0.05:
            # Log discrepancy but don't fail — human review will catch
            pass
        return v

class VendorInfo(BaseModel):
    name: str = Field(description="Legal name of the vendor")
    address: Optional[str] = Field(None, description="Vendor address if present")
    tax_id: Optional[str] = Field(None, description="Vendor tax ID or EIN if present")

class Invoice(BaseModel):
    invoice_number: str = Field(description="Invoice identifier (e.g., INV-2024-001)")
    invoice_date: str = Field(description="Invoice date in YYYY-MM-DD format")
    due_date: Optional[str] = Field(None, description="Payment due date in YYYY-MM-DD format, null if not specified")
    vendor: VendorInfo
    line_items: List[LineItem] = Field(min_length=1)
    subtotal: float = Field(description="Sum of all line items before tax, in USD")
    tax_amount: float = Field(default=0.0, description="Total tax amount in USD")
    total_amount: float = Field(description="Final total amount due in USD", gt=0)
    currency: str = Field(default="USD", description="Invoice currency (ISO 4217 code)")
    extraction_confidence: str = Field(
        description="Confidence in extraction quality: high (clear invoice), medium (some uncertainty), low (poor quality scan)"
    )
```

### Extraction Pipeline

```python
import instructor
from openai import AsyncOpenAI
from pathlib import Path
import asyncio

async_client = instructor.from_openai(AsyncOpenAI())

async def extract_invoice(pdf_path: Path) -> Invoice | None:
    # Convert PDF to image (first page)
    image_data = pdf_to_base64_image(pdf_path)

    try:
        invoice = await async_client.chat.completions.create(
            model="gpt-4o",
            response_model=Invoice,
            max_retries=3,
            messages=[{
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {"url": f"data:image/png;base64,{image_data}"}
                    },
                    {
                        "type": "text",
                        "text": "Extract all invoice data from this image."
                    }
                ]
            }]
        )

        # Post-extraction business validation
        calculated_total = invoice.subtotal + invoice.tax_amount
        if abs(calculated_total - invoice.total_amount) > 1.0:
            invoice.extraction_confidence = "low"
            flag_for_review(invoice, reason="total_mismatch")

        return invoice

    except InstructorRetryException as e:
        logger.error(f"Failed to extract {pdf_path.name}: {e}")
        queue_for_manual_entry(pdf_path)
        return None

# Process all invoices concurrently (rate limit: 50 concurrent)
async def process_batch(pdf_paths: list[Path]) -> list[Invoice]:
    semaphore = asyncio.Semaphore(10)  # max 10 concurrent extractions
    async def bounded_extract(path):
        async with semaphore:
            return await extract_invoice(path)
    return await asyncio.gather(*[bounded_extract(p) for p in pdf_paths])
```

### Results

| Metric | Before (manual) | After (extraction pipeline) |
|--------|----------------|----------------------------|
| Processing time | 3 hours/day | 8 minutes/day (500 invoices) |
| Accuracy | 99.8% (human) | 96.2% (all fields correct) |
| Confidence "high" | N/A | 81% (auto-approved) |
| Confidence "medium/low" | N/A | 19% (human review) |
| Human review time | 3 hours/day | 25 minutes/day |
| Cost per invoice | ~$1.50 (labor) | $0.038 (GPT-4o + infrastructure) |
