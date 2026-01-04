# 트러블슈팅

## 📋 일반적인 문제 해결

### 컨테이너 시작 실패

**문제**: 컨테이너가 시작되지 않음
**해결**:
```bash
docker-compose -f docker-compose.prod.yml logs backend
docker ps -a
```

### 포트 충돌

**문제**: 포트가 이미 사용 중
**해결**:
```bash
sudo netstat -tulpn | grep :8080
# 포트 변경 또는 기존 프로세스 종료
```

### 데이터베이스 연결 오류

**문제**: Backend에서 MySQL 연결 실패
**해결**:
```bash
docker exec petory-mysql-prod mysqladmin ping
docker network inspect petory_petory-network
```

### 메모리 부족

**문제**: Out of Memory 에러
**해결**:
- Docker 리소스 제한 확인
- JVM 메모리 설정 조정 (`JAVA_OPTS`)

---

자세한 내용은 각 도메인별 트러블슈팅 문서를 참고하세요.

