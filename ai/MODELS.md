# Models — current state and roadmap

## Status: no trained model yet

The ETL pipeline and notebooks compute **per-session driver-behaviour
features** that are designed as inputs for a future classifier. No model has
been trained or deployed.

## Available features (computed in `etl/transform.py` → `session_summary()`)

| Feature | Type | Description |
|---|---|---|
| `effective_hz` | float | Samples per second after filtering |
| `idle_pct` | float | % of session spent with all axes at neutral |
| `avg_throttle` | float | Mean throttle value |
| `max_throttle` | float | Peak throttle |
| `max_brake` | float | Peak brake |
| `throttle_brake_overlap_pct` | float | % of frames where both pedals > 5% |
| `steering_smoothness` | float | Std of frame-to-frame steering delta (lower = smoother) |
| `steering_aggression` | float | Mean |steering_delta| (higher = jerkier) |
| `avg_latency_ms` | float | Mean server−client latency |
| `p95_latency_ms` | float | 95th percentile latency |
| `source` | categorical | `wheel` or `keyboard` — natural label |

## Candidate model: input-source classifier

**Goal:** predict whether a session was driven with a physical wheel or
keyboard, based solely on the driving-style features above.

**Why useful:**
- Validates that the features actually capture meaningful differences between
  input methods.
- Serves as a sanity check — if the model can't distinguish wheel from
  keyboard, the features aren't discriminative enough for harder tasks
  (driver identification, skill grading).

**Recommended approach:**
1. Use `session_summary()` output as the feature matrix.
2. Label = `source` column (`wheel` / `keyboard`).
3. Train/test split: leave-one-session-out cross-validation (dataset is small —
   14 sessions at time of writing).
4. Baseline model: Random Forest or Logistic Regression (interpretable,
   works with small N).
5. Evaluation: accuracy, precision, recall, F1 (macro-averaged), confusion matrix.

**Minimum viable sample size:** ~30 sessions (15 per class) for a meaningful
split. Current dataset has 14 sessions total — not yet enough to train
reliably. Collect more sessions before attempting.

## Future models

| Model | Input | Output | When |
|---|---|---|---|
| Driver identification | Session features | driver-id | When multiple users drive regularly |
| Skill grading | Session features | beginner / intermediate / advanced | Needs labelled ground truth |
| Anomaly detection | Per-frame time-series | outlier score | Useful for detecting hardware issues (stuck pedal, broken sensor) |
| End-to-end fidelity | Controller input + ESP32 telemetry | intended-vs-actual delta | Needs ESP32 → backend telemetry (not yet implemented) |

## Where model artefacts go

Trained models should be saved to `models/` using joblib or pickle:

```python
import joblib
joblib.dump(model, 'models/source_classifier.joblib')
```

The `.gitignore` already excludes `models/*.pkl`, `*.joblib`, `*.h5`.

## Reproducing

Once a model exists, add a notebook `notebooks/04_model.ipynb` that:
1. Loads data via `etl.pipeline`
2. Computes `session_summary()`
3. Trains, evaluates, and saves the model
4. Prints metrics + confusion matrix
