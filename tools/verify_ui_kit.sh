#!/usr/bin/env bash
# Compiler-free verification for the shared UI kit and project structure.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/tools/static_checks.py"
