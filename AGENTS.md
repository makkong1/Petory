# Petory (페토리) - Agent Instructions

## Cursor Cloud specific instructions

### Project overview
Petory is a pet care & community platform: Spring Boot 3.5.7 backend (Java 17) + React 19 frontend (CRA).
See `README.md` for full feature list, architecture, and domain documentation links.

### Required services
| Service | Default | Notes |
|---------|---------|-------|
| MySQL 8.0+ | `localhost:3306`, DB name `petory` | Start with `sudo mysqld --user=mysql &` if not running |
| Redis | `localhost:6379` | Start with `sudo redis-server --daemonize yes` |

### Configuration
`backend/main/resources/application.properties` is **gitignored**. A working dev config must include:
- DB connection (`spring.datasource.*`)
- JWT secret/expiration (`jwt.secret`, `jwt.expiration`)
- OAuth2 client registrations (even dummy values) — without them the app fails on `ClientRegistrationRepository` bean
- Redis uses legacy property names: `spring.redis.host` / `spring.redis.port` (not `spring.data.redis.*`)
- Set `spring.profiles.active=dev` to skip email verification

### Running the application
- **Backend**: `./gradlew bootRun --no-daemon --args='--spring.profiles.active=dev'` (from repo root, port 8080)
- **Frontend**: `npm start` in `frontend/` (port 3000, proxies to backend via `"proxy"` in package.json)

### Build & lint
- **Backend compile**: `./gradlew compileJava`
- **Backend tests**: `./gradlew test` (requires running MySQL + Redis)
- **Frontend build**: `npm run build` (in `frontend/`)
- **Frontend lint**: ESLint is built into react-scripts; warnings appear during `npm start` and `npm run build`
- **Frontend tests**: `npm test` (in `frontend/`)

### Gotchas
- Gradle toolchain auto-downloads JDK 17 even when system Java is 21 — this is expected behavior
- The `@PreAuthorize("permitAll()")` on GET `/api/boards` still requires authentication because of the catch-all `.requestMatchers("/api/**").authenticated()` in `SecurityConfig`
- Board creation requires `role` field in registration payload (e.g., `"role":"USER"`)
- OAuth2 social login buttons are present in the UI but won't work with dummy credentials — local auth works fine
