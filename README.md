# Peluqueria

API REST para la gestión de peluqueros y turnos, desarrollada con Spring Boot 4, JPA, H2 en memoria y documentación OpenAPI/Swagger.

## Características

- CRUD completo de peluqueros.
- CRUD completo de turnos.
- Validaciones de negocio para reservas:
  - solo de martes a sábado,
  - entre 09:00 y 17:30,
  - en punto o y media.
- Base H2 en memoria con datos de ejemplo al iniciar.
- Swagger UI y consola H2 habilitados.
- Servidor MCP integrado para operaciones sobre peluqueros y turnos.

## Requisitos

- Java 17
- Maven

## Ejecución

```bash
./mvnw spring-boot:run
```

En Windows:

```bash
mvnw.cmd spring-boot:run
```

La app arranca en `http://localhost:8080`.

## Endpoints principales

- `GET /api/peluqueros`
- `GET /api/peluqueros/{id}`
- `POST /api/peluqueros`
- `PUT /api/peluqueros/{id}`
- `DELETE /api/peluqueros/{id}?confirmar=true`
- `GET /api/turnos`
- `GET /api/turnos/{id}`
- `POST /api/turnos`
- `PUT /api/turnos/{id}`
- `DELETE /api/turnos/{id}`

## Documentación

- Swagger UI: `http://localhost:8080/api-docs`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Consola H2: `http://localhost:8080/h2-console`

## Configuración

La aplicación usa H2 en memoria y configura la base desde `src/main/resources/application.properties`.

## Datos iniciales

Al iniciar por primera vez, se cargan peluqueros y turnos de ejemplo mediante `DataSeeder`.
