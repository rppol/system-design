# Case Study: Design an AI Data Analyst (Code Interpreter / Julius AI Style)

## Intuition

> **Design intuition**: An AI Data Analyst is a natural language interface to data -- it combines text-to-code generation (SQL, pandas) with a secure execution sandbox and a visualization renderer. The core loop is: user asks question in English, system generates executable code, runs it in a sandboxed environment, captures the output (table, chart, statistic), and returns both the answer and the reasoning. Unlike chatbots that generate text, this system generates and executes code -- making correctness verifiable but also making sandboxing critical.

**Key insight for this design**: The hardest problem is not code generation -- it is schema understanding. When a user asks "show me sales trends," the system must resolve ambiguity (revenue vs. units?), infer the correct time column, handle missing values, and pick the right aggregation. The schema inference and metadata enrichment layer that runs at upload time determines the quality of every downstream analysis. Investing 5-10 seconds at ingestion to build a rich data profile saves hundreds of failed queries later.

---

## 1. Requirements Clarification

### Functional Requirements
- Upload datasets: CSV (up to 500MB), Parquet (up to 2GB), Excel (.xlsx, .xls), SQL database connections (PostgreSQL, MySQL, BigQuery)
- Auto-EDA on upload: descriptive statistics, distribution analysis, correlation matrix, anomaly flags, missing value summary
- Natural language questions converted to executable SQL or Python (pandas/polars)
- Secure per-user code execution sandbox (Jupyter-like kernel, no network, resource capped)
- Visualization generation: auto-select chart type, render matplotlib/plotly, return image + interactive HTML
- Hypothesis generation: proactively suggest interesting patterns, correlations, follow-up analyses
- Report synthesis: compile findings into structured report with narrative, charts, and statistical evidence
- Conversation memory: multi-turn analysis sessions referencing prior results
- Data transformations: create derived columns, merge datasets, pivot tables via natural language

### Non-Functional Requirements
- **Upload processing**: schema inference + auto-EDA complete within 30 seconds for 500MB CSV
- **Query latency**: first result within 8 seconds for typical analytical query
- **Sandbox security**: per-user isolation, no cross-tenant data leakage, no network egress
- **Scale**: 50K concurrent users; 200K analysis queries/hour at peak
- **Data retention**: user datasets stored up to 30 days; reports stored indefinitely
- **Compliance**: SOC 2, GDPR (data deletion on request), optional HIPAA for healthcare datasets

### Out of Scope
- Real-time streaming data analysis (batch/uploaded data only)
- ML model training and deployment (analysis and visualization only)
- Collaborative editing (single-user sessions; sharing via exported reports)

---

## 2. Scale Estimation

### Traffic Estimates
```
Registered users: 2M
Daily active users: 200K
Concurrent at peak (10am-3pm): 50K

Queries per active user per day: 15
Daily queries: 200K x 15 = 3M queries/day
Peak QPS: 3M / (5 hours x 3600) = 167 req/sec analytical queries
Burst peak (Monday morning): 3x = 500 req/sec

File uploads per day: 100K
Average file size: 50MB (median), 500MB (P99)
Daily upload volume: 100K x 50MB = 5TB ingested/day

Sandbox executions per query: 1.3 (some queries require retry after error)
Daily sandbox executions: 3M x 1.3 = 3.9M executions/day
Peak sandbox demand: 650 concurrent sandboxes
```

### Storage Estimates
```
User datasets:
  Active datasets: 500K files x 50MB avg = 25TB
  30-day retention: ~150TB total (with churn)
  Storage: S3 (compressed Parquet) + Redis metadata cache

Schema metadata:
  Per dataset: ~5KB (column names, types, stats, sample values)
  Total: 500K x 5KB = 2.5GB → PostgreSQL

Generated reports:
  Per report: 500KB (HTML + embedded charts as base64)
  Daily: 50K reports x 500KB = 25GB/day
  Indefinite retention: S3 with lifecycle to Glacier after 90 days

Conversation history:
  Per session: 20KB (10 turns x 2KB per turn)
  Daily: 200K sessions x 20KB = 4GB/day
  Retention: 90 days → 360GB → PostgreSQL + S3 archive

Sandbox artifacts (intermediate DataFrames, chart images):
  Per query: 2MB avg (cached DataFrame + rendered chart)
  Daily: 3M x 2MB = 6TB → ephemeral (deleted after session, 24hr max)
  Storage: local SSD on sandbox nodes
```

### Compute Estimates
```
LLM inference (text-to-code generation):
  Input tokens per query: 2,000 (schema + question + conversation context)
  Output tokens per query: 300 (generated code)
  Daily tokens: 3M x 2,300 = 6.9B tokens/day
  Cost at $3/M input + $15/M output tokens (GPT-4o):
    Input: 6B x $3/M = $18,000/day
    Output: 0.9B x $15/M = $13,500/day
    Total LLM cost: ~$31,500/day = ~$11.5M/year

Sandbox compute:
  Per execution: 2 vCPU, 4GB RAM, avg 5 seconds
  Peak concurrent: 650 sandboxes
  Compute: 650 x 2 vCPU = 1,300 vCPUs reserved
  Cost: ~$0.05/vCPU-hour x 1,300 x 24 = $1,560/day

GPU for large dataset processing (optional):
  RAPIDS/cuDF for datasets > 100MB
  4x A10G GPUs for peak: $4/hr x 4 x 24 = $384/day
```

