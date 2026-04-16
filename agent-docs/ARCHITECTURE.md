# Architecture Overview
This document serves as a critical, living template designed to equip agents with a rapid and comprehensive understanding of the Petory codebase's architecture, enabling efficient navigation and effective contribution from day one.

## 1. Project Structure
This section provides a high-level overview of the project's directory and file structure, categorised by architectural layer or major functional area.

```text
[Petory Root]/
├── backend/              # Contains all server-side code and APIs
│   ├── main/java/com/linkup/Petory/ # Main source code for backend services
│   │   ├── domain/       # Business logic grouped by domain (user, board, care, meetup, chat, location)
│   │   ├── filter/       # Security and JWT filters
│   │   ├── global/       # Global configurations (Security, Exception Handling)
│   │   └── util/         # Backend utility functions (JwtUtil, etc.)
│   ├── main/resources/   # Backend configuration files (application.properties)
│   └── build.gradle      # Gradle build configuration
├── frontend/             # Contains all client-side code for user interfaces
│   ├── src/              # Main source code for frontend applications
│   │   ├── components/   # Reusable UI components
│   │   ├── pages/        # Application pages/views
│   │   ├── api/          # Axios API modules for backend interaction
│   │   ├── contexts/     # State management (Auth, Theme Context)
│   │   └── styles/       # theme.js and global styled-components
│   └── package.json      # Frontend dependencies and scripts
├── agent-docs/           # Agent-specific documentation (PRD, ADR, UI_GUIDE)
├── .claude/              # Claude/Agent harness settings and skills
├── scripts/              # Automation scripts and execution hooks
└── README.md             # Project overview
```

## 2. High-Level System Diagram
A simple clear text-based description of the major components and their interactions in Petory.

```text
[User (Web Browser)] 
       |
       v
[React Frontend App] <--(REST API & STOMP WebSocket)--> [Spring Boot Backend (Monolith)]
                                                               |
    +----------------------------------------------------------+-----------------+
    |                                                          |                 |
    v                                                          v                 v
[MySQL 8.0]                                                [Redis]      [External APIs]
(Primary DB: Users,                               (Notification Cache,  (Naver Maps API,
 Boards, Care, Chat History)                      Board Cache, Auth)     OAuth Providers)
```

## 3. Core Components

### 3.1. Frontend
**Name:** Petory Web App
**Description:** The main user interface for interacting with the system, allowing users to find pet-friendly locations, request/offer pet care, join local meetups, and engage in real-time chat.
**Technologies:** React 19, Styled-components, Axios, Recharts (Admin Dashboard).
**Deployment:** Currently optimized for local proxy testing, production deployment agnostic (e.g., Vercel, S3/CloudFront).

### 3.2. Backend Services

#### 3.2.1. Petory Monolithic Backend
**Name:** Petory Core Server
**Description:** A Monolithic Spring Boot API server that handles all business domains including JWT authentication, LBS(Location Based Service) processing with Haversine distance calculations, real-time STOMP websocket chatting, and escrow-based pet-coin transactions.
**Technologies:** Java 17, Spring Boot 3.5.7, Spring Data JPA, Spring Security, Spring WebSocket.
**Deployment:** Standard Java Application execution (e.g., AWS EC2, Docker Container).

## 4. Data Stores

### 4.1. Relational Database
**Name:** Petory Primary DB
**Type:** MySQL 8.0+
**Purpose:** Stores all persistent business data with ACID compliance. Uses `ST_Distance_Sphere` for efficient spatial queries.
**Key Schemas:** `User`, `Board`, `Care`, `Meetup`, `ChatRoom`, `ChatMessage`, `Location`, `Notification`.

### 4.2. In-Memory Data Store
**Name:** Petory Cache & Buffer
**Type:** Redis
**Purpose:** Used to reduce RDBMS load and manage ephemeral data.
- Buffers real-time SSE notifications (TTL 24h).
- Caches frequently accessed board details via Spring `@Cacheable`.
- Stores temporary email verification tokens.

## 5. External Integrations / APIs

**Service Name 1:** Naver Maps API
**Purpose:** Handles Geocoding (Address to Coordinates), Reverse Geocoding, and Directions for spatial features and map rendering on the frontend.
**Integration Method:** REST API via Backend `NaverMapService` to protect API keys.

**Service Name 2:** OAuth2 Social Login
**Purpose:** Provides social authentication (Google, Kakao, Naver).
**Integration Method:** Spring Security OAuth2 Client.

## 6. Deployment & Infrastructure
*(Currently focused on local development)*
**Local Infrastructure:** Localhost MySQL, Localhost Redis.
**Build Tools:** Gradle (Backend), Npm/Webpack (Frontend).
**Reverse Proxy:** React Dev Server uses `"proxy": "http://localhost:8080"` to map `/api` calls.

## 7. Security Considerations
**Authentication:** Stateless JWT (JSON Web Token). Access tokens valid for 15 minutes; Refresh tokens stored in DB valid for 1 day.
**Authorization:** Method-level security via `@PreAuthorize("hasRole('USER')")`. Roles include `USER`, `SERVICE_PROVIDER`, `ADMIN`, `MASTER`.
**Data Concurrency:** Pessimistic locking (`findByIdForUpdate`) applied on PetCoin transaction methods and Escrow domains to prevent race conditions.

## 8. Development & Testing Environment
**Local Setup Instructions:** 
- Run `sudo mysqld --user=mysql &`
- Run `sudo redis-server --daemonize yes`
- Backend: `./gradlew bootRun --args='--spring.profiles.active=dev'`
- Frontend: `npm start`
**Testing Frameworks:** JUnit (Backend tests requiring MySQL+Redis runtime), Jest (Frontend).

## 9. Future Considerations / Roadmap
- **Microservices Extraction:** If traffic grows steadily in areas like Chat/Notification, they may be broken out into independent microservices.
- **Search Optimization:** Currently using MySQL Spatial indexing, but Elasticsearch could be considered if location query complexity outscales MySQL bounds.

## 10. Project Identification
**Project Name:** Petory (페토리)
**Date of Last Update:** 2026-04-15

## 11. Glossary / Acronyms
**[LBS]:** Location-Based Service. Features built around the user's geographic coordinates.
**[PetCoin]:** Internal virtual currency used to pay for pet care services securely via an escrow system.
**[STOMP]:** Simple/Streaming Text Oriented Messaging Protocol. Used over WebSockets for chat functionality.
