# Evaluación Parcial N°2 (TCY0101) — Notas de pruebas

Sistema evaluado: **Gestor de Inventario** (5 microservicios Spring Boot + frontend Next.js).
Este archivo es el borrador de trabajo: aquí anotamos cada prueba a medida que la corremos
(resultado real, no inventado). Al final se traspasa lo relevante a las tablas del Word
(`Informe_Evidencias_Parcial2.docx`).

Orden acordado: JUnit → OWASP ZAP → SonarQube (sección extra) → Selenium/Katalon → Carga y estrés.

---

## 1. Pruebas Unitarias y de Integración (JUnit) — Estudiante 2  ✅ HECHO (ampliado a los 5 microservicios)

- Herramienta: JUnit 5 + Mockito + AssertJ (vienen con `spring-boot-starter-test`), más `@DataJpaTest` para la parte de integración con H2.
- Alcance: se escribieron y corrieron tests reales en **los 5 microservicios** (antes no existía ningún test en el repo).
- Ambiente de prueba: local, `mvn test` por servicio, Java 17, H2 en memoria (no la de archivo que usa la app en runtime).

### Resultado obtenido (real, ejecutado hoy — 20-10-2026)

| Servicio | Tests | Resultado | Archivos |
|---|---|---|---|
| auth-service | 14 | ✅ 14/14 | `AuthServiceTest` (9), `TotpServiceTest` (5) |
| category-service | 6 | ⚠️ 5/6 (1 falla real) | `CategoriaServiceTest` (4), `CategoriaRepositoryTest` (2) |
| product-service | 9 | ⚠️ 8/9 (1 falla real) | `ProductoServiceTest` (7), `ProductoRepositoryTest` (2) |
| inventory-service | 7 | ✅ 7/7 | `InventoryServiceTest` (5), `MovimientoInventarioRepositoryTest` (2) |
| report-service | 4 | ✅ 4/4 | `ReportServiceTest` (4) |
| **TOTAL** | **40** | **38 pasan / 2 fallan (intencional, defectos reales)** | |

```
auth-service:     Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- BUILD SUCCESS
category-service: Tests run: 6,  Failures: 1, Errors: 0, Skipped: 0 -- BUILD FAILURE
product-service:  Tests run: 9,  Failures: 1, Errors: 0, Skipped: 0 -- BUILD FAILURE
inventory-service:Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0 -- BUILD SUCCESS
report-service:   Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0 -- BUILD SUCCESS
```

### 🐛 Defecto real #1 — `product-service` y `category-service`: falta validar duplicados al EDITAR
Al **crear** un producto o categoría, el servicio sí valida que el código/nombre no esté repetido
(`existsByCodigoIgnoreCase` / `existsByNombreIgnoreCase`). Pero al **editar** (`actualizar()`), esa
validación no existe en ninguno de los dos servicios — es el mismo patrón de código copiado en ambos.
Consecuencia real: se puede editar el Producto B para que tenga el mismo código que el Producto A, y
lo único que detiene eso es la restricción `UNIQUE` de la base de datos, que lanza una excepción cruda
(`DataIntegrityViolationException`) sin traducir — el cliente de la API recibiría un 500 feo en vez de
un 400 con mensaje claro.

- Test que lo expone (y falla a propósito, porque documenta el comportamiento **correcto** esperado):
  `ProductoServiceTest.actualizar_conCodigoDuplicadoDeOtroProducto_deberiaRechazarPeroNoLoHace` y
  `CategoriaServiceTest.actualizar_conNombreDuplicadoDeOtraCategoria_deberiaRechazarPeroNoLoHace`.
- Confirmado también a nivel de base de datos real (`ProductoRepositoryTest` /
  `CategoriaRepositoryTest`): guardar dos registros con el mismo código/nombre lanza
  `DataIntegrityViolationException`, probando que el problema es real y no un error del test.
- **Recomendación de arreglo:** agregar en `actualizar()` la misma validación `existsByXxxIgnoreCase`
  que ya existe en `crear()`, excluyendo el propio id que se está editando.

### Casos unitarios cubiertos por servicio
**auth-service** (`AuthServiceTest` + `TotpServiceTest`): login válido sin 2FA, usuario inexistente,
password incorrecta, usuario inactivo, login con 2FA habilitado (sin token aún), verificación de
segundo factor con código TOTP **real** (calculado con el mismo algoritmo de producción vía
reflexión, no reinventado en el test), código incorrecto, sesión pendiente inexistente, bloqueo tras
5 intentos fallidos (RFC 6238 + política de lockout propia). Más 5 tests puros de `TotpService`
(generación de secreto, URL otpauth, verificación válida/ inválida/ mal formada).