---

## 3. High-Level Architecture

```
User (Browser / API Client)
    |
    v
[CDN / Load Balancer]
  - Static assets (JS app, report viewer)
  - WebSocket upgrade for streaming results
    |
    v
[API Gateway]
  - Auth (JWT + API key)
  - Rate limiting (Redis)
  - Request routing
    |
    +--→ [Upload Service]
    |      - Multipart upload → S3
    |      - Schema inference engine
    |      - Auto-EDA pipeline
    |      - Metadata → PostgreSQL
    |
    +--→ [Analysis Service]
    |      - Conversation manager
    |      - Ambiguity resolver
    |      - Code generation (LLM)
    |      - Sandbox orchestrator
    |      - Visualization renderer
    |      - Hypothesis engine
    |
    +--→ [Report Service]
           - Finding aggregation
           - Narrative generation (LLM)
           - PDF/HTML export
           - Report storage → S3

Supporting Infrastructure:
  ┌─────────────────────────────────────────────────┐
  │                                                  │
  │  [Sandbox Pool]           [Object Store (S3)]    │
  │   - Warm container pool    - User datasets       │
  │   - Per-user isolation     - Generated reports   │
  │   - Resource limits        - Chart images        │
  │   - No network egress                            │
  │                                                  │
  │  [PostgreSQL]             [Redis]                │
  │   - Dataset metadata       - Session state       │
  │   - Schema profiles        - Rate limits         │
  │   - User accounts          - Schema cache        │
  │   - Conversation history   - Sandbox routing     │
  │                                                  │
  │  [Kafka]                  [Prometheus + Grafana]  │
  │   - Async EDA jobs         - Query latency       │
  │   - Report generation      - Sandbox utilization  │
  │   - Usage metering         - LLM cost tracking   │
  │                                                  │
  └─────────────────────────────────────────────────┘
```

---

## 4. Component Deep Dives

### 4.1 File Upload and Schema Inference

```
Upload flow:
  1. Client uploads file via multipart POST (max 500MB CSV, 2GB Parquet)
  2. Upload Service streams to S3 (no full buffering in memory)
  3. On upload complete, trigger schema inference pipeline

Schema inference pipeline (runs synchronously for < 100MB, async for larger):

  Step 1: Format detection and parsing (< 2 seconds)
    - CSV: detect delimiter (comma, tab, pipe, semicolon)
      Heuristic: try each delimiter on first 100 lines, pick one with
      most consistent column count
    - CSV encoding: detect via chardet (UTF-8, Latin-1, Windows-1252)
    - Parquet: read schema from footer (instant — schema is in metadata)
    - Excel: read first sheet by default; list all sheets for user selection
    - SQL: execute INFORMATION_SCHEMA query for table/column metadata

  Step 2: Data type inference (< 3 seconds for 500MB)
    Read first 10,000 rows + random sample of 5,000 rows from remainder

    Type detection priority:
      1. Null/empty → track separately (missing_count, missing_pct)
      2. Boolean: {"true","false","yes","no","1","0","T","F"}
      3. Integer: regex ^-?\d+$ and value fits int64
      4. Float: regex ^-?\d+\.?\d*$ or scientific notation
      5. Date/DateTime: try 15 common formats (ISO 8601, US, EU, Unix epoch)
         Formats tried: yyyy-MM-dd, MM/dd/yyyy, dd-MMM-yyyy, epoch_seconds,
                        epoch_milliseconds, yyyy-MM-dd HH:mm:ss, etc.
      6. Categorical: string column with < 50 unique values in sample
      7. Free text: string column with high cardinality (> 1000 unique)
      8. Email/URL/Phone: regex-based semantic type detection

    Output per column:
      {
        name: "order_date",
        inferred_type: "datetime",
        nullable: true,
        missing_count: 47,
        missing_pct: 0.3,
        sample_values: ["2024-01-15", "2024-02-20", "2024-03-01"],
        detected_format: "yyyy-MM-dd",
        semantic_type: "date"        // enrichment for LLM context
      }

  Step 3: Semantic column labeling (< 1 second, LLM-assisted)
    Send column names + sample values to LLM:
      "Given columns: [order_id, cust_name, order_date, amt, qty, region]
       and sample values, classify each as:
       dimension, measure, temporal, identifier, or text"

    Result:
      order_id → identifier (primary key candidate)
      cust_name → dimension
      order_date → temporal (likely time axis)
      amt → measure (likely monetary — contains decimals, label suggests amount)
      qty → measure (likely count)
      region → dimension (low cardinality: 5 unique values)

  Step 4: Relationship detection (for multi-table uploads or SQL connections)
    - Foreign key inference: column names ending in _id that match another
      table's primary key
    - Join suggestion: "orders.customer_id can join customers.id"
    - Stored as join_hints in metadata

Total schema inference time:
  10MB CSV: < 3 seconds
  100MB CSV: < 8 seconds
  500MB CSV: < 20 seconds (async; user sees progress bar)
  Parquet (any size): < 2 seconds (schema in metadata, stats in row groups)
```

### 4.2 Auto-EDA (Exploratory Data Analysis)

