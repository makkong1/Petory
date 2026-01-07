# Nginx 설정

## 📋 개요

Nginx를 이용한 리버스 프록시 및 정적 파일 서빙 설정 방법을 설명합니다.

---

## 📁 Nginx 설정 파일 구조

```
docker/nginx/
├── nginx.conf              # 메인 Nginx 설정
├── default.conf            # 서버 블록 설정
└── ssl/                    # SSL 인증서 디렉토리
    ├── cert.pem
    └── key.pem
```

---

## ⚙️ 메인 설정: `nginx.conf`

```nginx
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;

events {
    worker_connections 1024;
    use epoll;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    log_format main '$remote_addr - $remote_user [$time_local] "$request" '
                    '$status $body_bytes_sent "$http_referer" '
                    '"$http_user_agent" "$http_x_forwarded_for"';

    access_log /var/log/nginx/access.log main;

    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    types_hash_max_size 2048;
    client_max_body_size 20M;

    # Gzip 압축
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css text/xml text/javascript 
               application/json application/javascript application/xml+rss 
               application/rss+xml font/truetype font/opentype 
               application/vnd.ms-fontobject image/svg+xml;

    # 캐싱 설정
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:10m max_size=100m 
                     inactive=60m use_temp_path=off;

    include /etc/nginx/conf.d/*.conf;
}
```

---

## 🌐 서버 블록 설정: `default.conf`

```nginx
# HTTP 서버 (HTTPS로 리다이렉트)
server {
    listen 80;
    server_name your-domain.com www.your-domain.com;

    # Let's Encrypt 인증서 갱신용
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    # HTTPS로 리다이렉트
    location / {
        return 301 https://$server_name$request_uri;
    }
}

# HTTPS 서버
server {
    listen 443 ssl http2;
    server_name your-domain.com www.your-domain.com;

    # SSL 인증서 설정
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    
    # SSL 보안 설정
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # 보안 헤더
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;

    # 로그 설정
    access_log /var/log/nginx/access.log main;
    error_log /var/log/nginx/error.log warn;

    # Frontend (React) - 정적 파일 서빙
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;

        # 캐싱 설정
        location ~* \.(jpg|jpeg|png|gif|ico|css|js|svg|woff|woff2|ttf|eot)$ {
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }

    # Backend API 프록시
    location /api {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;

        # 헤더 설정
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $server_name;

        # 타임아웃 설정
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;

        # 버퍼 설정
        proxy_buffering on;
        proxy_buffer_size 4k;
        proxy_buffers 8 4k;
        proxy_busy_buffers_size 8k;

        # 캐싱 (GET 요청만)
        proxy_cache api_cache;
        proxy_cache_valid 200 5m;
        proxy_cache_valid 404 1m;
        proxy_cache_key "$scheme$request_method$host$request_uri";
        proxy_cache_bypass $http_cache_control;
        add_header X-Cache-Status $upstream_cache_status;
    }

    # WebSocket 프록시 (채팅 기능)
    location /ws {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;

        # WebSocket 헤더
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 타임아웃 (더 길게)
        proxy_connect_timeout 7d;
        proxy_send_timeout 7d;
        proxy_read_timeout 7d;
    }

    # 파일 업로드
    location /api/uploads {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        
        # 파일 업로드 크기 제한
        client_max_body_size 20M;
    }

    # Health Check (캐싱 제외)
    location /api/actuator/health {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_cache off;
        access_log off;
    }
}
```

---

## 🔒 SSL/TLS 인증서 설정

### Let's Encrypt 자동 갱신

#### `docker-compose.prod.yml`에 certbot 추가

```yaml
services:
  certbot:
    image: certbot/certbot
    container_name: petory-certbot
    volumes:
      - ./docker/nginx/ssl:/etc/letsencrypt
      - ./docker/nginx/certbot:/var/www/certbot
    command: certonly --webroot --webroot-path=/var/www/certbot --email your-email@example.com --agree-tos --no-eff-email -d your-domain.com -d www.your-domain.com

  nginx:
    volumes:
      - ./docker/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./docker/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
      - ./docker/nginx/ssl:/etc/nginx/ssl:ro
      - ./docker/nginx/certbot:/var/www/certbot:ro
    depends_on:
      - certbot
```

#### 인증서 갱신 스크립트

```bash
#!/bin/bash
# renew-ssl.sh

docker-compose -f docker-compose.prod.yml run --rm certbot renew
docker-compose -f docker-compose.prod.yml restart nginx
```

