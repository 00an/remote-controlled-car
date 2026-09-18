# Data & Analytics

Everything that turns the car's input stream into insight: ETL pipeline,
exploratory notebooks, and an interactive **Streamlit dashboard**.

If you only have 30 seconds, read [What's in here](#whats-in-here) and
[I just want to see the dashboard](#i-just-want-to-see-the-dashboard).

## How this fits in the project

```
┌──────────────────┐   POST     ┌────────────────────┐    WebSocket    ┌────────┐
│ steering-input/  │ ─────────► │  backend     │ ──────────────► │ ESP32  │
│ main.py (wheel)  │            │  /api/controller   │                 │ (car)  │
│ or frontend kbd  │            │                    │                 └────────┘
└──────────────────┘            │  writes to Mongo:  │
                                │   ├ control_inputs       (filtered)
                                │   └ controller_broadcasts (raw, every frame)
                                └────────┬───────────┘
                                         │
                                         ▼
                              ┌────────────────────┐
                              │  ai     │  ← you are here
                              │  (ETL + dashboard) │
                              └────────────────────┘
```

The backend persists two views of the same input stream into MongoDB:

| Collection | What it is | Used for |
|---|---|---|
| `control_inputs` | **Filtered**: quantized to 0.01, pedal hysteresis applied, deduped per 200ms window | Long-term analytics; what a human cares about |
| `controller_broadcasts` | **Raw**: every WebSocket frame, exactly as sent to the ESP32 | Comparing what the ESP saw vs what got stored; quantifying filter dropout |

Notebooks and the dashboard read these collections directly — no auth, no API
round trip.

## What's in here

```
ai/
├── etl/                    # reusable Python ETL package
│   ├── config.py           # loads .env, returns immutable Config
│   ├── extract.py          # streams documents from MongoDB
│   ├── broadcasts.py       # raw-broadcast extract + transform
│   ├── transform.py        # cleaning + derived features + session summary
│   ├── load.py             # MongoDB connection helper
│   └── pipeline.py         # E + T runner; exposes run_pipeline() with step events
├── dashboard/              # Streamlit app — see "I just want to see the dashboard"
│   ├── app.py
│   └── README.md
├── notebooks/
│   ├── 01_eda.ipynb        # data quality, distributions, raw-vs-logged comparison
│   ├── 02_behavior.ipynb   # driver smoothness, aggression, reaction time
│   └── 03_system_health.ipynb  # latency, sample rate, gap detection
├── scripts/
│   ├── run_pipeline.ps1    # Windows: creates venv, installs deps, runs ETL
│   ├── run_pipeline.sh     # Linux/Mac equivalent
│   └── run_dashboard.ps1   # Launches Streamlit and opens the browser
├── .env.example
└── requirements.txt
```

## I just want to see the dashboard

If you're not on the data team and just want to look at the data your code
produced:

1. **Get a `.env`** from a data-team member (it contains the Mongo connection
   string). Copy it into `ai/.env`.
2. **Run the launcher**:
   ```powershell
   cd ai
   .\scripts\run_dashboard.ps1
   ```
   First run takes ~30s (creates a `.venv`, installs deps). Subsequent runs
   are instant. The browser opens at <http://localhost:8501>.
3. **Click around**. Start with the **About** tab — it explains every other
   tab and what the data means.

Linux/Mac equivalent (no `.ps1`, do it manually):
```bash
cd ai
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
streamlit run dashboard/app.py
```

## Setup (full data workflow)

1. Copy `.env.example` to `.env` and fill in `MONGO_URI`.
2. Create the venv and install deps (one-shot via the runner):
   ```powershell
   .\scripts\run_pipeline.ps1
   ```
   On Linux/Mac use `./scripts/run_pipeline.sh` instead.
3. Open any notebook in VS Code and select the `.venv` kernel.

## Running the ETL pipeline

```powershell
# Extract from Mongo + transform + log quality counts
python -m etl.pipeline

# Also write data/control_inputs.parquet for offline analysis
python -m etl.pipeline --export-parquet
```

You can also trigger the pipeline from the dashboard's **ETL pipeline** tab —
it shows each step as a colored block with timing and a per-step details
panel.

There's no "load" step here: the backend persists to Mongo directly. This
package is purely for **analysis**, not for moving data.

## Dashboard tabs at a glance

| Tab | What you'll find |
|---|---|
| **Overview** | Global KPIs, session sample counts, source mix (wheel vs keyboard), activity over time |
| **Session explorer** | Pick a session → see its steering/throttle/brake time-series, distributions, overlap scatter |
| **ETL pipeline** | Trigger the pipeline live, see step-by-step flow + timeline + per-step output. Also the quality counts and derived-feature distributions |
| **Raw vs Logged** | Compares `controller_broadcasts` (raw, → ESP32) with `control_inputs` (filtered) for one session. Quantifies what the backend's filter layer drops |
| **System health** | Latency p50/p95/p99, inter-sample gap, rolling p95 over time, per-session effective Hz |
| **About** | Architecture, derived-column reference, filtering-layer threshold values |

## MongoDB Atlas setup (data team only)

1. Sign up at <https://www.mongodb.com/cloud/atlas/register>
2. Create a free **M0** cluster (any region near you)
3. **Database Access** → add a user, save the password
4. **Network Access** → allow `0.0.0.0/0` (fine for the free tier with auth) — or
   add the OKD egress IP so the backend can reach it
5. **Connect** → "Drivers" → Python → copy the connection string
6. Paste into `.env` as `MONGO_URI=...` (replace `<password>`)
7. Set the same `SPRING_DATA_MONGODB_URI` env var on the backend Deployment in OKD

## Why MongoDB for logging, Postgres for users?

The backend uses Postgres for everything relational (users, auth, accounts) and
MongoDB for control-input logs. Mixing two stores is justified by the
**fundamentally different access patterns**:

| Aspect | Users / auth (Postgres) | Control inputs (MongoDB) |
|--------|-------------------------|---------------------------|
| Cardinality | Dozens | Millions over time |
| Write rate | Sporadic (signup, password change) | Continuous (~5–10 Hz per active driver) |
| Schema | Fixed, evolves rarely | Likely to grow (ESP32 telemetry to come) |
| Relationships | Critical (foreign keys, joins) | Self-contained samples |
| Consistency need | Strong (auth flows) | Eventual is fine |
| Query shape | Lookups by id/username | Time windows, aggregations |

On the logging side specifically, Mongo wins on:

1. **Schema flexibility** — adding ESP32 telemetry (motor temp, battery, IMU) is
   a document field, not an `ALTER TABLE` on a multi-million row table.
2. **Time-series ergonomics** — Mongo 5+ has native time-series collections;
   Postgres needs TimescaleDB or manual partitioning.
3. **Document-shaped data** — `buttons` and `hats` are nested arrays.
4. **Aggregation expressiveness** — per-session pipelines (smoothness, p95
   latency, gap detection) read more naturally as Mongo aggregation stages.

**Could Postgres still work?** Yes — at current scale, fine. The justification
is forward-looking. Mixing stores is also a real-world pattern (polyglot
persistence).

## Data fields after transform

| Field | Source | Notes |
|---|---|---|
| `recorded_at` (index) | backend | UTC, server-side timestamp |
| `clientTimestamp` | client | ms epoch, used for latency calc |
| `steering`, `throttle`, `brake` | client | normalized -1..1 / 0..1 |
| `source` | backend | `wheel` or `keyboard` |
| `sessionId` | backend | UUID, null outside an active session |
| `buttons`, `hats` | client | native nested arrays in Mongo |
| `movement` | derived | `throttle - brake` |
| `idle` | derived | all three axes at neutral |
| `latency_ms` | derived | `recorded_at - clientTimestamp` |
| `abs_steering` | derived | `abs(steering)` |
| `throttle_brake_overlap` | derived | both > 5%, beginner signal |
| `steering_delta`, `throttle_delta` | derived | per-session diff |
| `sample_dt_ms` | derived | gap to previous sample in same session |

## Backend filtering layer (background for Raw vs Logged)

Before writing to `control_inputs`, the backend applies three filters. The
**Raw vs Logged** dashboard tab quantifies their combined effect.

| Filter | Setting | What it does |
|---|---|---|
| **Quantize** | step 0.01 | Rounds steering/throttle/brake to a 0.01 grid — removes float noise |
| **Pedal hysteresis** | wake 0.08 / sleep 0.05 / full 0.96 | Pedal has to climb above wake to leave rest, drop below sleep to return — kills flicker at the deadzone |
| **Min-interval dedup** | 200 ms | If the quantized triple is unchanged AND < 200ms passed since the last write, skip the frame |

These are tuned in the backend's `application.properties` (search for
`controller.logging.*`).

## Data quality thresholds and evaluation metrics

The ETL pipeline's `detect_outliers()` function flags suspicious rows. These
are the thresholds used and what "healthy" looks like:

| Metric | Threshold | Healthy value | Where to check |
|---|---|---|---|
| `rows_missing_session` | sessionId is null | 0 | Pipeline quality step, ETL tab |
| `rows_negative_latency` | server < client clock | Low (clock-skew dependent) | System Health tab |
| `rows_huge_latency` | > 5000 ms | < 1% of total rows | System Health tab |
| `rows_steering_out_of_range` | outside [-1, 1] | 0 | ETL tab |
| `rows_throttle_out_of_range` | outside [0, 1] | 0 | ETL tab |
| `rows_brake_out_of_range` | outside [0, 1] | 0 | ETL tab |
| `rows_idle` | all axes at neutral | < 40% (higher = driver inactive) | Overview tab |
| `effective_hz` | rows / duration per session | 2–5 Hz (post-filter) | Session Explorer |
| `p95_latency_ms` | 95th percentile |latency| | < 1000 ms | System Health tab |
| `persist_ratio` | logged / raw broadcasts | 5–30% (filter is aggressive by design) | Raw vs Logged tab |

### Session-level behaviour metrics

Computed by `session_summary()` in `etl/transform.py`:

| Metric | What it measures | Interpretation |
|---|---|---|
| `steering_smoothness` | Std of consecutive steering deltas | Lower = smoother driving |
| `steering_aggression` | Mean |steering delta| | Higher = jerkier inputs |
| `throttle_brake_overlap_pct` | % frames with both pedals > 5% | > 5% suggests beginner driver |
| `idle_pct` | % frames at full neutral | High = tab left open, not driving |

### Model status

No ML model has been trained yet — see [MODELS.md](MODELS.md) for the feature
inventory, candidate model design, and roadmap.

## Preprocessing pipeline (step-by-step)

The ETL runs Extract → Transform in this exact order:

```
1. Load config       Read .env (MONGO_URI, db/collection names)
2. Connect           Ping MongoDB Atlas, verify reachability
3. Extract           Stream all docs from control_inputs (sorted by recordedAt)
4. to_dataframe()    Parse timestamps to UTC, normalize session UUIDs
5. add_derived()     Compute: movement, idle, latency_ms, abs_steering,
                     throttle_brake_overlap, steering_delta, throttle_delta,
                     sample_dt_ms
6. detect_outliers() Count suspicious rows (out-of-range, negative latency, etc.)
7. session_summary() Per-session aggregates (Hz, smoothness, aggression, overlap)
8. Export (optional) Write data/control_inputs.parquet
```

Each step's inputs and outputs are visible in the dashboard's **ETL pipeline**
tab when you click "Run pipeline now" — the step-details expanders show
exactly what each step produced.

## Reproducing the analysis

```powershell
# One command rebuilds everything from a clean checkout
.\scripts\run_pipeline.ps1 --export-parquet
```

Then run the notebooks in order. They read from Mongo directly using the same
connection helpers as the pipeline.

## Future work

- **ESP32 → backend telemetry**: the ESP currently consumes commands but
  doesn't publish actual servo position, motor temp, battery, IMU. Once it
  does, add a third Mongo collection and a "Intended vs Actual" dashboard
  section — end-to-end fidelity check.
- **Driver classification model**: session-level features (smoothness,
  aggression, overlap %) are ready; see [MODELS.md](MODELS.md) for the full
  roadmap. Needs ~30+ sessions before training is meaningful.