```
Triggered automatically after schema inference. Runs as async Kafka job.
Results available within 30 seconds for 500MB dataset.

Auto-EDA pipeline:

  Phase 1: Descriptive statistics (per column)
    Numeric columns:
      count, mean, std, min, 25%, 50%, 75%, max, skewness, kurtosis
      Outlier count: values beyond 1.5x IQR
      Zero count, negative count

    Categorical columns:
      unique_count, top_5_values_with_counts, entropy
      Dominance: if top value > 80% of rows → flag as "low variance"

    Temporal columns:
      min_date, max_date, date_range, granularity (daily, monthly, yearly)
      Gap detection: missing dates in sequence

    Missing value analysis:
      Per column: count, percentage
      Pattern: MCAR (random) vs MAR (correlated with another column)
      Correlation of missingness: if col_A missing implies col_B missing

  Phase 2: Distribution analysis
    Numeric: fit to normal, log-normal, exponential, uniform
      KS test p-value for each fit
      If skewness > 2: flag as "heavily right-skewed, consider log transform"

    Categorical: chi-square test for uniformity
      If top category > 50%: flag imbalance

  Phase 3: Correlation detection
    Numeric-numeric: Pearson correlation matrix
      Flag pairs with |r| > 0.7 as "strong correlation"
      Flag pairs with |r| > 0.9 as "potential redundancy"

    Categorical-numeric: ANOVA F-test
      "region has significant effect on revenue (F=23.4, p<0.001)"

    Categorical-categorical: Cramers V
      Flag pairs with V > 0.5 as "associated"

  Phase 4: Anomaly detection
    Statistical: Z-score > 3 or IQR method
    Temporal: sudden spikes/drops (> 3 std from rolling mean)
    Categorical: new categories appearing after a date

  Phase 5: Insight generation (LLM-assisted)
    Feed statistical summary to LLM:
      "Given this dataset profile: 50K rows, 12 columns, sales data from
       2020-2024. Key stats: revenue skewed right (skew=3.2), strong
       correlation between ad_spend and revenue (r=0.82), 15% missing
       values in customer_segment column, anomaly spike in March 2023.
       Generate 5 key insights and 3 suggested follow-up analyses."

    Example output:
      Insights:
        1. Revenue is heavily right-skewed — a small number of orders drive
           most revenue. Top 10% of orders account for 62% of total revenue.
        2. Ad spend and revenue are strongly correlated (r=0.82), suggesting
           ad spend is a significant revenue driver.
        3. March 2023 shows a 340% spike in orders — investigate if this was
           a promotional event or data anomaly.
        4. Customer segment is missing for 15% of rows, concentrated in
           Q1 2020 — likely a tracking issue before CRM integration.
        5. West region generates 42% of revenue despite having only 28% of
           customers — higher average order value.

      Suggested analyses:
        1. "What is the ROI per dollar of ad spend by region?"
        2. "Show monthly revenue trend with and without the March 2023 spike"
        3. "Segment customers by purchase frequency and average order value"

Auto-EDA storage:
  Results stored as JSON in PostgreSQL (per dataset):
    eda_results: {descriptive_stats, correlations, anomalies, insights}
    Size: ~50KB per dataset
    Used as context for all subsequent natural language queries
```

### 4.3 Natural Language to SQL/Python

