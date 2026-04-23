# Testcontainers + Spring Boot CI Debugging Log

Four separate root causes surfaced while getting integration tests green on GitHub Actions. Documented here so future contributors don't repeat the journey.

---

## Issue 1 — `@ActiveProfiles` was not suppressing the dev datasource URL

**Symptom:** `Connection to localhost:32769 refused` on the first CI run.

**Root cause (initially misdiagnosed):** `application-dev.yml` sets an explicit `spring.datasource.url`. The theory was that this overrode the Testcontainers URL. This turned out to be wrong — `@ServiceConnection` / `@DynamicPropertySource` already wins the property priority race.

**Actual effect:** Adding `@ActiveProfiles("test")` + `application-test.yml` was still the right hygiene (no dev-profile noise in tests), but it did not fix the connection error.

---

## Issue 2 — Ryuk reaper killed the container mid-run

**Symptom:** Tests started, Spring context loaded, Flyway ran fine — then Hikari began logging `HikariPool-1 - Failed to validate connection ... (This connection has been closed.)` roughly 2 minutes in, followed by 30-second timeout and `Connection refused`.

**Root cause:** Testcontainers uses a companion "Ryuk" container to garbage-collect test resources. Ryuk maintains a TCP keepalive socket to the JVM. On GitHub Actions runners, that socket can time out or be dropped by the network, causing Ryuk to assume the JVM exited and reap the Postgres container — while the JVM is still running tests.

**Fix:**
- `src/test/resources/testcontainers.properties` → `ryuk.disabled=true`
- CI step env: `TESTCONTAINERS_RYUK_DISABLED: "true"`

Without Ryuk, containers are stopped via JVM shutdown hooks instead.

---

## Issue 3 — `@Testcontainers` / `@Container` per-class lifecycle broke Spring context caching

**Symptom:** `AuthControllerIT` passed, then `StampFlowIT` hit "connection has been closed" immediately on the first request. The log showed Testcontainers creating a **new** container at the start of `StampFlowIT`:

```
tc.postgres:16 : Creating container for image: postgres:16   ← brand-new container
tc.postgres:16 : Container is started (JDBC URL: jdbc:postgresql://localhost:32769/...)
HikariPool-1 - Failed to validate connection ... (This connection has been closed.)
```

**Root cause:** `@Testcontainers` manages static `@Container` fields **per test class**. Even though `postgres` is a static field in `BaseIntegrationTest`, the extension stops it after `AuthControllerIT` ends, then restarts it for `StampFlowIT` with a new port. Spring's context cache reuses the context from `AuthControllerIT` (same cache key — the `@DynamicPropertySource` *method reference* is the key, not the evaluated URL). The cached context holds Hikari connections to the now-dead first container.

**Fix:** Switched to the [Testcontainers Singleton Container pattern](https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers):

```java
// No @Testcontainers, no @Container
static final PostgreSQLContainer<?> postgres;

static {
    postgres = new PostgreSQLContainer<>("postgres:16");
    postgres.start();  // started once when class is first loaded
}
```

The container starts once via the static initializer, lives for the entire JVM run, and is stopped by Testcontainers' own JVM shutdown hook. The URL never changes, so Spring's context cache works correctly across all IT classes.

---

## Issue 4 — Two actual production bugs caught by the tests

Once the container stayed alive, two tests still failed:

### 4a — Spring Security 6 returns 403 instead of 401 for unauthenticated requests

`GET /api/auth/me` without a session returned `403` but tests expected `401`.

Spring Security 6 uses `Http403ForbiddenEntryPoint` as the default when no authentication mechanism (form login, HTTP Basic, etc.) is configured. Fix: explicitly register an `AuthenticationEntryPoint`:

```java
.exceptionHandling(e -> e
    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
```

### 4b — `GET /api/auth/me` response was missing `slug`

`ShopPublicDTO` did not include `slug`. Added it to the record and `ShopService.toPublicDTO()`.

---

## Summary table

| # | Symptom | Root cause | Fix |
|---|---------|------------|-----|
| 1 | Connection refused — same port as Testcontainers | Misdiagnosis; dev profile URL theory was wrong | `@ActiveProfiles("test")` + `application-test.yml` (hygiene, not the fix) |
| 2 | Connections closed mid-run, new connections refused | Ryuk reaper killed the container over a dropped socket | `ryuk.disabled=true` in properties + CI env var |
| 3 | Second IT class fails immediately | `@Testcontainers` stops container between classes; Spring reuses stale context | Singleton Container pattern (static block, no annotations) |
| 4a | 403 instead of 401 for unauthenticated requests | Spring Security 6 default entry point returns 403 | `HttpStatusEntryPoint(UNAUTHORIZED)` in SecurityConfig |
| 4b | `$.slug` missing from `/api/auth/me` | `ShopPublicDTO` did not include slug field | Added `slug` to DTO and mapper |
