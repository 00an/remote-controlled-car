# Run the Streamlit dashboard. Reuses .venv created by run_pipeline.ps1.
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path ".venv")) {
    python -m venv .venv
}

.\.venv\Scripts\Activate.ps1
pip install -q -r requirements.txt

# Streamlit defaults to headless when launched from a non-TTY context (which
# is how PowerShell scripts run). Force-disable headless and pre-open the
# browser so the dashboard actually pops up.
$port = 8501
Start-Process "http://localhost:$port"
streamlit run dashboard/app.py --server.headless=false --server.port=$port --browser.gatherUsageStats=false