```
The core intelligence layer — converts user questions to executable code.

Two code generation paths:

Path A: Text-to-SQL (for SQL database connections)
  User: "What were the top 5 customers by revenue last quarter?"

  Prompt construction:
    [System]
    You are a data analyst. Generate SQL to answer the user's question.
    Return ONLY executable SQL — no explanations, no markdown fences.
    Use standard SQL compatible with {dialect: PostgreSQL}.
    Always include appropriate WHERE clauses for date filters.
    Use aliases for readability. Limit results to 1000 rows max.

    [Schema context]
    Table: orders (
      order_id INT PRIMARY KEY,
      customer_id INT,
      customer_name VARCHAR(100),
      order_date DATE,           -- range: 2020-01-01 to 2024-12-31
      revenue DECIMAL(10,2),     -- mean: 150.00, p99: 2500.00
      quantity INT,
      region VARCHAR(20)         -- values: North, South, East, West, Central
    )
    Table: customers (
      id INT PRIMARY KEY,
      name VARCHAR(100),
      segment VARCHAR(50),       -- values: Enterprise, SMB, Consumer
      signup_date DATE
    )
    Join hint: orders.customer_id = customers.id

    [EDA context]
    Key stats: 500K orders, date range 2020-01 to 2024-12,
    revenue is right-skewed (median $85, mean $150).
    "Last quarter" relative to latest data = Q4 2024 (Oct-Dec 2024).

    [Conversation history]
    (previous questions and results for context continuity)

    [User question]
    "What were the top 5 customers by revenue last quarter?"

  Generated SQL:
    SELECT
      c.name AS customer_name,
      c.segment,
      SUM(o.revenue) AS total_revenue,
      COUNT(o.order_id) AS order_count
    FROM orders o
    JOIN customers c ON o.customer_id = c.id
    WHERE o.order_date >= '2024-10-01'
      AND o.order_date < '2025-01-01'
    GROUP BY c.name, c.segment
    ORDER BY total_revenue DESC
    LIMIT 5;

Path B: Text-to-Python (for uploaded files — CSV, Parquet, Excel)
  User: "Show me the monthly revenue trend with a 3-month moving average"

  Prompt construction:
    [System]
    You are a data analyst. Generate Python code using pandas to answer
    the user's question. The DataFrame is pre-loaded as `df`.
    Available libraries: pandas, numpy, matplotlib, plotly, scipy, sklearn.
    For visualizations, use plotly for interactive charts.
    Save charts to '/output/chart.html' (interactive) and '/output/chart.png'.
    Print the final result DataFrame or summary statistics to stdout.
    Handle missing values gracefully — do not let NaN crash the analysis.

    [DataFrame schema]
    df.shape = (500000, 12)
    Columns:
      order_date: datetime64 (2020-01-01 to 2024-12-31, daily granularity)
      revenue: float64 (min=1.50, mean=150.00, max=12500.00, 0.2% null)
      quantity: int64 (min=1, mean=3, max=100)
      region: object (5 unique: North, South, East, West, Central)
      customer_segment: object (3 unique: Enterprise, SMB, Consumer, 15% null)
      ...

    [EDA context]
    Revenue is right-skewed. Strong seasonality in Q4.

    [User question]
    "Show me the monthly revenue trend with a 3-month moving average"

  Generated Python:
    import pandas as pd
    import plotly.graph_objects as go

    monthly = df.groupby(df['order_date'].dt.to_period('M'))['revenue'].sum()
    monthly.index = monthly.index.to_timestamp()
    monthly_ma = monthly.rolling(window=3).mean()

    fig = go.Figure()
    fig.add_trace(go.Scatter(x=monthly.index, y=monthly.values,
                             mode='lines', name='Monthly Revenue'))
    fig.add_trace(go.Scatter(x=monthly_ma.index, y=monthly_ma.values,
                             mode='lines', name='3-Month MA',
                             line=dict(dash='dash')))
    fig.update_layout(title='Monthly Revenue Trend',
                      xaxis_title='Month', yaxis_title='Revenue ($)',
                      template='plotly_white')
    fig.write_html('/output/chart.html')
    fig.write_image('/output/chart.png')
    print(monthly.tail(12).to_string())

Ambiguity resolution:
  Problem: "Show me sales" — does "sales" mean revenue, quantity, or order count?

  Strategy 1: Schema-aware inference
    If column named "revenue" or "sales_amount" exists → use that
    If column named "quantity" or "units_sold" exists → clarify
    Priority: monetary columns > count columns > categorical

  Strategy 2: Explicit clarification (when ambiguity is high)
    System response: "I found multiple columns that could represent 'sales':
      - revenue (monetary, $): total dollar amount per order
      - quantity (integer): number of units per order
      Which one would you like to analyze? Or I can show both."

  Strategy 3: Default + mention
    Generate code using the most likely interpretation, but state the assumption:
    "I'm interpreting 'sales' as total revenue. Here's the trend..."
    This avoids blocking the user with a clarification question for every query.

  Configuration: ambiguity_threshold = 0.7
    If LLM confidence in interpretation > 0.7 → proceed with assumption
    If < 0.7 → ask clarification question
```

### 4.4 Code Execution Sandbox

```
Every generated code snippet runs in an isolated sandbox — never on the
application server.

Sandbox architecture:

  Technology: gVisor-sandboxed containers (not just Docker — gVisor adds
  syscall-level isolation, preventing container escape attacks)

  Per-user sandbox:
    ┌─────────────────────────────────────────────────┐
    │  Container (gVisor runtime)                      │
    │                                                  │
    │  Python 3.11 + pre-installed libraries:          │
    │    pandas 2.2, numpy 1.26, matplotlib 3.8,      │
    │    plotly 5.18, scipy 1.12, scikit-learn 1.4,   │
    │    polars 0.20, openpyxl 3.1                    │
    │                                                  │
    │  Resources:                                      │
    │    CPU: 2 cores (burstable to 4)                │
    │    RAM: 4GB (hard limit — OOMKilled if exceeded) │
    │    Disk: 10GB ephemeral (for intermediate files) │
    │    Network: NONE (no egress, no ingress)         │
    │    Time limit: 60 seconds per execution          │
    │    Process limit: 50 (prevent fork bombs)        │
    │                                                  │
    │  Mounted volumes (read-only):                    │
    │    /data/  → user's uploaded dataset (from S3)   │
    │  Mounted volumes (read-write):                   │
    │    /output/ → results (charts, CSVs, reports)    │
    │                                                  │
    └─────────────────────────────────────────────────┘

  Lifecycle:
    Session start → allocate sandbox from warm pool (< 500ms)
    First query → load dataset into sandbox memory
      (pandas read_csv or read_parquet from /data/)
    Subsequent queries → DataFrame persists in memory (IPython kernel)
    Session idle > 15 minutes → snapshot state, release container
    Session idle > 60 minutes → destroy container, evict dataset
    Session resume → restore from snapshot (< 3 seconds)

  Warm pool management:
    Pool size: 800 warm containers (covers peak 650 + 25% buffer)
    Pre-loaded with Python environment + common libraries
    Container creation from warm pool: < 500ms
    Cold start (no warm container): 8-12 seconds
    Auto-scale: if pool drops below 200, trigger creation batch of 100

  Execution flow per query:
    1. Analysis Service sends generated code to Sandbox Orchestrator
    2. Orchestrator routes to user's assigned sandbox (sticky session)
    3. Code executed via IPython kernel (maintains state between queries)
    4. Capture: stdout, stderr, return value, files written to /output/
    5. If execution fails (syntax error, runtime error):
       a. Capture error traceback
       b. Send error + original code back to LLM for self-repair
       c. LLM generates corrected code
       d. Re-execute (max 2 retries)
    6. Return results to Analysis Service

  Error self-repair example:
    Generated code: df.groupby('region')['revenue'].sum().plot(kind='bar')
    Error: "KeyError: 'region' — column not found"
    LLM sees error + actual column names: ['Region', 'Revenue', ...]
    Corrected: df.groupby('Region')['Revenue'].sum().plot(kind='bar')
    Success rate of self-repair: ~70% on first retry

  Security measures:
    - No os.system(), subprocess, or socket allowed (blocked via seccomp)
    - No file access outside /data/ and /output/
    - No pip install at runtime (all libraries pre-installed)
    - Memory hard limit prevents loading 500MB CSV into 4GB RAM:
      Solution: for files > 200MB, use chunked reading or Polars (lazy eval)
    - CPU time limit: 60 seconds (prevents infinite loops)
    - Output size limit: 50MB per execution (prevents disk fill attacks)
```

