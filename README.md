# Gestor de Inventario — Backend (Microservicios)

Backend del sistema de Gestion de Inventario: **5 microservicios Spring Boot independientes**, cada uno en su propia carpeta, con su propia base de datos.

Repositorio del frontend (Next.js): `frontend_tec`.

## Microservicios

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| `auth-service` | 8081 | Login y emision de JWT |
| `category-service` | 8082 | Mantenedor CRUD de Categorias |
| `product-service` | 8083 | Mantenedor CRUD de Productos (principal) |
| `inventory-service` | 8084 | Proceso de Movimientos de Inventario (entradas/salidas) |
| `report-service` | 8085 | Reportes: stock actual valorizado y kardex por producto |

## Arquitectura

```
                        ┌──────────────────────┐
                        │  frontend (otro repo)  │   http://localhost:3000
                        └──────────┬────────────┘
                                   │ REST + JWT (Authorization: Bearer)
        ┌───────────────┬─────────┼─────────┬───────────────┐
        │               │         │         │               │
        ▼               ▼         ▼         ▼               ▼
 auth-service   category-service  product-service  inventory-service  report-service
  :8081             :8082            :8083             :8084             :8085
 (login/JWT)      (mantenedor       (mantenedor        (proceso:        (reportes:
                  categorias)        productos)        movimientos)     stock/kardex)
                                        ▲                    │                │
                                        └────────────────────┘                │
                                        (ajusta stock)                        │
                                        ▲───────────────────────────────────-─┘
                                        (consulta productos y movimientos)
```

Cada microservicio:
- Sigue el patron **MVC** (`entity` / `repository` / `service` / `controller`).
- Valida el JWT emitido por `auth-service` (misma clave secreta compartida via `application.properties`).
- Usa una base de datos **H2** embebida y propia (persistida en disco en `./data`).

`product-service`, `inventory-service` y `report-service` se comunican entre si por HTTP usando `RestTemplate`, reenviando el header `Authorization` recibido del frontend.

## Requisitos previos

- **Java 17+** y **Maven 3.9+**.

## Como ejecutar

En 5 terminales distintas, desde la carpeta de cada servicio:

```bash
cd auth-service        && mvn spring-boot:run   # puerto 8081
cd category-service    && mvn spring-boot:run   # puerto 8082
cd product-service     && mvn spring-boot:run   # puerto 8083
cd inventory-service   && mvn spring-boot:run   # puerto 8084
cd report-service      && mvn spring-boot:run   # puerto 8085
```

Para usar el sistema de punta a punta conviene tenerlos todos arriba (product-service depende de category-service para mostrar el nombre de categoria; inventory-service depende de product-service; report-service depende de product-service e inventory-service).

Cada servicio crea su propia base de datos H2 en `./data/<servicio>-db.mv.db` y siembra datos iniciales de ejemplo al primer arranque.

### Consolas H2 (opcional, para inspeccionar datos)

Cada servicio expone su consola en `http://localhost:<puerto>/h2-console` con JDBC URL `jdbc:h2:file:./data/<nombre>-db`, usuario `sa`, sin contrasena.

## Usuarios de prueba (emitidos por auth-service)

| Usuario | Contrasena | Rol |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `operador` | `operador123` | OPERADOR |

## Notas tecnicas

- Autenticacion: JWT (HS256) emitido por `auth-service`, validado de forma independiente por cada microservicio mediante un filtro (`OncePerRequestFilter`) que revisa el header `Authorization: Bearer <token>`.
- CORS habilitado en todos los microservicios para `http://localhost:3000` (frontend).

## Pruebas de calidad (TCY0101 — Evaluacion Parcial N°2)

Se aplicaron las 5 herramientas de testing que exige la pauta de la asignatura, contra el sistema
real (no simulado). Detalle completo e informe de evidencias en
[`docs/EvaluacionParcial2_TCY0101/`](docs/EvaluacionParcial2_TCY0101/).

| Prueba | Herramienta | Resultado |
|---|---|---|
| Unitarias y de integracion | JUnit 5 + Mockito + AssertJ + `@DataJpaTest` (H2) | 38/40 pasan (2 fallas intencionales exponen un bug real) |
| Analisis estatico de codigo | SonarQube Community Edition (Docker) | 0 bugs / 0 code smells / 0% duplicacion en 5 servicios; 1 hallazgo real de seguridad |
| Vulnerabilidades | OWASP ZAP (`zap-baseline.py`, Docker) | 0 hallazgos criticos/altos en frontend y `auth-service` |
| Automatizadas (E2E) | Selenium WebDriver 4.23 + Chrome headless (`selenium-tests/`) | 4/4 pasan, navegador real controlado |
| Carga y estres | k6 (Grafana, Docker) | 1.025 peticiones, 0% error, p95 = 36,8 ms con 25 usuarios concurrentes |

**Como correr cada una:**

```bash
# JUnit (por servicio)
cd inventory-service && mvn test   # idem en auth-service, category-service, product-service, report-service

# Selenium (requiere el frontend y los 5 microservicios ya levantados)
cd selenium-tests && mvn test

# SonarQube (requiere Docker; el scanner exige Java 21, aparte del 17 con que corren los servicios)
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.0.3922:sonar \
  -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<tu-token> -Dsonar.projectKey=<nombre-servicio>

# OWASP ZAP (requiere Docker, frontend/servicio ya levantado)
docker run --rm -v "<carpeta-salida>:/zap/wrk/:rw" -t zaproxy/zap-stable zap-baseline.py \
  -t http://host.docker.internal:3000 -r reporte.html

# k6 (requiere Docker, auth-service/product-service/report-service ya levantados)
docker run --rm -v "<carpeta-del-script>:/scripts" grafana/k6 run /scripts/carga_estres.js
```

### Hallazgos reales detectados (no solo "todo paso")

1. **Bug funcional (JUnit, confirmado en 2 capas — unitaria e integracion con H2 real):**
   `actualizar()` en `product-service` y `category-service` no valida que el codigo/nombre no este
   duplicado con otro registro — a diferencia de `crear()`, que si lo hace. Permite dejar dos
   productos/categorias con el mismo codigo/nombre; a nivel de base de datos termina en una
   `DataIntegrityViolationException` cruda en vez de un error de negocio claro.
2. **Hallazgo de seguridad real (SonarQube, severidad BLOCKER):** contrasenas semilla
   (`admin123`, `operador123`) escritas directo en `DataSeeder.java` de `auth-service`.
3. Otros 5 "security hotspots" que marco SonarQube (CSRF deshabilitado, HMAC-SHA1 en el TOTP) se
   revisaron y son **falsos positivos justificados por el contexto** (API stateless con JWT sin
   cookies de sesion; HMAC-SHA1 es el algoritmo que exige el propio estandar RFC 6238 de TOTP) —
   el detalle de cada justificacion esta en el informe.

Flujo completo de GitHub Actions ya configurado en [`.github/workflows/tests.yml`](.github/workflows/tests.yml) para correr los 40 tests JUnit automaticamente en cada push/PR.
