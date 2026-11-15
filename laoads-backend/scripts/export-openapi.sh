#!/usr/bin/env bash
# Export OpenAPI spec từ runtime (app phải đang chạy ở PORT). springdoc chạy được kể cả khi DB down.
#
# Usage:
#   ./scripts/export-openapi.sh [PORT] [OUT_FILE]
# Default: PORT=8095 OUT_FILE=<repo>/docs/openapi.yaml  (dev chạy 8095, uat/prod 8080)
#
# Sau khi export, regen type FE (gen:api đọc ../docs/openapi.yaml):
#   cd ../laoads-web && npm run gen:api
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PORT="${1:-8095}"
OUT="${2:-$REPO_ROOT/docs/openapi.yaml}"

mkdir -p "$(dirname "$OUT")"

# health trả 503 khi DB down → chỉ cần server phản hồi (không dùng -f)
if ! curl -s -o /dev/null "http://localhost:${PORT}/api/actuator/health"; then
  echo "ERROR: app không chạy ở port ${PORT}. Start app trước:" >&2
  echo "  ./mvnw -pl laoads-web -am spring-boot:run" >&2
  exit 1
fi

URL="http://localhost:${PORT}/api/v3/api-docs.yaml"
echo "Fetch: $URL"
curl -sf "$URL" -o "$OUT"
echo "Wrote: $OUT ($(wc -c < "$OUT") bytes)"