### 4.5 Visualization Generation

```
The system auto-selects the best chart type based on the data and question.

Chart type selection logic:

  Input: question intent + data characteristics + result shape

  Rules:
    Time series (1 temporal + 1 numeric):
      → Line chart
      With categories: → Multi-line chart (one line per category)
      If > 50 time points and noisy: → Line + smoothing (rolling average)

    Comparison (1 categorical + 1 numeric):
      Categories <= 7: → Bar chart (vertical)
      Categories 8-15: → Horizontal bar chart
      Categories > 15: → Top-10 bar chart + "others" bucket

    Distribution (1 numeric, question about spread):
      → Histogram (30 bins default)
      If comparing groups: → Box plot or violin plot

    Composition (parts of whole):
      Categories <= 6: → Pie chart / donut chart
      Over time: → Stacked area chart

    Relationship (2 numeric):
      → Scatter plot
      If > 10,000 points: → Hexbin plot or sampled scatter (2,000 points)
      With category coloring: → Colored scatter

    Geographic (lat/lon or region names):
      → Choropleth map (plotly)

    Correlation overview (many numerics):
      → Heatmap (correlation matrix)

  Rendering pipeline:
    1. LLM generates plotly code (preferred for interactivity)
    2. Sandbox executes code
    3. Output: chart.html (interactive, 200-500KB) + chart.png (static, 50-100KB)
    4. API returns both formats:
       - Interactive HTML embedded in response (for web UI)
       - PNG as fallback (for report generation, email)

  Large dataset handling:
    Problem: plotting 500K points makes charts unreadable and slow to render

    Strategies:
      Aggregation: group by appropriate granularity before plotting
        500K daily records → aggregate to monthly (60 points)
      Sampling: random sample of 2,000-5,000 points for scatter plots
        Stratified sampling to preserve distribution shape
      Binning: hexbin for dense scatter, histogram for distributions

    Rule: never send > 10,000 data points to plotly (browser will lag)

  Styling:
    Default theme: plotly_white (clean, professional)
    Color palette: colorblind-safe (Tableau 10 or IBM Design)
    Font: 14px axis labels, 16px title
    Always include: title, axis labels, legend (if multiple series)
    Auto-format numbers: $1.2M (not $1,234,567), 15.3K (not 15,324)
```

### 4.6 Hypothesis Generation Engine

```
Proactively suggests interesting patterns without user prompting.
Runs after auto-EDA and after each user query.

Hypothesis sources:

  Source 1: Statistical anomalies from auto-EDA
    Input: anomaly list from Phase 4 of auto-EDA
    Example: "March 2023 has 340% more orders than the rolling average"
    Hypothesis: "A promotional event or external factor drove a spike in
    March 2023. Would you like to compare this period by region or channel?"

  Source 2: Correlation-driven hypotheses
    Input: correlation matrix + column semantic labels
    If corr(ad_spend, revenue) = 0.82:
      Hypothesis: "Ad spend and revenue are strongly correlated. Want to
      see the ROI per dollar of ad spend, broken down by region?"
    If corr(temperature, ice_cream_sales) = 0.91:
      Hypothesis: "Temperature strongly predicts ice cream sales. Want to
      see a regression model with predicted vs actual?"

  Source 3: Segment comparison
    Automatically compare key metrics across categorical dimensions:
    "Enterprise customers have 3.2x higher average order value than
    Consumer segment, but Consumer has 8x more orders. Revenue split:
    Enterprise 45%, Consumer 38%, SMB 17%."

  Source 4: Trend decomposition
    For time series: decompose into trend + seasonality + residual
    "Revenue shows strong Q4 seasonality (45% above annual average).
    Year-over-year growth is 18% after removing seasonal effects."

  Source 5: Query-driven follow-ups
    After each user query, suggest next logical analysis:
    User asked: "Show revenue by region"
    Follow-ups:
      1. "Want to see this broken down by quarter?"
      2. "The West region has the highest revenue — want to drill into
         which product categories drive it?"
      3. "Want to see if the regional pattern has changed over time?"

  Implementation:
    Hypothesis generation prompt (sent to LLM after each query):
      "Given the dataset profile, the user's current analysis
      ({last_query} → {result_summary}), and these statistical findings
      ({eda_highlights}), suggest 2-3 follow-up analyses the user might
      find valuable. Each suggestion should be specific and actionable,
      referencing actual columns and values from the data."

  Display: suggestions shown as clickable chips below each result
    User clicks → triggers that analysis automatically
    Acceptance rate target: > 15% of suggestions clicked
```

