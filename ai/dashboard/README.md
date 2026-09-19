# Streamlit Dashboard

Interactive view on the ETL pipeline + MongoDB control-input data.

## Tabs

- **Overview** — global counts, source mix, activity over time.
- **Session explorer** — drill into one session's steering / throttle / brake
  time-series, distributions, throttle-vs-brake overlap.
- **ETL pipeline** — `detect_outliers` quality counts, derived-feature
  distributions, full session summary table.
- **Raw vs Logged** — compares the raw broadcast collection
  (`controller_broadcasts`, what the ESP32 actually received) against the
  filtered `control_inputs` collection. Quantifies what the backend's
  quantize / dedup / hysteresis layer drops.
- **System health** — latency distribution, rolling p95 latency,
  inter-sample gap, per-session effective Hz.

## Running

From `ai/`:

```powershell
.\.venv\Scripts\Activate.ps1   # (or run the pipeline runner once to create it)
pip install -r requirements.txt
streamlit run dashboard/app.py
```

Requires the same `.env` as the ETL pipeline (`MONGO_URI`,
`MONGO_DB`, `MONGO_COLLECTION`, and the new
`MONGO_BROADCAST_COLLECTION` — defaults to `controller_broadcasts`).

The "Raw vs Logged" tab only has data once the updated backend (with
`ControllerBroadcastLoggingService` wired into `ControllerWebSocketHub`) has
been deployed and at least one session has run. Older sessions show only the
filtered side.