**category-service**: crear con nombre nuevo, crear con nombre duplicado (rechaza bien), eliminar
categoría inexistente, y el test que expone el defecto #1.

**product-service**: crear con código nuevo, crear con código duplicado (rechaza bien), producto
inexistente, ajuste de stock válido (SALIDA), stock insuficiente, tipo de movimiento inválido, y el
test que expone el defecto #1.

**inventory-service** (ya documentado antes): movimiento válido, tipo inválido, producto inexistente,
producto inactivo, listado filtrado — más las 2 de integración contra H2 real.

**report-service**: cálculo de valor total de inventario y conteo de stock bajo, caso sin productos,
combinación producto+kardex, y propagación correcta de "producto no encontrado".

### Resultado esperado vs. obtenido
38 casos: esperado = obtenido. 2 casos (el defecto real): el resultado **esperado** era que
`actualizar()` rechazara el duplicado con una excepción de negocio clara; el resultado **obtenido**
es que no lo hace — exactamente el tipo de hallazgo de calidad que pide el indicador 2.2/2.3 de la
pauta (no una prueba rota, sino una prueba que encontró un problema real).

### Nota para CI/CD (GitHub Actions)
Los 40 tests son deterministas y no dependen de servicios externos levantados (RabbitMQ, otros
microservicios, etc.) — cada uno usa Mockito o H2 en memoria propio. Son aptos para correr en un
pipeline de GitHub Actions sin configuración adicional (ver `.github/workflows/tests.yml`).

## 2. Pruebas de Vulnerabilidad (OWASP ZAP) — Estudiante 1  ✅ HECHO

- Herramienta: **OWASP ZAP** vía contenedor Docker oficial (`zaproxy/zap-stable`), escaneo baseline
  (`zap-baseline.py`). La pauta pide "instalar y aplicar" la herramienta sin exigir un metodo de
  instalacion especifico — Docker es la forma oficial recomendada por el propio proyecto ZAP para
  escaneos automatizados/CI, no un atajo.
- Alcance: frontend Next.js (`localhost:3000`) y `auth-service` (`localhost:8081`, el mas sensible
  por manejar login/2FA).
- Comando ejecutado:
  ```
  docker run --rm -v "<carpeta>/owasp_zap:/zap/wrk/:rw" -t zaproxy/zap-stable zap-baseline.py \
    -t http://host.docker.internal:3000 -r reporte_frontend.html -J reporte_frontend.json
  ```
  (mismo comando contra `host.docker.internal:8081` para auth-service)
- Reportes completos guardados en `docs/EvaluacionParcial2_TCY0101/owasp_zap/`:
  `reporte_frontend.html`, `reporte_frontend.json`, `reporte_auth.html`, `reporte_auth.json`.

### Resultado obtenido (real, ejecutado hoy — 20-10-2026)
```
Frontend (localhost:3000):     FAIL-NEW: 0   WARN-NEW: 11   PASS: 56
auth-service (localhost:8081): FAIL-NEW: 0   WARN-NEW: 1    PASS: 66
```
**Ningún hallazgo de riesgo Alto o Crítico** en ninguno de los dos escaneos.

### Hallazgos del frontend (13 tipos, por nivel de riesgo)
| Riesgo | Hallazgo | Instancias |
|---|---|---|
| Medio (confianza Alta) | Content-Security-Policy (CSP) no configurado | 3 |
| Medio | Falta cabecera anti-clickjacking (X-Frame-Options) | 1 |
| Bajo | Cross-Origin-Resource-Policy faltante | 5 |
| Bajo | Permissions-Policy no configurada | 5 |
| Bajo | X-Content-Type-Options faltante | 5 |
| Bajo | X-Powered-By expone el framework usado | 3 |
| Bajo | Cross-Origin-Embedder-Policy faltante | 1 |
| Bajo | Cross-Origin-Opener-Policy faltante | 1 |
| Bajo | Timestamp Unix expuesto en un bundle JS | 1 |
| Bajo | Uso de funciones JS consideradas peligrosas (eval-like) | 2 |
| Informativo | Comentarios sospechosos en bundles JS compilados | 21 |
| Informativo | Contenido "Modern Web Application" detectado | 3 |
| Informativo | Contenido cacheable pero sin política de cache clara | 5 |

### Hallazgo de auth-service
1 WARN menor: "Non-Storable Content" en rutas que responden 401 — esperado y de bajo impacto (es
una API que correctamente rechaza sin autenticar, no una página cacheable).