### 4.7 Report Synthesis

```
Compiles a multi-turn analysis session into a structured report.

Trigger: user clicks "Generate Report" or asks "summarize my findings"

Report structure:
  1. Executive Summary (2-3 sentences, key takeaways)
  2. Dataset Overview (source, size, date range, quality notes)
  3. Key Findings (numbered list with supporting charts)
  4. Detailed Analysis (one section per major question asked)
  5. Statistical Appendix (raw numbers, methodology notes)
  6. Recommendations (actionable next steps)

Generation pipeline:

  Step 1: Collect session artifacts
    - All queries and their results (DataFrames, charts)
    - Auto-EDA insights
    - Hypothesis suggestions that were explored
    - Total: 10-30 query-result pairs per session

  Step 2: Finding extraction (LLM)
    For each query-result pair, extract:
      - Finding statement (one sentence)
      - Supporting evidence (number or statistic)
      - Confidence level (high/medium/low based on sample size, p-value)
      - Associated chart (reference to generated visualization)

  Step 3: Narrative synthesis (LLM)
    Prompt: "Given these {n} findings from a data analysis session on
    {dataset_description}, write a coherent analytical report. Connect
    findings logically. Highlight the most important discoveries first.
    Use professional business analyst tone. Include specific numbers."

    Context window management:
      If session has > 30 findings: summarize in batches of 10, then
      synthesize summaries into final report (hierarchical summarization)

  Step 4: Chart integration
    - Select top 5-8 most impactful charts for the report
    - Resize to report-friendly dimensions (800x500px)
    - Embed as base64 PNG in HTML report
    - Interactive version: embed plotly HTML iframes

  Step 5: Export formats
    - HTML (primary): interactive charts, responsive layout
    - PDF: via Puppeteer headless Chrome rendering of HTML
    - Markdown: for integration into wikis or docs
    - PowerPoint: one finding per slide (stretch goal)

  Report quality metrics:
    - Coherence: does the narrative flow logically? (LLM-as-judge)
    - Completeness: are all major findings included?
    - Accuracy: do stated numbers match actual query results?
    - Actionability: does the report suggest concrete next steps?

  Storage:
    Reports stored in S3 with unique URL
    Shareable link (read-only, no auth required, expires in 30 days)
    Enterprise: shareable within organization only (SSO-gated)
```

### 4.8 Conversation and Session Management

```
Multi-turn analysis requires maintaining state across queries.

Session state:
  {
    session_id: uuid,
    user_id: uuid,
    dataset_id: uuid,
    sandbox_id: "sandbox-a3f2b1",        // sticky sandbox assignment
    conversation: [
      {
        turn: 1,
        user_query: "Show monthly revenue trend",
        generated_code: "...",
        execution_result: {stdout: "...", charts: ["chart_1.png"]},
        llm_response: "Here's the monthly revenue trend...",
        derived_variables: ["monthly_revenue"]   // new vars created
      },
      ...
    ],
    eda_profile: {...},                   // cached auto-EDA results
    derived_dataframes: {                 // named intermediate results
      "monthly_revenue": "DataFrame hash: abc123",
      "top_customers": "DataFrame hash: def456"
    },
    created_at: timestamp,
    last_active: timestamp
  }

Context window management for multi-turn:
  Total budget: 128K tokens (Claude 3.5 Sonnet / GPT-4o)
  Allocation:
    System prompt + instructions: 1,500 tokens (fixed)
    Schema + EDA summary: 2,000 tokens (fixed per dataset)
    Conversation history: up to 8,000 tokens (last 5-8 turns)
    Current query: 200 tokens
    Code generation budget: 1,000 tokens output
    Total per request: ~12,700 tokens

  Context pruning (when conversation exceeds 8,000 tokens):
    Keep: last 3 turns in full
    Summarize: turns 1 through N-3 into a 500-token summary
    Always keep: variable names and their definitions
    Always keep: error corrections (so the same mistake isn't repeated)

Derived variable tracking:
  When user says "use the monthly data from before," the system must
  know that "monthly data" refers to the monthly_revenue DataFrame
  created in turn 1.

  Implementation:
    After each code execution, extract new variable names from the code
    Store: {variable_name → description, shape, dtypes, creation_turn}
    In subsequent prompts, include: "Available variables from prior analysis:
      monthly_revenue: DataFrame (60 rows, 2 cols: month, revenue)
      top_customers: DataFrame (5 rows: customer_name, total_revenue)"
```

---

## 5. API Design

