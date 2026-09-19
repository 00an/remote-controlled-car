"""Interactive analytics dashboard.

Reads from MongoDB live (cached with a short TTL) and exposes:
  - ETL pipeline overview (counts, quality, source mix)
  - Session explorer with steering / throttle / brake time-series
  - Raw broadcast (what ESP32 received) vs filtered Mongo logs comparison
  - System health: latency, sample rate, gap detection

Run:
    streamlit run dashboard/app.py
"""
from __future__ import annotations

import sys
from pathlib import Path

# Ensure `etl` is importable when streamlit launches from any cwd.
DASHBOARD_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = DASHBOARD_DIR.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
import streamlit as st

try:
    from streamlit_autorefresh import st_autorefresh
except ImportError:  # graceful fallback if dep missing
    st_autorefresh = None

from etl.broadcasts import broadcasts_to_dataframe, fetch_broadcasts
from etl.config import load_config
from etl.extract import fetch_all, fetch_by_session
from etl.load import get_collection
from etl.pipeline import PipelineRun, StepResult, run_pipeline
from etl.transform import (
    add_derived_features,
    detect_outliers,
    session_summary,
    to_dataframe,
)

st.set_page_config(
    page_title="Control Input Analytics",
    page_icon=":racing_car:",
    layout="wide",
)


# ---------------------------------------------------------------------------
# Data access (cached)
# ---------------------------------------------------------------------------

@st.cache_resource
def get_config():
    return load_config()


@st.cache_data(ttl=60, show_spinner="Loading control_inputs from MongoDB...")
def load_inputs() -> pd.DataFrame:
    cfg = get_config()
    rows = fetch_all(cfg.mongo_uri, cfg.mongo_db, cfg.mongo_collection)
    df = to_dataframe(rows)
    return add_derived_features(df)


def load_session_summary(df: pd.DataFrame) -> pd.DataFrame:
    # Not @st.cache_data: the input df contains unhashable list columns
    # (buttons, hats), which forces Streamlit to fall back to pickling and
    # spams warnings. The groupby is fast enough to recompute on each rerun.
    return session_summary(df)


@st.cache_data(ttl=60, show_spinner="Loading broadcast for session...")
def load_broadcasts_for_session(session_id: str) -> pd.DataFrame:
    cfg = get_config()
    rows = fetch_broadcasts(cfg.mongo_uri, cfg.mongo_db,
                            cfg.mongo_broadcast_collection, session_id=session_id)
    return broadcasts_to_dataframe(rows)


@st.cache_data(ttl=60, show_spinner="Loading logged samples for session...")
def load_inputs_for_session(session_id: str) -> pd.DataFrame:
    cfg = get_config()
    rows = fetch_by_session(cfg.mongo_uri, cfg.mongo_db,
                            cfg.mongo_collection, session_id=session_id)
    df = to_dataframe(rows)
    return add_derived_features(df)


def count_collections() -> dict[str, int]:
    """Live document counts for both Mongo collections. Not cached — used to
    compute deltas around a pipeline run."""
    cfg = get_config()
    counts = {}
    for label, name in (("control_inputs", cfg.mongo_collection),
                        ("controller_broadcasts", cfg.mongo_broadcast_collection)):
        try:
            counts[label] = get_collection(cfg.mongo_uri, cfg.mongo_db, name).count_documents({})
        except Exception:  # noqa: BLE001
            counts[label] = -1  # signals "could not read"
    return counts


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def kpi_row(items: list[tuple[str, str]]) -> None:
    cols = st.columns(len(items))
    for col, (label, value) in zip(cols, items):
        col.metric(label, value)


def fmt_int(n: float | int) -> str:
    try:
        return f"{int(n):,}"
    except (TypeError, ValueError):
        return "—"


def fmt_pct(n: float) -> str:
    if pd.isna(n):
        return "—"
    return f"{n:.1f}%"


def fmt_ms(n: float) -> str:
    if pd.isna(n):
        return "—"
    return f"{n:.0f} ms"


STATUS_COLOR = {"ok": "#22c55e", "error": "#ef4444", "skipped": "#9ca3af",
                "pending": "#374151", "running": "#3b82f6"}
STATUS_ICON = {"ok": "✓", "error": "✗", "skipped": "⊘",
               "pending": "○", "running": "▶"}