#### Crontab 설정 (월 1회 갱신)

```bash
0 3 1 * * /opt/petory/renew-ssl.sh >> /var/log/ssl-renewal.log 2>&1
```

---

## 🚦 Rate Limiting 설정

### `nginx.conf`에 추가

```nginx
http {
    # Rate limiting zones
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;
    limit_req_zone $binary_remote_addr zone=auth_limit:10m rate=5r/m;

    # Connection limiting
    limit_conn_zone $binary_remote_addr zone=conn_limit:10m;

    # ...

    server {
        # API Rate Limiting
        location /api {
            limit_req zone=api_limit burst=20 nodelay;
            limit_conn conn_limit 10;
            
            # ...
        }

        # 인증 엔드포인트 더 엄격한 제한
        location ~ ^/api/(auth|oauth2)/ {
            limit_req zone=auth_limit burst=5 nodelay;
            limit_conn conn_limit 5;
            
            # ...
        }
    }
}
```

---

## 📊 로그 설정

### 로그 포맷 커스터마이징

```nginx
log_format detailed '$remote_addr - $remote_user [$time_local] '
                    '"$request" $status $body_bytes_sent '
                    '"$http_referer" "$http_user_agent" '
                    'rt=$request_time uct="$upstream_connect_time" '
                    'uht="$upstream_header_time" urt="$upstream_response_time"';

access_log /var/log/nginx/access.log detailed;
```

### 로그 로테이션

```bash
# /etc/logrotate.d/nginx
/var/log/nginx/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 nginx adm
    sharedscripts
    postrotate
        [ -f /var/run/nginx.pid ] && kill -USR1 `cat /var/run/nginx.pid`
    endscript
}
```

---

## 🔍 모니터링 및 헬스체크

### Nginx Status 모듈 활성화

```nginx
server {
    location /nginx_status {
        stub_status on;
        access_log off;
        allow 127.0.0.1;
        deny all;
    }
}
```

### 응답 예시

```
Active connections: 3
server accepts handled requests
 45 45 123
Reading: 0 Writing: 1 Waiting: 2
```

---

## 🎯 최적화 설정

### 캐싱 전략

```nginx
# API 응답 캐싱
proxy_cache api_cache;
proxy_cache_valid 200 5m;
proxy_cache_valid 404 1m;
proxy_cache_valid 500 1s;
proxy_cache_use_stale error timeout updating http_500 http_502 http_503 http_504;
proxy_cache_background_update on;

# 정적 파일 캐싱
location ~* \.(jpg|jpeg|png|gif|ico|css|js)$ {
    expires 1y;
    add_header Cache-Control "public, immutable";
    access_log off;
}
```

### 압축 설정

```nginx
gzip on;
gzip_vary on;
gzip_min_length 1024;
gzip_comp_level 6;
gzip_types text/plain text/css text/xml text/javascript 
           application/json application/javascript application/xml+rss 
           application/rss+xml font/truetype font/opentype;
```

---

## 🛡️ 보안 설정

### 추가 보안 헤더

```nginx
# Clickjacking 방지
add_header X-Frame-Options "SAMEORIGIN" always;

# MIME 타입 스니핑 방지
add_header X-Content-Type-Options "nosniff" always;

# XSS 방지
add_header X-XSS-Protection "1; mode=block" always;

# Referrer Policy
add_header Referrer-Policy "strict-origin-when-cross-origin" always;

# Content Security Policy (필요시 조정)
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline';" always;
```

### 숨겨진 정보 제거

```nginx
server_tokens off;
more_set_headers "Server: ";
```

---

## 🔧 프론트엔드 빌드 결과물 서빙

### React Router (SPA) 지원

```nginx
location / {
    root /usr/share/nginx/html;
    index index.html;
    try_files $uri $uri/ /index.html;
}
```

### 환경 변수 주입 (빌드 타임)

React 앱은 빌드 타임에 환경 변수가 주입되므로, 배포 시점의 환경 변수는 사용 불가.

대신 빌드 스크립트에서 환경 변수 설정:

```dockerfile
# Dockerfile.frontend
ARG REACT_APP_API_URL
ENV REACT_APP_API_URL=$REACT_APP_API_URL

RUN npm run build
```

---

## 📝 다음 단계

1. [환경 변수 관리](./05-environment-variables.md) - 보안 설정
2. [배포 프로세스](./06-deployment-process.md) - 실제 배포 가이드
3. [모니터링 및 로깅](./07-monitoring-logging.md) - 운영 모니터링