```
Core API endpoints:

POST /v1/datasets/upload
  Headers: Authorization: Bearer {jwt}
  Body: multipart/form-data (file + metadata)
  Response (async):
    {
      "dataset_id": "ds_abc123",
      "status": "processing",
      "estimated_time_seconds": 15,
      "poll_url": "/v1/datasets/ds_abc123/status"
    }

GET /v1/datasets/{dataset_id}/status
  Response:
    {
      "dataset_id": "ds_abc123",
      "status": "ready",          // processing | ready | failed
      "schema": {
        "columns": [...],
        "row_count": 500000,
        "size_bytes": 52428800
      },
      "eda_summary": {
        "insights": [...],
        "suggested_questions": [...]
      }
    }

POST /v1/datasets/{dataset_id}/connect
  Body: {
    "type": "postgresql",
    "host": "db.example.com",
    "port": 5432,
    "database": "analytics",
    "credentials_ref": "vault://db-creds/prod-readonly"
  }
  Response: { "dataset_id": "ds_sql_456", "tables": [...] }

POST /v1/analysis/query
  Body: {
    "session_id": "sess_xyz",     // null for new session
    "dataset_id": "ds_abc123",
    "question": "What were the top 5 customers by revenue last quarter?",
    "stream": true
  }
  Response (streamed via SSE):
    event: thinking
    data: {"step": "Generating SQL query..."}

    event: code
    data: {"language": "python", "code": "monthly = df.groupby(...)"}

    event: executing
    data: {"step": "Running analysis..."}

    event: result
    data: {
      "answer": "The top 5 customers by Q4 2024 revenue are...",
      "table": {"columns": [...], "rows": [...]},
      "charts": [
        {"type": "bar", "html_url": "/charts/ch_789.html",
         "png_url": "/charts/ch_789.png"}
      ],
      "code_executed": "SELECT c.name, SUM(o.revenue)...",
      "suggestions": [
        "Want to see their purchase frequency?",
        "Compare these customers across quarters?"
      ],
      "session_id": "sess_xyz"
    }

POST /v1/analysis/report
  Body: {
    "session_id": "sess_xyz",
    "format": "html",             // html | pdf | markdown
    "title": "Q4 2024 Revenue Analysis"
  }
  Response:
    {
      "report_id": "rpt_abc",
      "status": "generating",
      "estimated_time_seconds": 30,
      "poll_url": "/v1/reports/rpt_abc"
    }

WebSocket /v1/ws/analysis
  Used for real-time streaming of results and progress updates.
  Preferred over SSE for bidirectional communication (user can cancel).
  Heartbeat: every 30 seconds.
  Auto-reconnect: client-side with exponential backoff.
```

---

## 6. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Sandbox runtime | gVisor containers | Docker only | gVisor adds syscall-level isolation; Docker alone has known escape vectors |
| Code generation model | GPT-4o / Claude 3.5 Sonnet | Code-specialized (CodeLlama) | General models handle NL ambiguity better; code-only models miss business context |
| Execution kernel | IPython (persistent) | Fresh process per query | Persistent kernel lets users reference prior DataFrames across turns; 10x faster for follow-ups |
| DataFrame library | pandas (default) + polars (> 200MB) | pandas only | polars lazy evaluation handles 500MB files in 4GB RAM; pandas OOMs at ~300MB |
| Chart library | plotly (interactive) | matplotlib (static) | Interactive charts let users hover, zoom, filter without re-querying; 3x higher user engagement |
| Schema inference | Sample-based (15K rows) | Full scan | 15K rows gives 99%+ accuracy for type inference; full scan of 500MB adds 20+ seconds |
| Ambiguity handling | Default + state assumption | Always ask | Asking clarification on every ambiguous query breaks analysis flow; 80% of defaults are correct |
| Auto-EDA | Async (Kafka job) | Synchronous | 500MB files take 20-30s for full EDA; async lets user start querying on basic schema while EDA completes |
| Session persistence | Sandbox snapshot + restore | Always-on containers | Keeping 50K containers alive wastes resources; snapshot/restore gives 3s resume at 10x lower cost |
| Report generation | Hierarchical summarization | Single-pass LLM | Sessions with 30+ findings exceed context window; hierarchical approach handles unlimited session length |

---

## 7. Cost Optimization Strategies

```
LLM costs dominate ($11.5M/year at scale). Optimization levers:

1. Schema-aware prompt compression
   Instead of sending full dataset to LLM, send compressed schema:
     Full: "order_date: datetime64, min=2020-01-01, max=2024-12-31,
            count=500000, missing=0, granularity=daily, ..."
     Compressed: "order_date: date [2020-2024, daily, 500K rows, no nulls]"
   Savings: 40% fewer input tokens on schema context
   Impact: ~$4.5M/year saved

2. Query template caching
   Common patterns: "show X by Y over time", "top N by metric",
     "compare A vs B", "distribution of X"
   Cache: (query_pattern, schema_hash) → code template
   Fill in column names without LLM call
   Hit rate: 15-20% of queries match templates
   Savings: $1.5-2M/year

3. Tiered model routing
   Simple queries (top N, basic aggregation): GPT-4o-mini ($0.15/$0.60 per M)
   Complex queries (multi-table joins, statistical): GPT-4o ($3/$15 per M)
   Report synthesis: Claude 3.5 Sonnet (strong at long narrative)
   Router: classify query complexity before LLM call (small classifier, < 5ms)
   Savings: 60% of queries are simple → route to mini → $5M/year saved

4. Prompt caching (Anthropic/OpenAI feature)
   Schema + system prompt is identical across queries in a session
   Cache the 2,000-token prefix → pay 90% less for cached tokens
   Savings: ~$3M/year on input token costs

5. Self-hosted models for simple queries
   Llama 3.1 70B or Qwen2.5-Coder-32B on vLLM cluster
   4x A100 GPUs: $50K/month = $600K/year
   Handles 60% of simple queries (replacing $5M in API costs)
   Net savings: $4.4M/year

Total optimized cost: ~$3-4M/year (vs $11.5M unoptimized)
```

---

## 8. Failure Modes and Mitigations

