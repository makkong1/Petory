#!/usr/bin/env bash
# staged 파일 중 docs/**/*.md가 있으면 docs/INDEX.md를 재생성하고 커밋에 포함시킨다.
# 설치: .git/hooks/pre-commit에서 이 스크립트를 호출 (install-hooks.sh 참고)
set -e

if git diff --cached --name-only --diff-filter=ACM | grep -qE '^docs/.*\.md$'; then
    python3 scripts/docs_index.py
    git add docs/INDEX.md
fi