def render_pipeline_flow(steps: list[StepResult] | list[dict], total_ms: float | None,
                         container) -> None:
    """Render a left-to-right flow diagram. Accepts either StepResult objects
    or plain dicts (status, name, duration_ms) so it can show in-progress
    state during a live run."""
    cells = container.columns(len(steps) * 2 - 1) if len(steps) > 1 else [container]
    for i, step in enumerate(steps):
        if isinstance(step, StepResult):
            name, status, dur = step.name, step.status, step.duration_ms
        else:
            name, status, dur = step["name"], step["status"], step.get("duration_ms", 0.0)
        color = STATUS_COLOR.get(status, "#374151")
        icon = STATUS_ICON.get(status, "?")
        col = cells[i * 2] if len(steps) > 1 else cells[0]
        col.markdown(
            f"""
            <div style="border:1px solid {color}; border-radius:8px; padding:8px 10px;
                        background:{color}22; text-align:center; min-height:78px;
                        display:flex; flex-direction:column; justify-content:center;">
              <div style="font-size:18px; color:{color}; line-height:1;">{icon}</div>
              <div style="font-size:13px; font-weight:600; margin-top:4px; color:#e5e7eb;">{name}</div>
              <div style="font-size:11px; color:#9ca3af; margin-top:2px;">
                {status} · {dur:.0f} ms
              </div>
            </div>
            """,
            unsafe_allow_html=True,
        )
        # Arrow between cells
        if i < len(steps) - 1:
            arrow_col = cells[i * 2 + 1]
            arrow_col.markdown(
                """<div style="text-align:center; padding-top:30px; color:#6b7280;
                font-size:20px;">→</div>""",
                unsafe_allow_html=True,
            )
    if total_ms is not None:
        container.caption(f"Total: {total_ms:.0f} ms · {len(steps)} steps")


def render_step_details(steps: list[StepResult], container) -> None:
    for step in steps:
        icon = STATUS_ICON.get(step.status, "?")
        with container.expander(
            f"{icon}  {step.name} · {step.status} · {step.duration_ms:.0f} ms",
            expanded=(step.status == "error"),
        ):
            if step.error:
                st.error(step.error)
            if step.details:
                # Render scalar details as metrics, complex as JSON for clarity
                scalars = {k: v for k, v in step.details.items()
                           if isinstance(v, (int, float, str, bool)) or v is None}
                complex_items = {k: v for k, v in step.details.items() if k not in scalars}
                if scalars:
                    cols = st.columns(min(4, max(1, len(scalars))))
                    for j, (k, v) in enumerate(scalars.items()):
                        if isinstance(v, int):
                            shown = fmt_int(v)
                        elif isinstance(v, float):
                            shown = f"{v:.2f}"
                        else:
                            shown = str(v)
                        cols[j % len(cols)].metric(k, shown)
                if complex_items:
                    st.json(complex_items)


def render_step_timeline(steps: list[StepResult], container) -> None:
    """Horizontal bar chart of step durations — like a Gantt-ish view."""
    if not steps:
        return
    rows = []
    cursor = 0.0
    for step in steps:
        rows.append({
            "step": step.name,
            "start_ms": cursor,
            "end_ms": cursor + step.duration_ms,
            "duration_ms": step.duration_ms,
            "status": step.status,
        })
        cursor += step.duration_ms
    timeline_df = pd.DataFrame(rows)
    fig = px.bar(
        timeline_df, x="duration_ms", y="step", color="status",
        color_discrete_map={"ok": "#22c55e", "error": "#ef4444", "skipped": "#9ca3af"},
        orientation="h", text="duration_ms",
    )
    fig.update_traces(texttemplate="%{text:.0f} ms", textposition="outside")
    fig.update_layout(
        height=max(220, 40 * len(steps) + 80),
        margin=dict(l=0, r=20, t=10, b=0),
        yaxis=dict(autorange="reversed", title=""),
        xaxis=dict(title="duration (ms)"),
        showlegend=True,
    )
    container.plotly_chart(fig, width="stretch")


# ---------------------------------------------------------------------------
# Sidebar — global filters
# ---------------------------------------------------------------------------

st.sidebar.title("Car Analytics")

try:
    df_all = load_inputs()
except Exception as exc:  # noqa: BLE001
    st.error(f"Could not load data from MongoDB: {exc}")
    st.stop()

if df_all.empty:
    st.warning("No control_inputs documents found in MongoDB yet. "
               "Start a session from the steering script or the frontend "
               "and refresh.")
    st.stop()

sessions = load_session_summary(df_all)

with st.sidebar:
    st.caption(f"{len(df_all):,} samples · {len(sessions)} sessions")

    sources = sorted(df_all["source"].dropna().unique().tolist()) if "source" in df_all else []
    src_filter = st.multiselect("Source", sources, default=sources)

    time_window = st.radio(
        "Time window",
        ["All", "Last session", "Last hour", "Last 24h", "Last 7d"],
        index=0,
        help="Quick time filter applied across every tab.",
    )

    st.markdown("---")
    st.markdown("**Live mode**")
    auto_refresh = st.toggle("Auto-refresh", value=False,
                             help="Reloads data and reruns the page on an interval.")
    if auto_refresh:
        interval_s = st.slider("Interval (s)", min_value=2, max_value=60, value=5)
        if st_autorefresh is not None:
            # Returns a counter; when it changes Streamlit reruns.
            st_autorefresh(interval=interval_s * 1000, key="autorefresh_counter")
            st.cache_data.clear()
        else:
            st.warning("Install `streamlit-autorefresh` for live mode "
                       "(it's in requirements.txt — re-run `pip install -r requirements.txt`).")

    if st.button("Refresh data", width="stretch"):
        st.cache_data.clear()
        st.rerun()