```
Failure 1: Generated code crashes at runtime
  Frequency: 15-20% of first attempts
  Cause: column name mismatch, type error, NaN handling, memory overflow
  Mitigation: self-repair loop (send error + code back to LLM, retry 2x)
  After self-repair: success rate reaches 92%
  After 2 failed retries: return error to user with explanation

Failure 2: LLM generates correct code but wrong analysis
  Example: user asks "average revenue" but code computes median
  Frequency: 5-8% of queries
  Mitigation: show generated code to user; include assumption statement
    "I computed the mean (average) revenue. Click here to see the code."
  Users can edit code directly in the UI for corrections

Failure 3: Sandbox OOM on large datasets
  Cause: pandas loading 500MB CSV into 4GB RAM (actual memory ~2-3x file size)
  Mitigation:
    Files > 200MB: auto-switch to polars (lazy evaluation, streams from disk)
    Files > 500MB: auto-chunk with dask or polars scan_csv
    Always: set dtype optimization (int32 instead of int64, category for strings)
  Memory reduction: 60-70% with dtype optimization

Failure 4: Visualization renders but is unreadable
  Cause: too many data points, overlapping labels, wrong chart type
  Mitigation: chart type selection rules (Section 4.5) + sampling
  Fallback: if chart has > 100 categories → auto-truncate to top 20

Failure 5: Schema inference guesses wrong type
  Example: ZIP codes detected as integers (loses leading zeros)
  Frequency: 3-5% of columns
  Mitigation: semantic type detection (column named "zip" → force string)
  User override: UI lets user correct inferred types before analysis

Failure 6: SQL injection via natural language
  Risk: user asks "Show all data; DROP TABLE customers;"
  Mitigation: generated SQL runs with READ-ONLY database connection
  Additional: parameterized queries where possible; sandbox has no
  write access to source database
```

---

## 9. Interview Discussion Points

**Why not just give the LLM raw data and ask it to analyze directly?** LLMs cannot reliably perform arithmetic on large datasets. GPT-4o asked "what is the average of these 1000 numbers" will hallucinate or approximate. The correct architecture generates code that a deterministic runtime (pandas, SQL) executes. The LLM is the translator (NL to code), not the calculator. This separation ensures numerical accuracy regardless of dataset size.

**The schema inference quality determines everything downstream.** If the system infers a date column as a string, every time-series question fails. If it misses that "revenue" is monetary, currency formatting is wrong. The 5-10 seconds spent at upload time building a rich schema profile (types, distributions, semantics) is the highest-leverage investment. Production systems like Julius AI report that 40% of user complaints trace back to schema inference errors, not LLM quality.

**Persistent kernel vs. stateless execution -- a critical architecture decision.** Stateless (fresh process per query) is simpler and more secure. But data analysis is inherently stateful: "now filter that to just Q4" references the DataFrame from the prior query. A persistent IPython kernel maintains variables across turns, enabling conversational analysis. The tradeoff: state management complexity (snapshot/restore, memory leaks from accumulating DataFrames) vs. user experience. Every production system (ChatGPT Code Interpreter, Julius, Noteable) chose persistent kernels because stateless analysis is unusable for real workflows.

**How do you handle a 500MB CSV in a 4GB sandbox?** Naive pandas read_csv on a 500MB file consumes 1.5-3GB of RAM (object overhead, string storage). Solutions, in order of preference: (1) dtype optimization at load time (downcast numerics, categorize strings) reduces memory 60%; (2) polars lazy evaluation processes data in streaming fashion without loading all rows; (3) chunked processing via dask for truly massive files; (4) pre-convert to Parquet at upload time (columnar format, 3-5x compression, memory-mapped reads). The upload service should convert CSV to Parquet on ingestion -- every downstream read is faster and cheaper.

**Ambiguity resolution is a product decision, not just a technical one.** When the user says "show me trends," you can: (A) always ask for clarification (safe but annoying -- user leaves after 3 clarification rounds), (B) always guess (fast but sometimes wrong), or (C) guess with transparency (default to most likely interpretation, state the assumption, offer alternatives). Option C is what production systems converge on. The key metric is the "clarification abandonment rate" -- if > 20% of users drop off after a clarification question, you are asking too often.

**Why auto-EDA matters for LLM code quality.** Without EDA context, the LLM generating code does not know that revenue is right-skewed (should it use mean or median?), that there is a data gap in March 2022 (time series will have a misleading dip), or that the "status" column has 47 unique values (a pie chart would be unreadable). Feeding EDA results into the code generation prompt improves first-attempt code correctness from 72% to 89% in production benchmarks.

**Security of the sandbox is not optional -- it is existential.** The system executes LLM-generated code. If the LLM is tricked (prompt injection) or hallucinates dangerous code (os.system('rm -rf /')), the sandbox must contain the blast radius. gVisor intercepts syscalls at the kernel level, not just at the container boundary. No network egress prevents data exfiltration. Read-only dataset mounts prevent data corruption. The 60-second execution limit prevents crypto-mining. Every layer matters because the attack surface is "arbitrary code execution triggered by natural language input" -- one of the highest-risk architectures in software.

**Report synthesis requires hierarchical summarization for long sessions.** A power user might run 50 queries in a session. At 500 tokens per query-result pair, that is 25,000 tokens of findings -- too much to fit in a single LLM context window alongside instructions. The solution: summarize in batches of 10 findings, then synthesize the batch summaries into a final report. This hierarchical approach handles arbitrarily long sessions but introduces a risk of information loss at each summarization layer. Mitigation: always preserve exact numbers and chart references through the summarization chain; only compress narrative.