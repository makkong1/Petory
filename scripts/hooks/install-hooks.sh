#!/usr/bin/env bash
# .git/hooks/pre-commit을 설치한다 (git으로 추적 안 되는 디렉토리라 클론 후 한 번 실행 필요).
set -e

HOOK_DIR="$(git rev-parse --git-dir)/hooks"
mkdir -p "$HOOK_DIR"

cat > "$HOOK_DIR/pre-commit" <<'EOF'
#!/bin/sh
exec bash scripts/hooks/pre-commit-docs-index.sh
EOF

chmod +x "$HOOK_DIR/pre-commit"
echo "설치 완료: $HOOK_DIR/pre-commit"
