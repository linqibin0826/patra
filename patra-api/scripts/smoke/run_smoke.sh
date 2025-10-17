#!/usr/bin/env bash
set -euo pipefail

# Simple runner for Phase 6 smoke tests.
# Usage: scripts/smoke/run_smoke.sh [maven-args]

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd)

echo "[smoke] Running Expr smoke tests (requires registry service and dev DB)..."
RUN_SMOKE=1 mvn -q -f "$ROOT_DIR/pom.xml" -pl patra-ingest/patra-ingest-boot -Dtest=ExprSmokeTest test "$@"

echo "[smoke] Done. Check logs for compiler DEBUG lines and INFO query hashes."
