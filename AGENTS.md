# AGENTS.md

> **`AGENTS.md` is the canonical instructions file for this project.**
> `CLAUDE.md` and `GEMINI.md` are symlinks pointing to it, so edits made to
> either symlink may fail (or silently rewrite the link) depending on the
> tool — always edit `AGENTS.md` directly.

This is a Spring Boot project that uses MongoDB and Redis.

## Tech stack

- Java 17, Spring Boot 4.1.0 (Maven)
- Spring Data MongoDB — primary persistence
- Spring Data Redis (Jedis) — caching / secondary store
- Spring MVC (REST controllers) + Spring Data REST
- `RestClient` for outbound HTTP to an external scale API
- Lombok for boilerplate, Jakarta Bean Validation for entity constraints

## Project layout

`src/main/java/cl/usm/hddt1rsgebh/`

- `HddT1RsGeBhApplication.java` — application entry point
- `controllers/` — REST endpoints (e.g. `ShipmentController`)
- `services/` — business logic (e.g. `ShipmentService`)
- `repositories/` — Spring Data repositories (e.g. `ShipmentRepository`)
- `entities/` — MongoDB documents and domain types (`Shipment`,
  `ShipmentRequest`, `StatusUpdateRequest`, `Status`, `WeightCategory`)
- `dto/` — data transfer objects (e.g. `Scale`)
- `integration/` — external API clients (e.g. `ExternalScaleClient`)
- `exceptions/` — domain exceptions (e.g. `IllegalWeighingStateException`)
- `configs/` — Spring configuration (e.g. `RedisConfig`)

## Domain notes

- The app tracks shipments weighed on external scales. A `Shipment` is
  persisted to the MongoDB collection `RegistroPesaje`.
- Weights use a custom unit, the Sansa (Sa): 1 Sa = 1.337 Kg. Incoming
  weights are in Kg and stored in Sa (`ShipmentService.kgToSa`).
- Each shipment has a `WeightCategory` derived from its weight in Sa:
  `LIVIANO` (≤ 10), `MEDIANO` (≤ 50), `PESADO` (> 50).
- A shipment's `Status` follows a fixed flow:
  `INGRESADO → PESADO → APROBADO`/`RECHAZADO → DESPACHADO`.
  `ShipmentService.validateTransition` enforces it and these extra rules
  for weighing a `PESADO` package (status → `PESADO`), throwing
  `IllegalWeighingStateException` on violation:
  - no heavy weighing between 20:00 (inclusive) and 06:00 (exclusive);
  - a prime numeric `scaleId` cannot weigh heavy packages on odd days.
  Times use the `America/Santiago` zone.
- Scale specifications are fetched from an external "sansaweigh" API
  (`sansaweigh.api.url`, default `http://localhost:3005/api/v1/scales`) via
  `ExternalScaleClient`; responses are cached in Redis (`scaleSpecs`).

## REST API (`ShipmentController`)

- `POST /shipments` — create a shipment from a `ShipmentRequest` body.
- `GET /shipments/{date}` — list shipments updated on `{date}` (ISO 8601).
- `PATCH /shipments/{id}` — transition a shipment to the status named in the
  `StatusUpdateRequest` body (`{"status": "..."}`); validated against the
  rules above.

An OpenAPI 3.1 spec for these endpoints lives in `docs/openapi.yaml`.

## Configuration

- Config lives in `src/main/resources/application.properties`.
- JSON uses `SNAKE_CASE` property naming.
- Redis connects to `localhost:6379` (see `RedisConfig`).
- MongoDB connects to `mongodb://localhost:27017/hddt1rsgebh`
  (`spring.mongodb.uri`).

## Build & run

```bash
./mvnw spring-boot:run    # run the app
./mvnw test               # run tests
./mvnw clean package      # build the jar
```

MongoDB and Redis must be running locally for the app to start.
