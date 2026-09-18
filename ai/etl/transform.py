"""Clean and enrich raw control-input log rows."""
from __future__ import annotations

import logging
import math
import uuid

import pandas as pd

logger = logging.getLogger(__name__)


def _normalize_session_id(value):
    """Mongo returns UUIDs as bytes under the legacy uuidRepresentation. Convert
    to a stable string so groupby / filtering / display all behave."""
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return None
    if isinstance(value, uuid.UUID):
        return str(value)
    if isinstance(value, (bytes, bytearray)):
        try:
            return str(uuid.UUID(bytes=bytes(value)))
        except (ValueError, TypeError):
            return value.hex()
    return str(value)


def to_dataframe(rows: list[dict]) -> pd.DataFrame:
    """Build a sorted dataframe indexed by recorded_at.

    Accepts the timestamp column under either `recordedAt` (Mongo doc from the
    Spring backend) or `recorded_at` (legacy snapshot).
    """
    if not rows:
        return pd.DataFrame()
    df = pd.DataFrame(rows)
    src_col = "recordedAt" if "recordedAt" in df.columns else "recorded_at"
    df["recorded_at"] = pd.to_datetime(df[src_col], utc=True, format="mixed")
    if src_col != "recorded_at":
        df = df.drop(columns=[src_col])
    if "sessionId" in df.columns:
        df["sessionId"] = df["sessionId"].map(_normalize_session_id)
    df = df.set_index("recorded_at").sort_index()
    return df


def add_derived_features(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty:
        return df
    df = df.copy()

    df["movement"] = df["throttle"] - df["brake"]
    df["idle"] = (df["steering"] == 0) & (df["throttle"] == 0) & (df["brake"] == 0)

    # Network/processing latency: server timestamp - client timestamp.
    # Some senders (notably the keyboard fallback) omit clientTimestamp and we
    # store 0. Decoding 0 as epoch 1970 would skew latency by ~56 years per
    # affected row, so treat anything before 2020 as missing.
    raw_client_ts = pd.to_numeric(df["clientTimestamp"], errors="coerce")
    valid = raw_client_ts >= 1_577_836_800_000  # 2020-01-01 in ms
    client_ts = pd.to_datetime(raw_client_ts.where(valid), unit="ms", utc=True)
    server_ts = pd.Series(df.index, index=df.index)
    df["latency_ms"] = (server_ts - client_ts).dt.total_seconds() * 1000

    df["abs_steering"] = df["steering"].abs()
    df["throttle_brake_overlap"] = ((df["throttle"] > 0.05) & (df["brake"] > 0.05)).astype(int)

    df["steering_delta"] = df.groupby("sessionId", dropna=False)["steering"].diff().fillna(0)
    df["throttle_delta"] = df.groupby("sessionId", dropna=False)["throttle"].diff().fillna(0)

    ts_series = pd.Series(df.index, index=df.index)
    df["sample_dt_ms"] = (
        ts_series.groupby(df["sessionId"], dropna=False).diff().dt.total_seconds() * 1000
    )

    return df


def session_summary(df: pd.DataFrame) -> pd.DataFrame:
    if df.empty or "sessionId" not in df.columns:
        return pd.DataFrame()
    sessions = df[df["sessionId"].notna()].copy()
    if sessions.empty:
        return pd.DataFrame()

    def _agg(group: pd.DataFrame) -> pd.Series:
        duration_s = (group.index.max() - group.index.min()).total_seconds()
        return pd.Series({
            "start": group.index.min(),
            "end": group.index.max(),
            "duration_s": duration_s,
            "rows": len(group),
            "effective_hz": len(group) / duration_s if duration_s > 0 else math.nan,
            "source": group["source"].mode().iloc[0] if not group["source"].mode().empty else None,
            "idle_pct": group["idle"].mean() * 100,
            "avg_throttle": group["throttle"].mean(),
            "max_throttle": group["throttle"].max(),
            "max_brake": group["brake"].max(),
            "throttle_brake_overlap_pct": group["throttle_brake_overlap"].mean() * 100,
            "steering_smoothness": group["steering_delta"].std(),
            "steering_aggression": group["steering_delta"].abs().mean(),
            "avg_latency_ms": group["latency_ms"].mean(),
            "p95_latency_ms": group["latency_ms"].quantile(0.95),
        })

    summary = (
        sessions.groupby("sessionId", dropna=True)
        .apply(_agg, include_groups=False)
        .sort_values("start", ascending=True)
    )
    # Replace the unwieldy UUID index with human-readable labels (S01, S02, ...)
    # ordered by start time. Keep the UUID as a column for traceability.
    summary = summary.reset_index().rename(columns={"sessionId": "uuid"})
    summary.index = [f"S{i:02d}" for i in range(1, len(summary) + 1)]
    summary.index.name = "session"
    return summary.sort_values("start", ascending=False)


def detect_outliers(df: pd.DataFrame) -> dict[str, int]:
    """Quality-check counts useful for the EDA notebook."""
    if df.empty:
        return {}
    eps = 1e-6
    return {
        "rows_total": len(df),
        "rows_missing_session": int(df["sessionId"].isna().sum()),
        "rows_negative_latency": int((df["latency_ms"] < 0).sum()),
        "rows_huge_latency": int((df["latency_ms"] > 5000).sum()),
        "rows_steering_out_of_range": int(((df["steering"] < -1 - eps) | (df["steering"] > 1 + eps)).sum()),
        "rows_throttle_out_of_range": int(((df["throttle"] < -eps) | (df["throttle"] > 1 + eps)).sum()),
        "rows_brake_out_of_range": int(((df["brake"] < -eps) | (df["brake"] > 1 + eps)).sum()),
        "rows_idle": int(df["idle"].sum()),
    }
