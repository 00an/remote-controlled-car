"""End-to-end ET pipeline runner.

The L (load) step is owned by the backend now — it writes control inputs to the
same MongoDB collection we read here. This script runs E + T: pull from Mongo,
clean, enrich with derived features, optionally write a parquet snapshot.

Usage:
    python -m etl.pipeline                  # extract + transform, log quality
    python -m etl.pipeline --export-parquet # also write data/control_inputs.parquet
"""
from __future__ import annotations

import argparse
import logging
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Callable

from etl.config import load_config
from etl.extract import iter_documents
from etl.load import get_collection
from etl.transform import add_derived_features, detect_outliers, session_summary, to_dataframe


@dataclass
class StepResult:
    name: str
    status: str  # "ok" | "error" | "skipped"
    duration_ms: float
    details: dict[str, Any] = field(default_factory=dict)
    error: str | None = None


@dataclass
class PipelineRun:
    steps: list[StepResult] = field(default_factory=list)
    total_ms: float = 0.0
    status: str = "ok"


def _run_step(run: PipelineRun, name: str, fn: Callable[[], dict[str, Any]],
              on_step: Callable[[StepResult], None] | None) -> StepResult:
    started = time.perf_counter()
    try:
        details = fn() or {}
        result = StepResult(name=name, status="ok",
                            duration_ms=(time.perf_counter() - started) * 1000,
                            details=details)
    except Exception as exc:  # noqa: BLE001
        result = StepResult(name=name, status="error",
                            duration_ms=(time.perf_counter() - started) * 1000,
                            error=f"{type(exc).__name__}: {exc}")
        run.status = "error"
    run.steps.append(result)
    if on_step is not None:
        on_step(result)
    return result


def run_pipeline(export_parquet: bool = False,
                 batch_size: int = 1000,
                 on_step: Callable[[StepResult], None] | None = None) -> PipelineRun:
    """Run the ET pipeline and report per-step events. Each step's `details`
    dict carries human-readable counts/paths suitable for a dashboard."""
    run = PipelineRun()
    started = time.perf_counter()
    state: dict[str, Any] = {}

    def step_config() -> dict[str, Any]:
        cfg = load_config()
        state["config"] = cfg
        return {
            "mongo_db": cfg.mongo_db,
            "collection": cfg.mongo_collection,
            "broadcast_collection": cfg.mongo_broadcast_collection,
            "project_root": str(cfg.project_root),
        }

    def step_connect() -> dict[str, Any]:
        cfg = state["config"]
        collection = get_collection(cfg.mongo_uri, cfg.mongo_db, cfg.mongo_collection)
        state["collection"] = collection
        return {"namespace": f"{cfg.mongo_db}.{cfg.mongo_collection}",
                "estimated_doc_count": collection.estimated_document_count()}

    def step_extract() -> dict[str, Any]:
        rows = list(iter_documents(state["collection"], batch_size=batch_size))
        state["rows"] = rows
        return {"rows_extracted": len(rows), "batch_size": batch_size}

    def step_transform() -> dict[str, Any]:
        df = to_dataframe(state["rows"])
        df = add_derived_features(df)
        state["df"] = df
        return {"rows": len(df), "columns": len(df.columns),
                "derived_columns": [c for c in df.columns
                                    if c in {"movement", "idle", "latency_ms",
                                             "abs_steering", "throttle_brake_overlap",
                                             "steering_delta", "throttle_delta",
                                             "sample_dt_ms"}]}

    def step_quality() -> dict[str, Any]:
        return detect_outliers(state["df"])

    def step_sessions() -> dict[str, Any]:
        sessions = session_summary(state["df"])
        state["sessions"] = sessions
        return {"sessions": len(sessions)}

    def step_parquet() -> dict[str, Any]:
        if not export_parquet:
            return {"skipped": True}
        cfg = state["config"]
        data_dir = cfg.project_root / "data"
        data_dir.mkdir(exist_ok=True)
        path = data_dir / "control_inputs.parquet"
        state["df"].to_parquet(path)
        return {"path": str(path), "size_bytes": path.stat().st_size}

    steps: list[tuple[str, Callable[[], dict[str, Any]]]] = [
        ("Load config", step_config),
        ("Connect to MongoDB", step_connect),
        ("Extract documents", step_extract),
        ("Transform + enrich", step_transform),
        ("Quality check", step_quality),
        ("Session summary", step_sessions),
        ("Export parquet", step_parquet),
    ]

    for name, fn in steps:
        result = _run_step(run, name, fn, on_step)
        if not export_parquet and name == "Export parquet" and result.status == "ok":
            result.status = "skipped"
        if result.status == "error":
            break

    run.total_ms = (time.perf_counter() - started) * 1000
    return run


def configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def main(argv: list[str] | None = None) -> int:
    configure_logging()
    parser = argparse.ArgumentParser(description="Run the NL-14 control-input analytics pipeline.")
    parser.add_argument("--export-parquet", action="store_true", help="Write data/control_inputs.parquet.")
    parser.add_argument("--batch-size", type=int, default=1000)
    args = parser.parse_args(argv)

    logger = logging.getLogger("pipeline")

    def log_step(step: StepResult) -> None:
        if step.status == "error":
            logger.error("%s failed in %.0f ms: %s", step.name, step.duration_ms, step.error)
        else:
            logger.info("%s [%s] %.0f ms — %s", step.name, step.status,
                        step.duration_ms, step.details)

    run = run_pipeline(export_parquet=args.export_parquet,
                       batch_size=args.batch_size,
                       on_step=log_step)
    logger.info("Pipeline %s in %.0f ms (%d steps)", run.status, run.total_ms, len(run.steps))
    return 0 if run.status == "ok" else 1


if __name__ == "__main__":
    sys.exit(main())