# Apply global filters
df_filtered = df_all.copy()
if src_filter and "source" in df_filtered:
    df_filtered = df_filtered[df_filtered["source"].isin(src_filter)]

# Time-window filter
if time_window != "All" and not df_filtered.empty:
    now = pd.Timestamp.now(tz="UTC")
    if time_window == "Last hour":
        df_filtered = df_filtered[df_filtered.index >= now - pd.Timedelta(hours=1)]
    elif time_window == "Last 24h":
        df_filtered = df_filtered[df_filtered.index >= now - pd.Timedelta(hours=24)]
    elif time_window == "Last 7d":
        df_filtered = df_filtered[df_filtered.index >= now - pd.Timedelta(days=7)]
    elif time_window == "Last session" and not sessions.empty:
        latest_session = sessions.sort_values("start", ascending=False).iloc[0]["uuid"]
        df_filtered = df_filtered[df_filtered["sessionId"] == latest_session]

sessions_filtered = (
    sessions[sessions["uuid"].isin(df_filtered["sessionId"].dropna().unique())]
    if not sessions.empty else sessions
)


# ---------------------------------------------------------------------------
# Tabs
# ---------------------------------------------------------------------------

tab_overview, tab_session, tab_pipeline, tab_compare, tab_health, tab_about = st.tabs([
    "Overview",
    "Session explorer",
    "ETL pipeline",
    "Raw vs Logged",
    "System health",
    "About",
])


# ---------- Overview --------------------------------------------------------
with tab_overview:
    st.subheader("Overview")
    span_s = (df_filtered.index.max() - df_filtered.index.min()).total_seconds() if not df_filtered.empty else 0
    median_abs_latency = (df_filtered["latency_ms"].abs().median()
                          if not df_filtered.empty else float("nan"))
    valid_latency_count = (df_filtered["latency_ms"].notna().sum()
                           if not df_filtered.empty else 0)
    kpi_row([
        ("Samples", fmt_int(len(df_filtered))),
        ("Sessions", fmt_int(len(sessions_filtered))),
        ("Time span", f"{span_s/3600:.1f} h" if span_s else "—"),
        ("Idle rate", fmt_pct((df_filtered["idle"].mean() * 100) if not df_filtered.empty else float("nan"))),
        ("Median |latency|", fmt_ms(median_abs_latency)),
    ])
    if not df_filtered.empty and valid_latency_count < len(df_filtered):
        missing = len(df_filtered) - valid_latency_count
        st.caption(
            f"⚠️ {missing:,} of {len(df_filtered):,} samples have no valid "
            "`clientTimestamp` (keyboard fallback doesn't send one) — they are "
            "excluded from latency stats."
        )

    left, right = st.columns(2)
    with left:
        st.markdown("**Samples per session** — pick one to drill in")
        if not sessions_filtered.empty:
            # Single-select to drive the drill-down nav.
            picked_session_label = st.selectbox(
                "Open in Session Explorer",
                ["(none)"] + sessions_filtered.index.tolist(),
                key="overview_session_pick",
                label_visibility="collapsed",
            )
            if picked_session_label != "(none)":
                if st.button(f"➜ Send {picked_session_label} to Session Explorer",
                             key="drilldown_open"):
                    st.session_state["session_explorer_pick"] = [picked_session_label]
                    st.toast(f"{picked_session_label} pre-selected — "
                             "switch to the Session explorer tab.")

            chart = sessions_filtered.reset_index()[["session", "rows", "source"]]
            fig = px.bar(chart, x="session", y="rows", color="source",
                         labels={"rows": "samples"})
            fig.update_layout(height=300, margin=dict(l=0, r=0, t=10, b=0))
            st.plotly_chart(fig, width="stretch")
        else:
            st.info("No sessions in this date range.")

    with right:
        st.markdown("**Source mix**")
        if "source" in df_filtered:
            mix = df_filtered["source"].value_counts().reset_index()
            mix.columns = ["source", "samples"]
            fig = px.pie(mix, values="samples", names="source", hole=0.45)
            fig.update_layout(height=320, margin=dict(l=0, r=0, t=10, b=0))
            st.plotly_chart(fig, width="stretch")

    timeline_header, bucket_col = st.columns([3, 1])
    timeline_header.markdown("**Activity over time**")
    with bucket_col:
        bucket_choice = st.selectbox(
            "Bucket",
            ["1s", "10s", "1min", "5min", "15min", "1h"],
            index=2,
            key="overview_bucket",
            label_visibility="collapsed",
        )
    if not df_filtered.empty:
        bucket = df_filtered.resample(bucket_choice).size().rename("samples").reset_index()
        fig = px.area(bucket, x="recorded_at", y="samples")
        fig.update_layout(height=260, margin=dict(l=0, r=0, t=10, b=0))
        st.plotly_chart(fig, width="stretch")

    st.download_button(
        "Download filtered slice (CSV)",
        data=df_filtered.reset_index()[
            [c for c in ["recorded_at", "sessionId", "source",
                         "steering", "throttle", "brake", "latency_ms", "idle"]
             if c in df_filtered.reset_index().columns]
        ].to_csv(index=False).encode("utf-8"),
        file_name="filtered_samples.csv",
        mime="text/csv",
    )