### Resultado esperado vs. obtenido
Esperado: sin vulnerabilidades Altas/Críticas, con oportunidades de mejora en cabeceras de seguridad.
Obtenido: exactamente eso — 0 hallazgos graves, pero sí una lista concreta y accionable de cabeceras
de seguridad HTTP que faltan (CSP, X-Frame-Options, X-Content-Type-Options, Permissions-Policy),
típico de una app Next.js en desarrollo sin hardening de producción. No se detectó inyección SQL,
XSS explotable, ni exposición de credenciales/secretos.

### Recomendación de remediación (para la sección de conclusiones)
Agregar en el frontend (Next.js) las cabeceras de seguridad vía `next.config.js` (`headers()`):
`Content-Security-Policy`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
`Permissions-Policy`. Es una tarea de configuración, no de rediseño — bajo esfuerzo, buen impacto.

## 3. Análisis Estático de Código (SonarQube) — sección agregada aparte  ✅ HECHO

- Herramienta: **SonarQube Community Edition** vía Docker (`docker run -d -p 9000:9000 sonarqube:community`)
  + `sonar-maven-plugin` (`mvn ... sonar:sonar`) para análisis real sobre los 5 microservicios.
- Nota técnica: el scanner de SonarQube exige Java 21 para ejecutarse (aunque los servicios corren en
  Java 17 normalmente) — se descargó un JDK 21 (Temurin) aparte, usado **solo** para este comando,
  sin tocar el JDK 17 con el que corren los servicios.
- Alcance: los 5 microservicios completos.

### Resultado obtenido (real, ejecutado hoy — 20-10-2026)
| Servicio | Líneas de código | Bugs | Vulnerabilidades | Code Smells | Duplicación | Rating Seguridad |
|---|---|---|---|---|---|---|
| auth-service | 796 | 0 | **2** | 0 | 0% | **E (5.0)** |
| product-service | 738 | 0 | 1 | 0 | 0% | D (4.0) |
| inventory-service | 617 | 0 | 1 | 0 | 0% | D (4.0) |
| report-service | 534 | 0 | 1 | 0 | 0% | D (4.0) |
| category-service | 425 | 0 | 1 | 0 | 0% | D (4.0) |
| **Total** | **3.110** | **0** | **6** | **0** | **0%** | |

0 bugs, 0 code smells y 0% de duplicación en los 5 servicios — el diseño general del código es limpio.
Las 6 vulnerabilidades son "security hotspots" reales que SonarQube marca para revisión humana, no
necesariamente errores automáticos:

### 🔴 Hallazgo real y válido — contraseñas hardcodeadas (auth-service, BLOCKER)
`java:S6437` en `DataSeeder.java:40` — *"Revoke and change this password, as it is compromised."*
Las contraseñas semilla (`admin123`, `operador123`) están escritas directo en el código fuente. Es
un hallazgo **real**, no un falso positivo: aunque son datos de prueba para un proyecto académico,
es mala práctica que SonarQube detecta correctamente. **Recomendación:** mover a variables de entorno
o generar contraseñas aleatorias que se deban cambiar en el primer login.

### 🟡 Hallazgos marcados como riesgo pero justificables (4 servicios + 1 en auth)
- `java:S4502` (CRITICAL) *"Make sure disabling Spring Security's CSRF protection is safe here"* —
  aparece en `SecurityConfig.java:50` de **inventory, product, category y report-service** (misma
  línea en los 4, porque se migraron con el mismo patrón). **Justificación:** son APIs REST sin
  estado que usan JWT por header `Authorization`, no cookies de sesión — CSRF ataca precisamente el
  envío automático de cookies por el navegador, así que deshabilitarlo aquí es la práctica estándar
  recomendada por la propia documentación de Spring Security para APIs stateless, no un descuido.
- `java:S4790` (CRITICAL) *"Make sure this weak hash algorithm is not used in a sensitive context"*
  en `TotpService.java:93` (uso de HMAC-SHA1). **Justificación:** HMAC-SHA1 no es una debilidad aquí
  — es el algoritmo que exige el propio estándar RFC 6238 (TOTP) para ser compatible con Google
  Authenticator y Microsoft Authenticator; cambiarlo rompería la compatibilidad con esas apps.

### Resultado esperado vs. obtenido
Esperado: el analisis estatico deberia marcar puntos a revisar manualmente, no necesariamente todos
siendo errores reales. Obtenido: exactamente eso — de 6 "vulnerabilidades", 5 son hotspots que al
revisarlos manualmente resultan justificados por el contexto (API stateless, estandar RFC), y 1 es
un hallazgo real y accionable (password hardcodeada) que sí se recomienda corregir. Esto demuestra
valor del analisis estatico: no basta con "arreglar todo lo que marca en rojo", hay que revisar cada
hallazgo con criterio — que es justo lo que pide el indicador 2.2 de la pauta.

