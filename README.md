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
