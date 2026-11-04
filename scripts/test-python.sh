#!/bin/bash
# Unit tests for the local runner harness (app/src/main/python/titan_runner.py), on the Mac's python3.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -m unittest discover -s app/src/test/python -p 'test_*.py' -v
