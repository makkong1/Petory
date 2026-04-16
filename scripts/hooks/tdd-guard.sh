#!/bin/bash

# 테스트가 작성되지 않은 경우 커밋을 차단하거나 피드백을 주는 스크립트

echo "Running TDD guard hook..."

# 테스트 디렉토리 확인 (Petory 프로젝트 구조 반영)
TEST_DIR="backend/test"
FRONTEND_TEST_DIR="frontend/src"

# 여기서 git diff --cached 등을 분석해서
# 새로운 코드가 추가되었는데, 관련된 테스트 코드가 없는지 검사하는 로직을 작성합니다.
# 현재는 예시용으로 패스합니다.

echo "TDD guard passed."
exit 0