# ---------- Session explorer ------------------------------------------------
CHANNEL_COLORS = {"steering": "#1f77b4", "throttle": "#2ca02c", "brake": "#d62728"}

with tab_session:
    st.subheader("Session explorer")
    if sessions_filtered.empty:
        st.info("No sessions to display. Adjust filters.")
    else:
        labels = sessions_filtered.index.tolist()

        ctrl_col, chan_col = st.columns([2, 1])
        with ctrl_col:
            # Honor drill-down navigation from the Overview tab.
            default_sessions = st.session_state.pop("session_explorer_pick", None)
            if default_sessions:
                default_sessions = [s for s in default_sessions if s in labels]
            selected = st.multiselect(
                "Sessions (pick one or compare multiple)",
                labels,
                default=default_sessions or [labels[0]],
                format_func=lambda s: f"{s} · {sessions_filtered.loc[s, 'start']:%Y-%m-%d %H:%M} · "
                                      f"{int(sessions_filtered.loc[s, 'rows'])} samples",
            )
        with chan_col:
            st.caption("Channels")
            show_steering = st.checkbox("steering", value=True, key="se_steer")
            show_throttle = st.checkbox("throttle", value=True, key="se_throt")
            show_brake = st.checkbox("brake", value=True, key="se_brake")

        active_channels = [c for c, on in
                           [("steering", show_steering), ("throttle", show_throttle), ("brake", show_brake)]
                           if on]

        if not selected:
            st.info("Pick at least one session.")
        else:
            # KPIs only when a single session is picked (otherwise they'd be ambiguous).
            if len(selected) == 1:
                row = sessions_filtered.loc[selected[0]]
                kpi_row([
                    ("Duration", f"{row['duration_s']:.0f} s"),
                    ("Effective Hz", f"{row['effective_hz']:.1f}"),
                    ("Avg throttle", f"{row['avg_throttle']:.2f}"),
                    ("Max brake", f"{row['max_brake']:.2f}"),
                    ("Idle %", fmt_pct(row["idle_pct"])),
                    ("p95 latency", fmt_ms(row["p95_latency_ms"])),
                ])

            picked_uuids = [sessions_filtered.loc[s, "uuid"] for s in selected]
            session_df = df_filtered[df_filtered["sessionId"].isin(picked_uuids)].copy()

            if not session_df.empty and active_channels:
                # Map UUID back to its human-readable session label
                label_for = {sessions_filtered.loc[s, "uuid"]: s for s in selected}
                session_df["session_label"] = session_df["sessionId"].map(label_for)

                plot_df = session_df.reset_index().melt(
                    id_vars=["recorded_at", "session_label"],
                    value_vars=active_channels,
                    var_name="channel",
                    value_name="value",
                )

                if len(selected) == 1:
                    fig = px.line(plot_df, x="recorded_at", y="value", color="channel",
                                  color_discrete_map=CHANNEL_COLORS)
                else:
                    # Multi-session: colour by channel, dash by session to disambiguate
                    fig = px.line(plot_df, x="recorded_at", y="value",
                                  color="channel", line_dash="session_label",
                                  color_discrete_map=CHANNEL_COLORS)
                fig.update_layout(height=380, margin=dict(l=0, r=0, t=10, b=0))
                st.plotly_chart(fig, width="stretch")

                if len(selected) == 1:
                    c1, c2 = st.columns(2)
                    with c1:
                        st.markdown("**Steering distribution**")
                        fig = px.histogram(session_df.reset_index(), x="steering", nbins=40)
                        fig.update_layout(height=260, margin=dict(l=0, r=0, t=10, b=0))
                        st.plotly_chart(fig, width="stretch")
                    with c2:
                        st.markdown("**Throttle vs brake (overlap zone in red)**")
                        scatter = session_df.reset_index()
                        scatter["overlap"] = scatter["throttle_brake_overlap"].astype(bool)
                        fig = px.scatter(scatter, x="throttle", y="brake", color="overlap",
                                         color_discrete_map={True: "#d62728", False: "#7f7f7f"},
                                         opacity=0.5)
                        fig.update_layout(height=260, margin=dict(l=0, r=0, t=10, b=0))
                        st.plotly_chart(fig, width="stretch")

                csv_cols = ["sessionId", "session_label", "source",
                            "steering", "throttle", "brake", "latency_ms"]
                exportable = session_df.reset_index()[[c for c in csv_cols
                                                       if c in session_df.reset_index().columns]]
                st.download_button(
                    "Download these rows as CSV",
                    data=exportable.to_csv(index=False).encode("utf-8"),
                    file_name="session_samples.csv",
                    mime="text/csv",
                )
            elif not active_channels:
                st.info("Tick at least one channel to see the time-series.")


