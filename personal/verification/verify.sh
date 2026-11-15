#!/usr/bin/env bash
set -euo pipefail
VERIFY_ROOT=$(cd -- "$(dirname -- "$0")/../.." && pwd)
exec "$VERIFY_ROOT/tools/venv/bin/python" -I "$VERIFY_ROOT/personal/verification/verify.py" "$@"