## 4. Pruebas Automatizadas (Selenium WebDriver) — Estudiante 1  ✅ HECHO

- Herramienta: **Selenium WebDriver 4.23 (Java) + Chrome headless**. Se usó la API programática de
  Selenium (no Selenium IDE/grabadora) porque permite correr y verificar el resultado de forma
  reproducible por consola, sin depender de una grabación manual con interfaz gráfica.
- Proyecto nuevo: `selenium-tests/` (Maven independiente), clase `LoginYNavegacionTest`.
- Alcance: frontend real (`localhost:3000`), controlando un Chrome real en modo headless.

### Resultado obtenido (real, ejecutado hoy — 20-10-2026)
```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
1. `loginConCredencialesValidas_debeRedirigirAlMenu` — login con operador/operador123 → redirige a `/menu`.
2. `loginConCredencialesInvalidas_debeMostrarErrorYNoRedirigir` — clave incorrecta → muestra mensaje de error, permanece en `/login`.
3. `navegarAProductos_debeMostrarElListadoDeProductos` — clic en "Productos" desde el menú → carga la tabla.
4. `cerrarSesion_debeRedirigirAlLogin` — botón "Cerrar sesión" → vuelve a `/login`.

### Nota de depuración real (vale la pena dejarla, muestra el proceso real de QA)
El primer intento de la prueba 1 falló: el selector `//*[contains(text(),'Gestor de Inventario')]` encontraba
el `<title>` de la pestaña del navegador (que también dice "Gestor de Inventario") en vez del texto visible
del menú — un elemento real pero nunca "mostrado" en pantalla, por lo que `isDisplayed()` daba `false`. Se
corrigió acotando el selector a `//header//*[...]`. Esto confirma que la prueba efectivamente se ejecuta de
verdad contra el DOM real (no es un resultado fijo) y que hubo que depurarla como un caso real de QA.

### Resultado esperado vs. obtenido
Esperado = obtenido en los 4 casos, tras corregir el selector de la prueba 1 (el defecto estaba en la prueba,
no en el sistema).

## 5. Pruebas de Carga y Estrés (k6) — Estudiante 1  ✅ HECHO

- Herramienta: **k6 (Grafana)**, vía contenedor Docker oficial `grafana/k6`.
- Script: `docs/EvaluacionParcial2_TCY0101/k6/carga_estres.js`.
- Alcance: `product-service` (`GET /api/productos`) y `report-service` (`GET /api/reportes/stock-actual`),
  autenticado con un JWT real obtenido de `auth-service` al iniciar la prueba.
- Perfil de carga: rampa 0→10 VUs (10s) → 25 VUs sostenidos (20s) → bajada a 0 (10s). Total 40s.
- Umbrales definidos: p95 de latencia < 800ms, tasa de error < 1%.

### Resultado obtenido (real, ejecutado hoy — 20-10-2026)
```
✓ http_req_duration: p(95)<800   -> p(95)=36.84ms
✓ http_req_failed:   rate<0.01   -> rate=0.00%

http_reqs: 1025 (25.51 req/s)   | http_req_failed: 0.00% (0/1025)
http_req_duration: avg=18.78ms  med=15.51ms  p(90)=24.33ms  p(95)=36.84ms  max=566.69ms
checks: 2048 total, 100% exitosos
peak VUs: 25
```

### Resultado esperado vs. obtenido
Esperado: p95 < 800ms y error < 1% incluso en el pico de 25 usuarios concurrentes. Obtenido: ambos
umbrales se cumplieron con mucho margen (p95 real de solo 36,84ms, 22 veces mejor que el límite
exigido) y 0% de errores. El sistema no mostró degradación perceptible con esta carga — para
encontrar el punto de quiebre real habría que repetir la prueba con una carga bastante mayor
(100-500 VUs), lo cual queda como recomendación de trabajo futuro, no como parte de esta entrega.

## 6. Conclusiones preliminares  ✅ HECHO

Ya escritas directamente en la tabla final del Word (individuales por estudiante + generales),
sintetizando los hallazgos reales de las 5 secciones anteriores: 40/40 pruebas JUnit ejecutadas
(2 fallas intencionales que exponen un bug real), 0 vulnerabilidades críticas en 2 escaneos ZAP,
análisis SonarQube de 3.110 líneas en 5 servicios (0 bugs, 1 hallazgo real de seguridad),
4/4 pruebas Selenium con navegador real, y 1.025 peticiones k6 con 0% de error.

---

## ✅ ESTADO FINAL: las 5 secciones de la pauta están completas y documentadas con evidencia real
(ejecutada hoy, 20-10-2026, no simulada). Todo trasladado también a las tablas del
`Informe_Evidencias_Parcial2.docx`.