# ---------- ETL pipeline ----------------------------------------------------
with tab_pipeline:
    st.subheader("ETL pipeline")
    st.caption("E + T runs against MongoDB. Counts below come from "
               "`etl.transform.detect_outliers` on the filtered slice.")

    st.markdown("**Run pipeline**")
    run_col, parquet_col, _ = st.columns([1, 1, 2])
    with parquet_col:
        export_parquet = st.checkbox("Also export parquet snapshot", value=False,
                                     help="Writes data/control_inputs.parquet")
    trigger = run_col.button("Run pipeline now", type="primary")

    PIPELINE_STEPS = [
        "Load config", "Connect to MongoDB", "Extract documents",
        "Transform + enrich", "Quality check", "Session summary", "Export parquet",
    ]

    flow_slot = st.empty()
    details_slot = st.container()
    timeline_slot = st.container()

    if trigger:
        # Snapshot collection counts before the run so we can show a real delta.
        # The ETL pipeline itself doesn't write to Mongo — the backend does —
        # but between runs of this button new samples typically arrive, and
        # this surfaces that activity.
        baseline_counts = st.session_state.get("last_pipeline_counts")
        pre_counts = count_collections()

        # Initial "all pending" render so the user sees the planned steps.
        planned = [{"name": n, "status": "pending", "duration_ms": 0.0}
                   for n in PIPELINE_STEPS]
        with flow_slot.container():
            render_pipeline_flow(planned, None, st.container())

        completed: list[StepResult] = []

        def on_step(step: StepResult) -> None:
            completed.append(step)
            # Build live view: completed steps + remaining as pending
            done_names = {s.name for s in completed}
            live = list(completed) + [
                {"name": n, "status": "pending", "duration_ms": 0.0}
                for n in PIPELINE_STEPS if n not in done_names
            ]
            with flow_slot.container():
                render_pipeline_flow(live, None, st.container())

        with st.spinner("Running ETL pipeline..."):
            run: PipelineRun = run_pipeline(export_parquet=export_parquet,
                                            on_step=on_step)

        # Final render with totals
        with flow_slot.container():
            render_pipeline_flow(run.steps, run.total_ms, st.container())

        if run.status == "ok":
            st.success(f"Pipeline finished cleanly in {run.total_ms:.0f} ms.")
        else:
            st.error(f"Pipeline failed after {run.total_ms:.0f} ms.")

        # Persist this run's counts for the next click's delta.
        st.session_state["last_pipeline_counts"] = pre_counts

        st.markdown("**Collection deltas**")
        st.caption(
            "Live document counts. *vs. previous run* compares against the snapshot "
            "taken at the last time this button was pressed; *during this run* is "
            "almost always 0 because the ETL doesn't write to Mongo — the backend "
            "does, and only while a session is active."
        )
        post_counts = count_collections()
        delta_cols = st.columns(2)
        for col, label in zip(delta_cols, ["control_inputs", "controller_broadcasts"]):
            with col:
                total = post_counts.get(label, -1)
                if total < 0:
                    st.metric(label, "unavailable")
                    continue
                prev_session = baseline_counts.get(label) if baseline_counts else None
                during_run = post_counts[label] - pre_counts.get(label, post_counts[label])
                delta_label = (f"+{post_counts[label] - prev_session:,} vs prev run"
                               if prev_session is not None else "first run this session")
                st.metric(label, fmt_int(total), delta=delta_label)
                st.caption(f"During this run: **+{during_run:,}** rows")

        with timeline_slot:
            st.markdown("**Step timeline**")
            render_step_timeline(run.steps, st.container())

        with details_slot:
            st.markdown("**Step details**")
            render_step_details(run.steps, st.container())

        st.cache_data.clear()
        st.caption("Cache cleared — switch tabs or hit Refresh in the sidebar "
                   "to see updated counts.")

    st.markdown("---")
    st.markdown("**Quality snapshot** (computed live on the filtered slice)")

    quality = detect_outliers(df_filtered) if not df_filtered.empty else {}
    if quality:
        ordered = [
            ("rows_total", "Total rows"),
            ("rows_missing_session", "Missing sessionId"),
            ("rows_negative_latency", "Negative latency"),
            ("rows_huge_latency", "Latency > 5s"),
            ("rows_steering_out_of_range", "Steering out of [-1, 1]"),
            ("rows_throttle_out_of_range", "Throttle out of [0, 1]"),
            ("rows_brake_out_of_range", "Brake out of [0, 1]"),
            ("rows_idle", "Idle samples"),
        ]
        cols = st.columns(4)
        for i, (k, label) in enumerate(ordered):
            cols[i % 4].metric(label, fmt_int(quality.get(k, 0)))

    st.markdown("---")
    st.markdown("**Derived feature distributions**")
    c1, c2, c3 = st.columns(3)
    with c1:
        fig = px.histogram(df_filtered.reset_index(), x="movement", nbins=40,
                           title="movement = throttle − brake")
        fig.update_layout(height=260, margin=dict(l=0, r=0, t=30, b=0))
        st.plotly_chart(fig, width="stretch")
    with c2:
        fig = px.histogram(df_filtered.reset_index(), x="abs_steering", nbins=40,
                           title="|steering|")
        fig.update_layout(height=260, margin=dict(l=0, r=0, t=30, b=0))
        st.plotly_chart(fig, width="stretch")
    with c3:
        fig = px.histogram(df_filtered.reset_index(), x="steering_delta", nbins=40,
                           title="Δ steering (smoothness)")
        fig.update_layout(height=260, margin=dict(l=0, r=0, t=30, b=0))
        st.plotly_chart(fig, width="stretch")

    st.markdown("---")
    st.markdown("**Session summary table**")
    st.dataframe(sessions_filtered, width="stretch")


