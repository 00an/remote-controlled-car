"""Extract + transform for the raw broadcast collection.

The backend writes one document per WebSocket frame sent to the ESP32 into
``controller_broadcasts`` — no quantize, no dedup, no hysteresis. Paired with
the filtered ``control_inputs`` collection this lets us measure exactly what
the backend's filtering layer drops before the data lands in the ETL pipeline.
"""
from __future__ import annotations

import logging
import math
import uuid

import pandas as pd
from pymongo import ASCENDING

from etl.extract import _session_id_query
from etl.load import get_collection

logger = logging.getLogger(__name__)


def _normalize_session_id(value):
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


def fetch_broadcasts(mongo_uri: str, db_name: str, collection_name: str,
                    session_id: str | None = None) -> list[dict]:
    collection = get_collection(mongo_uri, db_name, collection_name)
    query: dict = _session_id_query(session_id) if session_id else {}
    cursor = collection.find(query, projection={"_id": 0}).sort("recordedAt", ASCENDING)
    return list(cursor)


def broadcasts_to_dataframe(rows: list[dict]) -> pd.DataFrame:
    """Project the raw axes to (steering, throttle, brake) using the same
    convention the backend / ESP32 use, but **without** any filtering. This is
    the pristine signal that hit the ESP."""
    if not rows:
        return pd.DataFrame()
    df = pd.DataFrame(rows)
    src_col = "recordedAt" if "recordedAt" in df.columns else "recorded_at"
    df["recorded_at"] = pd.to_datetime(df[src_col], utc=True, format="mixed")
    if src_col != "recorded_at":
        df = df.drop(columns=[src_col])
    if "sessionId" in df.columns:
        df["sessionId"] = df["sessionId"].map(_normalize_session_id)

    def _axis_value(axes, i, rest_default):
        """Extract axes[i] safely. `axes` may be a list, a numpy array, NaN,
        None, or even missing entirely."""
        try:
            if axes is None:
                return rest_default
            # Reject anything that doesn't behave like a sequence — catches NaN
            # floats that Mongo produces for missing fields.
            length = len(axes)
        except TypeError:
            return rest_default
        if i >= length:
            return rest_default
        val = axes[i]
        if val is None:
            return rest_default
        try:
            return float(val)
        except (TypeError, ValueError):
            return rest_default

    axes_col = df["axes"] if "axes" in df.columns else pd.Series([None] * len(df), index=df.index)

    def _clamp_unit(v):
        return max(-1.0, min(1.0, v))

    df["raw_steering"] = [_clamp_unit(_axis_value(a, 0, 0.0)) for a in axes_col]
    # Pedal raw axis: +1=rest, -1=floored. Map to 0..1 applied pressure.
    df["raw_throttle"] = [(1.0 - _clamp_unit(_axis_value(a, 1, 1.0))) / 2.0 for a in axes_col]
    df["raw_brake"] = [(1.0 - _clamp_unit(_axis_value(a, 2, 1.0))) / 2.0 for a in axes_col]

    df = df.set_index("recorded_at").sort_index()
    return df
