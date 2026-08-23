# AGENTS.md

SOHT2 — Socket Over HTTP Tunnel 2. Java-based service that tunnels socket connections over HTTP so a
client can reach remote hosts (e.g. internal services) without direct exposure.

## Modules (Gradle multi-module, group `net.soht2`)

- `soht2-common` — shared library: DTOs, compression, utilities (used by client and server).
- `soht2-server` — Spring Boot 3.5 server: receives tunnel requests, forwards them to remote hosts.
  Uses Spring Security, Data JPA + H2 (file DB), Flyway, Caffeine cache, springdoc OpenAPI,
  Thymeleaf. Config: `application-server.yaml` (root) + `src/main/resources/application.yaml`.
- `soht2-client` — Spring Boot 3.5 client: opens local ports, tunnels through the server over HTTP.
  Config: `application-client.yaml` (root) + `src/main/resources/application.yaml`.
- `soht2-ui` — React 19 + TypeScript 5 (Vite) admin UI. `npm run build` also deploys: copies `dist/`
  to `soht2-server/src/main/resources/public/` and moves `index.html` to
  `soht2-server/src/main/resources/templates/index.html` (served by Thymeleaf server). Gradle wires
  `server build` after `ui build` (root `build.gradle`). The Thymeleaf `index.html` template injects
  `window.__CONTEXT_PATH__` and `window.__SWAGGER_URL__`
  (set by `IndexController`), which the UI reads for its router basename and API base URL;
  `IndexController` also serves the SPA fallback routes (`/`, `/login`, `/admin`, `/user`).

## Stack & versions

- Java 21 (Gradle toolchain), Spring Boot 3.5.9, Lombok, vavr; Node ≥ 20 / npm.
- Code style: Spotless for Java (`./gradlew spotlessCheck|spotlessApply`); ESLint + Prettier for the
  UI (`npm run lint|format`). Every Java file must start with the Spotless-enforced header
  `/* SOHT2 © Licensed under MIT $YEAR. */` (UI TS/TSX files use
  `/* SOHT2 © Licensed under MIT 2025. */`);
  `spotlessCheck` fails if it is missing.

## Build & run

- `./gradlew build` — builds all modules (UI builds before server; runs tests).
- Run server: `./gradlew :soht2-server:bootRun` (requires `database-path`, `admin-username`,
  `default-admin-password` in `application-server.yaml`).
- Run client: `./gradlew :soht2-client:bootRun` (requires `url` + at least one `connections` entry
  in `application-client.yaml`).
- UI dev: `cd soht2-ui && npm install && npm run dev`.
- Tests: `./gradlew test` (per-module: `./gradlew :soht2-server:test`).
- Server runs under context path `/soht2` (`server.servlet.context-path` in
  `application-server.yaml`), so the API is at `/soht2/api/...`, the UI at `/soht2`, and Swagger UI
  at `/soht2/swagger-ui`.
- API testing: `requests.http` (IntelliJ HTTP client) at the root, with vars in the gitignored
  `http-client.private.env.json`.
- Client launcher: `doc/soht2-client` bash script (`start|stop|restart|status|log`) for running the
  client as a background service (see `doc/tips-client.md`).
- CI: `.github/workflows/gradle-publish.yml` builds and publishes to GitHub Packages on release.

## Conventions

- Server/client config properties are namespaced `soht2.server.*` / `soht2.client.*`; keep
  `@ConfigurationProperties` classes in sync with root `application-server.yaml` /
  `application-client.yaml` examples.
- API auth is HTTP Basic; roles are `USER` and `ADMIN` (see `SecurityConfig`,
  `UserEntity.ROLE_ADMIN`).
- README docs: root README for config reference; `doc/tips-server.md`, `doc/tips-client.md` for
  tips.
- Change admin password right after first server start; never commit H2 DB files (`soht2.mv.db`,
  `soht2.trace.db`).
- Do not put secrets in code; placeholders like `${SOHT2_USR}` / env vars are the convention.