# ---------- Raw vs Logged ---------------------------------------------------
with tab_compare:
    st.subheader("Raw broadcast (→ ESP32) vs Logged samples")
    st.caption("The backend broadcasts raw axes to the ESP32 over WebSocket, then "
               "quantizes / dedups / applies pedal hysteresis before persisting to "
               "the `control_inputs` collection. This view shows what the filtering "
               "layer drops.")

    if sessions_filtered.empty:
        st.info("No sessions to compare.")
    else:
        labels = sessions_filtered.index.tolist()
        ctrl_col, chan_col = st.columns([2, 1])
        with ctrl_col:
            choice = st.selectbox("Session to compare",
                                  labels,
                                  format_func=lambda s: f"{s} · {sessions_filtered.loc[s, 'start']:%Y-%m-%d %H:%M}",
                                  key="compare_session")
        with chan_col:
            st.caption("Channels")
            cmp_steering = st.checkbox("steering", value=True, key="cmp_steer")
            cmp_throttle = st.checkbox("throttle", value=True, key="cmp_throt")
            cmp_brake = st.checkbox("brake", value=True, key="cmp_brake")
        session_uuid = sessions_filtered.loc[choice, "uuid"]

        try:
            raw_df = load_broadcasts_for_session(session_uuid)
        except Exception as exc:  # noqa: BLE001
            st.error(f"Could not load broadcasts: {exc}")
            raw_df = pd.DataFrame()

        logged_df = load_inputs_for_session(session_uuid)

        if raw_df.empty:
            st.warning(
                "No raw broadcast documents for this session. The backend feature "
                "that captures them may not have been deployed yet, or this session "
                "predates it. Newer sessions should show data."
            )
        else:
            ratio = (len(logged_df) / len(raw_df) * 100) if len(raw_df) else float("nan")
            kpi_row([
                ("Raw frames (→ ESP32)", fmt_int(len(raw_df))),
                ("Logged samples", fmt_int(len(logged_df))),
                ("Persist ratio", fmt_pct(ratio)),
                ("Frames dropped by filter", fmt_int(len(raw_df) - len(logged_df))),
            ])

            if cmp_steering:
                st.markdown("**Steering: raw broadcast vs persisted log**")
                fig = go.Figure()
                fig.add_trace(go.Scatter(x=raw_df.index, y=raw_df["raw_steering"],
                                         name="raw → ESP", mode="lines",
                                         line=dict(color="#aaaaaa", width=1)))
                if not logged_df.empty:
                    fig.add_trace(go.Scatter(x=logged_df.index, y=logged_df["steering"],
                                             name="logged",
                                             mode="markers+lines",
                                             marker=dict(size=4, color="#1f77b4")))
                fig.update_layout(height=320, margin=dict(l=0, r=0, t=10, b=0),
                                  yaxis_title="steering")
                st.plotly_chart(fig, width="stretch")

            if cmp_throttle or cmp_brake:
                cols = st.columns(2 if (cmp_throttle and cmp_brake) else 1)
                idx = 0
                if cmp_throttle:
                    with cols[idx]:
                        st.markdown("**Throttle**")
                        fig = go.Figure()
                        fig.add_trace(go.Scatter(x=raw_df.index, y=raw_df["raw_throttle"],
                                                 name="raw → ESP", mode="lines",
                                                 line=dict(color="#aaaaaa", width=1)))
                        if not logged_df.empty:
                            fig.add_trace(go.Scatter(x=logged_df.index, y=logged_df["throttle"],
                                                     name="logged",
                                                     mode="markers", marker=dict(size=4, color="#2ca02c")))
                        fig.update_layout(height=260, margin=dict(l=0, r=0, t=10, b=0))
                        st.plotly_chart(fig, width="stretch")
                    idx += 1
                if cmp_brake:
                    with cols[idx]:
                        st.markdown("**Brake**")
                        fig = go.Figure()
                        fig.add_trace(go.Scatter(x=raw_df.index, y=raw_df["raw_brake"],
                                                 name="raw → ESP", mode="lines",
                                                 line=dict(color="#aaaaaa", width=1)))
                        if not logged_df.empty:
                            fig.add_trace(go.Scatter(x=logged_df.index, y=logged_df["brake"],
                                                     name="logged",
                                                     mode="markers", marker=dict(size=4, color="#d62728")))
                        fig.update_layout(height=260, margin=dict(l=0, r=0, t=10, b=0))
                        st.plotly_chart(fig, width="stretch")

            st.markdown("**Sample rate comparison**")
            raw_rate = raw_df.resample("1s").size().rename("raw")
            log_rate = (logged_df.resample("1s").size().rename("logged")
                        if not logged_df.empty else pd.Series(dtype=int, name="logged"))
            rate = pd.concat([raw_rate, log_rate], axis=1).fillna(0).reset_index()
            rate_long = rate.melt(id_vars="recorded_at", var_name="stream", value_name="hz")
            fig = px.line(rate_long, x="recorded_at", y="hz", color="stream",
                          color_discrete_map={"raw": "#aaaaaa", "logged": "#1f77b4"})
            fig.update_layout(height=240, margin=dict(l=0, r=0, t=10, b=0),
                              yaxis_title="samples / second")
            st.plotly_chart(fig, width="stretch")

            dl_a, dl_b = st.columns(2)
            with dl_a:
                st.download_button(
                    "Download raw broadcasts (CSV)",
                    data=raw_df.reset_index().to_csv(index=False).encode("utf-8"),
                    file_name="raw_broadcasts.csv",
                    mime="text/csv",
                )
            with dl_b:
                if not logged_df.empty:
                    st.download_button(
                        "Download logged samples (CSV)",
                        data=logged_df.reset_index()[
                            [c for c in ["recorded_at", "sessionId", "source",
                                         "steering", "throttle", "brake", "latency_ms"]
                             if c in logged_df.reset_index().columns]
                        ].to_csv(index=False).encode("utf-8"),
                        file_name="logged_samples.csv",
                        mime="text/csv",
                    )


