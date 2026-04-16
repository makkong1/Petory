#!/bin/bash

# 위험한 명령어가 실행되지 않도록 차단하는 스크립트 (Pre-command 훅)

COMMAND=$1

echo "Checking dangerous commands for: $COMMAND"

# 차단할 키워드 목록
DANGEROUS_KEYWORDS=("rm -rf /" "drop database" "truncate table")

for keyword in "${DANGEROUS_KEYWORDS[@]}"; do
    if [[ "$COMMAND" == *"$keyword"* ]]; then
        echo "Error: Blocked dangerous command: $COMMAND"
        exit 1
    fi
done

exit 0