# ---------- System health ---------------------------------------------------
with tab_health:
    st.subheader("System health")
    st.caption(
        "**Latency** here = server `recordedAt` − client `clientTimestamp`. "
        "It's a mix of clock-skew + network + processing, not pure network RTT. "
        "We show **|latency|** (absolute value) because the raw signed delta is "
        "often negative when the client clock leads the server clock — that's "
        "real but visually confusing. The *Raw negatives* counter shows how many "
        "rows had a negative delta if you want to spot clock-skew issues. For "
        "real performance health, look at the sample-rate / inter-sample gap "
        "panels below — those are clock-independent."
    )
    if df_filtered.empty:
        st.info("No samples in filter range.")
    else:
        kpi_row([
            ("Median |latency|", fmt_ms(df_filtered["latency_ms"].abs().median())),
            ("p95 |latency|", fmt_ms(df_filtered["latency_ms"].abs().quantile(0.95))),
            ("p99 |latency|", fmt_ms(df_filtered["latency_ms"].abs().quantile(0.99))),
            ("Raw negatives", fmt_int((df_filtered["latency_ms"] < 0).sum())),
        ])

        c1, c2 = st.columns(2)
        with c1:
            st.markdown("**Latency distribution (ms)**")
            clipped = df_filtered["latency_ms"].clip(lower=-50, upper=2000)
            fig = px.histogram(clipped.reset_index(name="latency_ms"),
                               x="latency_ms", nbins=60)
            fig.update_layout(height=300, margin=dict(l=0, r=0, t=10, b=0))
            st.plotly_chart(fig, width="stretch")
        with c2:
            st.markdown("**Inter-sample gap (ms, per session)**")
            gap = df_filtered["sample_dt_ms"].dropna()
            gap = gap[(gap > 0) & (gap < 5000)]
            fig = px.histogram(gap.reset_index(name="sample_dt_ms"),
                               x="sample_dt_ms", nbins=60)
            fig.update_layout(height=300, margin=dict(l=0, r=0, t=10, b=0))
            st.plotly_chart(fig, width="stretch")

        st.markdown("**|latency| over time (rolling p95, 1-min window)**")
        rolled = df_filtered["latency_ms"].abs().rolling("60s").quantile(0.95)
        fig = px.line(rolled.reset_index(name="p95_abs_latency_ms"),
                      x="recorded_at", y="p95_abs_latency_ms")
        fig.update_layout(height=260, margin=dict(l=0, r=0, t=10, b=0))
        st.plotly_chart(fig, width="stretch")

        st.markdown("**Effective sample rate per session**")
        if not sessions_filtered.empty:
            fig = px.bar(sessions_filtered.reset_index(), x="session", y="effective_hz",
                         color="source")
            fig.update_layout(height=260, margin=dict(l=0, r=0, t=10, b=0),
                              yaxis_title="Hz")
            st.plotly_chart(fig, width="stretch")


# ---------- About -----------------------------------------------------------
with tab_about:
    st.subheader("About this dashboard")

    st.markdown(
        """
This dashboard is the **analytics window** on the remote-controlled-car
project. It reads from MongoDB and lets you explore how a physical steering
wheel (or keyboard fallback) is driving an ESP32-controlled car.

#### Data flow

```
┌──────────────────┐   POST     ┌────────────────────┐    WebSocket    ┌────────┐
│ steering-input/  │ ─────────► │  Spring backend     │ ──────────────► │ ESP32  │
│ main.py (wheel)  │            │  /api/controller    │                 │ (car)  │
│ or frontend kbd  │            │                     │                 └────────┘
└──────────────────┘            │  ControlInputLogger │
                                │     ▼               │
                                │  control_inputs     │  ←  filtered: quantize 0.01,
                                │                     │       pedal hysteresis,
                                │                     │       200ms min-interval dedup
                                │                     │
                                │  BroadcastLogger    │
                                │     ▼               │
                                │  controller_broadcasts  ←  raw, every frame
                                └────────────────────┘
                                          │
                                          ▼
                              ┌──────────────────────┐
                              │ etl pipeline (E + T) │
                              └──────────────────────┘
                                          │
                                          ▼
                                  this Streamlit app
```
        """
    )

    st.markdown("#### What each tab does")
    st.markdown(
        """
- **Overview** — global KPIs (samples, sessions, idle %), sample counts per
  session, source mix (`wheel` vs `keyboard`), activity over time.
- **Session explorer** — pick a session, view its steering / throttle / brake
  time-series, steering distribution, and throttle-vs-brake overlap scatter
  (overlap = beginner signal: pressing both pedals at once).
- **ETL pipeline** — *Run pipeline now* re-extracts from Mongo, transforms,
  computes quality counts and session summary. Each step is a colored block
  with timing, plus a Gantt-like timeline and per-step expandable details.
- **Raw vs Logged** — compares what the backend *broadcast to the ESP32*
  (raw axes, every frame, no filter) against what got *persisted after
  filtering*. Quantifies how much the dedup/quantize/hysteresis layer drops.
- **System health** — latency p50/p95/p99 (server clock − client clock),
  inter-sample gap histogram, rolling p95 latency over time, per-session
  effective sample rate (Hz).
        """
    )

    st.markdown("#### ETL pipeline steps")
    st.markdown(
        """
| Step | What it does |
|---|---|
| **Load config** | Reads `.env` (`MONGO_URI`, db & collection names). |
| **Connect to MongoDB** | Pings the cluster, fetches `estimated_document_count`. |
| **Extract documents** | Streams the full `control_inputs` collection in batches, sorted by `recordedAt`. |
| **Transform + enrich** | Parses timestamps to UTC, normalizes session UUIDs, derives `movement`, `idle`, `latency_ms`, `abs_steering`, `throttle_brake_overlap`, `steering_delta`, `throttle_delta`, `sample_dt_ms`. |
| **Quality check** | Counts suspicious rows (negative latency, out-of-range axes, missing session, idle). |
| **Session summary** | Per-session aggregates: duration, effective Hz, idle %, p95 latency, smoothness, aggression. |
| **Export parquet** | Optional. Writes `data/control_inputs.parquet` for offline analysis. |
        """
    )

    st.markdown("#### The filtering layer (why Raw vs Logged exists)")
    st.markdown(
        """
The backend doesn't naively store every frame. Before writing to
`control_inputs` it:

1. **Quantizes** steering/throttle/brake to a 0.01 grid — removes float noise.
2. **Applies pedal hysteresis** — a pedal value has to climb above
   `wake-threshold` (0.08) to leave the rest state, and drop below
   `sleep-threshold` (0.05) to return. Prevents flicker at the deadzone.
3. **Dedups** — if the quantized triple is unchanged from the previous sample
   *and* less than `min-interval-ms` (200ms) has passed, the frame is dropped.

So `control_inputs` is **eventful**: each row represents a meaningful change.
`controller_broadcasts` is **continuous**: every WebSocket frame, exactly as
the ESP32 saw it. Comparing the two tells you how much information the
filtering removes — and confirms the ESP isn't being starved.
        """
    )

    st.markdown("#### Derived columns reference")
    st.markdown(
        """
| Column | Meaning |
|---|---|
| `movement` | `throttle − brake`. Positive = forward, negative = braking. |
| `idle` | All three axes at neutral. Driver isn't doing anything. |
| `latency_ms` | `recordedAt − clientTimestamp`. Network + backend processing delay. |
| `abs_steering` | Magnitude of steering input regardless of direction. |
| `throttle_brake_overlap` | Both > 5% — beginner indicator. |
| `steering_delta` | Per-session diff between consecutive steering samples — smoothness. |
| `sample_dt_ms` | Time gap from previous sample in same session — sample-rate health. |
        """
    )

    st.markdown("#### Config")
    cfg = get_config()
    st.json({
        "mongo_db": cfg.mongo_db,
        "mongo_collection": cfg.mongo_collection,
        "mongo_broadcast_collection": cfg.mongo_broadcast_collection,
        "project_root": str(cfg.project_root),
    })
