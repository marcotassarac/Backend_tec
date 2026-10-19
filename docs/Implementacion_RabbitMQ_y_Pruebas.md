# Implementación de RabbitMQ en el Gestor de Inventario — Guía y Pruebas

Proyecto: `CLOUD_NATIVE_I/Backend_tec` · Rama: `feature/rabbitmq-mensajeria`

Este documento explica **todo lo que se implementó** para incorporar mensajería asíncrona con RabbitMQ a los microservicios, **las pruebas que se realizaron** (con sus resultados reales) y **cómo repetirlas paso a paso**.

---

## Índice

1. [Resumen](#1-resumen)
2. [Conceptos básicos](#2-conceptos-básicos)
3. [Arquitectura y flujo de mensajes](#3-arquitectura-y-flujo-de-mensajes)
4. [Lo que se implementó](#4-lo-que-se-implementó)
   - 4.1 [Clúster RabbitMQ con Docker Compose](#41-clúster-rabbitmq-con-docker-compose)
   - 4.2 [product-service: productor, configuración y administración](#42-product-service-productor-configuración-y-administración)
   - 4.3 [inventory-service: consumidor con validación DLQ](#43-inventory-service-consumidor-con-validación-dlq)
   - 4.4 [report-service: consumidores de Kardex y Auditoría con validación DLQ](#44-report-service-consumidores-de-kardex-y-auditoría-con-validación-dlq)
   - 4.5 [Commits realizados](#45-commits-realizados)
5. [Pruebas realizadas y resultados](#5-pruebas-realizadas-y-resultados)
6. [Cómo probarlo tú mismo paso a paso](#6-cómo-probarlo-tú-mismo-paso-a-paso)
7. [Errores comunes y cómo resolverlos](#7-errores-comunes-y-cómo-resolverlos)
8. [Cómo detener todo](#8-cómo-detener-todo)

---

## 1. Resumen

Se implementó lo siguiente:

| # | Qué | Dónde |
|---|-----|-------|
| 1 | Clúster de **2 nodos RabbitMQ** con interfaz web | `docker-compose.yml` (raíz del backend) |
| 2 | **Productor** de eventos hacia **3 colas**, configuración de exchanges/colas/DLQ | `product-service` |
| 3 | **Servicio administrador dedicado** (`RabbitAdminService`) que encapsula `AmqpAdmin` para colas, exchanges y bindings, con **validación de nombres** (400 Bad Request) | `product-service` |
| 4 | **Consumidor** de `cola.inventario` con validación que deriva a DLQ | `inventory-service` |
| 5 | **Consumidor** de `cola.notificaciones` (registro de Kardex) con validación que deriva a DLQ | `report-service` |
| 6 | **Consumidor** de `cola.auditoria` (registro de auditoría) con validación que deriva a DLQ | `report-service` |

**Regla respetada:** no se modificó ningún archivo de seguridad (`SecurityConfig.java` ni nada relacionado con la seguridad) en ningún microservicio.

**Resultado:** cuando se crea un producto, product-service publica un evento `PRODUCTO_CREADO` hacia **tres colas**: `cola.inventario`, `cola.notificaciones` y `cola.auditoria`. RabbitMQ lo entrega a inventory-service (inventario) y a report-service (Kardex y auditoría). Si un evento trae datos inválidos, el consumidor lo rechaza y RabbitMQ lo mueve automáticamente a la **Dead Letter Queue (DLQ)** de esa cola.

---

## 2. Conceptos básicos

| Concepto | Explicación sencilla |
|----------|---------------------|
| **Productor** | El servicio que envía mensajes (aquí: product-service). |
| **Consumidor** | El servicio que recibe y procesa mensajes (aquí: inventory-service y report-service). |
| **Cola (Queue)** | "Buzón" donde se guardan los mensajes hasta que un consumidor los lee. |
| **Exchange** | "Oficina de correos": recibe el mensaje del productor y decide a qué cola(s) enviarlo según reglas. |
| **Binding** | La regla que conecta un exchange con una cola. |
| **Routing key** | Etiqueta del mensaje que el exchange usa para decidir el destino. |
| **Direct Exchange** | Envía el mensaje a la cola cuyo binding coincide **exactamente** con la routing key. |
| **Topic Exchange** | Envía según **patrones** (`producto.#` = cualquier routing key que empiece con `producto.`). |
| **Fanout Exchange** | Envía el mensaje a **todas** las colas enlazadas, sin mirar la routing key. |
| **DLX (Dead Letter Exchange)** | Exchange que recibe los mensajes "muertos" (rechazados, expirados, etc.). |
| **DLQ (Dead Letter Queue)** | Cola donde terminan los mensajes rechazados para revisarlos después, sin perderlos. |
| **AmqpAdmin** | Componente de Spring para crear y borrar colas, exchanges y bindings desde código. |
| **Erlang Cookie** | "Contraseña" compartida que permite a los nodos RabbitMQ confiar entre sí y formar el clúster. |

---

## 3. Arquitectura y flujo de mensajes

```
                         ┌──────────────────────┐
  Postman  ──POST──►     │   product-service    │  (puerto 8083)
  /api/productos         │   (PRODUCTOR)        │
                         └──────────┬───────────┘
                                    │ evento PRODUCTO_CREADO (JSON)
                                    ▼
          ┌──────────────────────────────────────────────┐
          │  RabbitMQ (clúster 2 nodos)                  │
          │                                              │
          │  exchange: pedidos.direct                    │
          │   ├─ rk "cola.inventario" ──► cola.inventario ─────► inventory-service (8084)
          │   ├─ rk "cola.notificaciones" ► cola.notificaciones ► report-service (8085) · Kardex
          │   └─ rk "cola.auditoria" ───► cola.auditoria ──────► report-service (8085) · Auditoría
          │                                              │              │
          │  exchange: pedidos.topic                     │              │ si el consumidor
          │   └─ "producto.#" ─► las tres colas          │              │ lanza excepción
          │                                              │              ▼
          │  exchange: pedidos.dlx ◄───────────── (mensaje rechazado, sin reencolar)
          │   ├─ "cola.inventario.dlq" ─────► cola.inventario.dlq
          │   ├─ "cola.notificaciones.dlq" ─► cola.notificaciones.dlq
          │   └─ "cola.auditoria.dlq" ──────► cola.auditoria.dlq
          └──────────────────────────────────────────────┘
```

**Flujo normal (mensaje válido):**
1. Se crea un producto con `POST /api/productos`.
2. product-service lo guarda en la base de datos.
3. product-service publica el evento en `pedidos.direct` **tres veces**, con routing keys `cola.inventario`, `cola.notificaciones` y `cola.auditoria`.
4. RabbitMQ deja una copia en cada cola.
5. inventory-service procesa `cola.inventario`; report-service procesa `cola.notificaciones` (Kardex) y `cola.auditoria` (auditoría). Las colas quedan en 0.

**Flujo de error (mensaje inválido):**
1. El consumidor detecta datos inválidos y lanza `IllegalArgumentException`.
2. Spring rechaza el mensaje **sin reencolarlo** (`default-requeue-rejected=false`).
3. La cola tiene configurado `x-dead-letter-exchange = pedidos.dlx`, así que RabbitMQ envía el mensaje a ese exchange.
4. `pedidos.dlx` lo deja en la DLQ correspondiente (`cola.inventario.dlq`, `cola.notificaciones.dlq` o `cola.auditoria.dlq`).

---

## 4. Lo que se implementó

### 4.1 Clúster RabbitMQ con Docker Compose

**Archivo:** `Backend_tec/docker-compose.yml`

```yaml
services:
  rabbitmq1:
    image: rabbitmq:3-management
    container_name: rabbitmq1
    hostname: rabbitmq1
    environment:
      RABBITMQ_ERLANG_COOKIE: "PEDIDOS360_COOKIE"
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks:
      - rabbitmq-cluster

  rabbitmq2:
    image: rabbitmq:3-management
    container_name: rabbitmq2
    hostname: rabbitmq2
    environment:
      RABBITMQ_ERLANG_COOKIE: "PEDIDOS360_COOKIE"
    ports:
      - "5673:5672"
      - "15673:15672"
    depends_on:
      rabbitmq1:
        condition: service_healthy
    # Arranca el nodo y lo une al cluster de rabbitmq1
    command: >
      bash -c "docker-entrypoint.sh rabbitmq-server &
      PID=$$!;
      until gosu rabbitmq rabbitmqctl -q await_startup; do sleep 2; done;
      gosu rabbitmq rabbitmqctl stop_app &&
      gosu rabbitmq rabbitmqctl reset &&
      gosu rabbitmq rabbitmqctl join_cluster rabbit@rabbitmq1 &&
      gosu rabbitmq rabbitmqctl start_app;
      wait $$PID"
    networks:
      - rabbitmq-cluster

networks:
  rabbitmq-cluster:
    driver: bridge
```

**Explicación de cada parte:**

| Elemento | Para qué sirve |
|----------|----------------|
| `image: rabbitmq:3-management` | RabbitMQ 3 con el plugin de interfaz web incluido. |
| `hostname` | Nombre fijo del nodo. RabbitMQ identifica cada nodo como `rabbit@<hostname>`, por eso debe ser estable. |
| `RABBITMQ_ERLANG_COOKIE` | Ambos nodos comparten `PEDIDOS360_COOKIE`. Sin la misma cookie no pueden comunicarse. |
| Puertos nodo 1 | `5672` (AMQP, lo usan los microservicios) y `15672` (interfaz web). |
| Puertos nodo 2 | `5673` (AMQP) y `15673` (interfaz web). Se cambian para no chocar con el nodo 1. |
| `healthcheck` | Comprueba que el nodo 1 responde (`rabbitmq-diagnostics ping`). |
| `depends_on ... service_healthy` | El nodo 2 **espera a que el nodo 1 esté sano**, no solo a que el contenedor exista. |
| `command` del nodo 2 | Arranca RabbitMQ, espera a que esté listo y lo **une al clúster** del nodo 1 (`join_cluster rabbit@rabbitmq1`). |
| `networks` | Red propia para que los nodos se encuentren por nombre. |

**Problemas encontrados al levantar el clúster y cómo se resolvieron:**

1. **El nodo 2 se caía con `Error when reading /var/lib/rabbitmq/.erlang.cookie: eacces`.**
   - *Causa:* los comandos `rabbitmqctl` se ejecutaban como `root` y creaban el archivo de la cookie con dueño `root`; el servidor corre como el usuario `rabbitmq` y no podía leerlo.
   - *Solución:* ejecutar los comandos con `gosu rabbitmq rabbitmqctl ...` (como el usuario correcto).

2. **El nodo 2 arrancaba pero nunca se unía al clúster (sin error visible).**
   - *Causa:* `await_startup` se ejecutaba antes de que el nodo existiera, fallaba al instante, y como los comandos estaban encadenados con `&&`, nunca se ejecutaba `join_cluster`.
   - *Solución:* un bucle `until ... await_startup; do sleep 2; done;` que reintenta hasta que el nodo responda.

> **Importante:** compartir la Erlang cookie **no basta** para formar el clúster; también hay que ejecutar `join_cluster`. Eso es lo que hace el `command` del nodo 2.

---

### 4.2 product-service: productor, configuración y administración

#### a) Dependencia en `pom.xml`

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

#### b) Conexión en `application.properties`

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

Se conecta al **nodo 1** del clúster (puerto 5672).

#### c) `config/RabbitMQConfig.java` — topología

Define con `ExchangeBuilder`, `QueueBuilder` y `BindingBuilder`:

**Exchanges** (todos durables):

| Nombre | Tipo | Uso |
|--------|------|-----|
| `pedidos.topic` | Topic | Difusión por patrón `producto.#` a las tres colas |
| `pedidos.direct` | Direct | Entrega a una cola concreta usando su nombre como routing key (**es el que usa el productor**) |
| `pedidos.dlx` | Direct | Dead Letter Exchange: recibe los mensajes rechazados |

**Colas** (todas durables):

| Constante | Cola | Argumentos | Función |
|-----------|------|-----------|---------|
| `COLA_INVENTARIO` | `cola.inventario` | `x-dead-letter-exchange=pedidos.dlx`, `x-dead-letter-routing-key=cola.inventario.dlq` | Eventos para inventory-service |
| `COLA_NOTIFICACIONES` | `cola.notificaciones` | `x-dead-letter-exchange=pedidos.dlx`, `x-dead-letter-routing-key=cola.notificaciones.dlq` | Eventos de Kardex para report-service |
| `COLA_AUDITORIA` | `cola.auditoria` | `x-dead-letter-exchange=pedidos.dlx`, `x-dead-letter-routing-key=cola.auditoria.dlq` | Eventos de auditoría para report-service |
| `COLA_INVENTARIO_DLQ` | `cola.inventario.dlq` | — | Mensajes rechazados por el consumidor de inventario |
| `COLA_NOTIFICACIONES_DLQ` | `cola.notificaciones.dlq` | — | Mensajes rechazados por el consumidor de Kardex |
| `COLA_AUDITORIA_DLQ` | `cola.auditoria.dlq` | — | Mensajes rechazados por el consumidor de auditoría |

**Bindings:**

| Exchange | Routing key / patrón | Cola destino |
|----------|---------------------|--------------|
| `pedidos.direct` | `cola.inventario` | `cola.inventario` |
| `pedidos.direct` | `cola.notificaciones` | `cola.notificaciones` |
| `pedidos.direct` | `cola.auditoria` | `cola.auditoria` |
| `pedidos.topic` | `producto.#` | `cola.inventario` |
| `pedidos.topic` | `producto.#` | `cola.notificaciones` |
| `pedidos.topic` | `producto.#` | `cola.auditoria` |
| `pedidos.dlx` | `cola.inventario.dlq` | `cola.inventario.dlq` |
| `pedidos.dlx` | `cola.notificaciones.dlq` | `cola.notificaciones.dlq` |
| `pedidos.dlx` | `cola.auditoria.dlq` | `cola.auditoria.dlq` |

Ejemplo de cómo se declara la cola de auditoría con su DLX usando Builder:

```java
@Bean
public Queue colaAuditoria() {
    return QueueBuilder.durable(COLA_AUDITORIA)
            .deadLetterExchange(DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(COLA_AUDITORIA_DLQ)
            .build();
}

@Bean
public Queue colaAuditoriaDlq() {
    return QueueBuilder.durable(COLA_AUDITORIA_DLQ).build();
}

@Bean
public Binding auditoriaDirectBinding() {
    return BindingBuilder.bind(colaAuditoria()).to(pedidosDirectExchange()).with(COLA_AUDITORIA);
}

@Bean
public Binding auditoriaTopicBinding() {
    return BindingBuilder.bind(colaAuditoria()).to(pedidosTopicExchange()).with(TOPIC_ROUTING_PRODUCTO);
}

@Bean
public Binding auditoriaDlqBinding() {
    return BindingBuilder.bind(colaAuditoriaDlq()).to(pedidosDeadLetterExchange()).with(COLA_AUDITORIA_DLQ);
}
```

También se registra un `Jackson2JsonMessageConverter` para que los mensajes viajen en **JSON** en lugar de serialización Java.

> **Nota:** Spring no crea los exchanges ni las colas al arrancar el servicio, sino **la primera vez que abre una conexión** con RabbitMQ (por ejemplo, al crear el primer producto o al llamar al endpoint de administración).

#### d) Administración de RabbitMQ: `RabbitAdminService` + `RabbitAdminController`

La administración sigue una **arquitectura en capas**:

```
RabbitAdminController (solo REST)  ──►  RabbitAdminService (lógica + validación)  ──►  AmqpAdmin  ──►  RabbitMQ
```

- **`service/RabbitAdminService.java`**: es la **única clase** que usa `AmqpAdmin`. Contiene toda la lógica para crear y borrar **colas, exchanges y bindings**, y valida los datos de entrada.
- **`controller/RabbitAdminController.java`**: solo inyecta `RabbitAdminService` y expone los endpoints REST. **No tiene lógica de RabbitMQ ni llama a `AmqpAdmin`.**

```java
@RestController
@RequestMapping("/api/v1/admin/rabbitmq")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping("/queues/{queueName}")
    public ResponseEntity<Map<String, String>> crearCola(@PathVariable String queueName) {
        String nombre = rabbitAdminService.crearCola(queueName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("queue", nombre, "status", "creada"));
    }
    // ... resto de endpoints, todos delegan en rabbitAdminService
}
```

**Endpoints disponibles** (todos requieren token JWT; sin token responden **401**):

| Método | Ruta | Resultado |
|--------|------|-----------|
| `POST` | `/api/v1/admin/rabbitmq/queues/{queueName}` | Crea una cola durable → **201** `{"queue":"...","status":"creada"}` |
| `DELETE` | `/api/v1/admin/rabbitmq/queues/{queueName}` | Borra la cola → **204** |
| `POST` | `/api/v1/admin/rabbitmq/exchanges/{exchangeName}?type=direct\|topic\|fanout` | Crea un exchange durable (por defecto `direct`) → **201** |
| `DELETE` | `/api/v1/admin/rabbitmq/exchanges/{exchangeName}` | Borra el exchange → **204** |
| `POST` | `/api/v1/admin/rabbitmq/bindings?queue=...&exchange=...&routingKey=...` | Enlaza una cola a un exchange (`routingKey` opcional) → **201** |
| `DELETE` | `/api/v1/admin/rabbitmq/bindings?queue=...&exchange=...&routingKey=...` | Elimina el enlace → **204** |

**Validación de nombres (error 400 Bad Request):**

```java
private void validarNombre(String nombre, String recurso) {
    if (nombre == null || nombre.isBlank()) {
        throw new IllegalArgumentException("El nombre de la " + recurso + " es obligatorio");
    }
    if (nombre.chars().anyMatch(Character::isWhitespace)) {
        throw new IllegalArgumentException(
                "El nombre de la " + recurso + " no puede contener espacios: '" + nombre + "'");
    }
}
```

| Caso inválido | Respuesta |
|---------------|-----------|
| Nombre nulo o en blanco (ej. `/queues/%20%20`) | **400** `"El nombre de la cola es obligatorio"` |
| Nombre con espacios (ej. `/queues/cola%20mala`) | **400** `"El nombre de la cola no puede contener espacios: 'cola mala'"` |
| Tipo de exchange desconocido (ej. `?type=otro`) | **400** `"Tipo de exchange invalido: 'otro'. Valores permitidos: direct, topic, fanout"` |
| Routing key con espacios | **400** `"La routing key no puede contener espacios: ..."` |
| Falta un parámetro obligatorio en bindings (ej. sin `exchange`) | **400** `"Falta el parametro obligatorio 'exchange'"` |

> `%20` es como se escribe un **espacio** dentro de una URL.

Para que estas excepciones respondan **400** (y no 500), se agregaron dos manejadores en `exception/GlobalExceptionHandler.java`: uno para `IllegalArgumentException` y otro para `MissingServletRequestParameterException`. Ambos devuelven el mismo formato de error JSON que el resto del servicio:

```json
{
  "timestamp": "2026-10-19T00:26:07.2169509",
  "status": 400,
  "error": "Bad Request",
  "message": "El nombre de la cola no puede contener espacios: 'cola mala'",
  "path": "/api/v1/admin/rabbitmq/queues/cola%20mala"
}
```

> Como no se modificó `SecurityConfig`, **cualquier usuario autenticado** puede usar estos endpoints. Para restringirlos solo a administradores habría que modificar la configuración de seguridad.

#### e) `service/ProductoService.java` — productor

Se inyecta `RabbitTemplate` en el constructor. Al **crear un producto**, después de guardarlo, se llama a `publicarEvento("PRODUCTO_CREADO", producto)`, que envía el evento a **las tres colas**:

```java
private void publicarEvento(String tipo, Producto producto) {
    Map<String, Object> evento = new LinkedHashMap<>();
    evento.put("tipo", tipo);
    evento.put("productoId", producto.getId());
    evento.put("codigo", producto.getCodigo());
    evento.put("nombre", producto.getNombre());
    evento.put("stock", producto.getStock());
    evento.put("fecha", LocalDateTime.now().toString());

    try {
        rabbitTemplate.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, RabbitMQConfig.COLA_INVENTARIO, evento);
        rabbitTemplate.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, RabbitMQConfig.COLA_NOTIFICACIONES, evento);
        rabbitTemplate.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, RabbitMQConfig.COLA_AUDITORIA, evento);
        log.info("Evento {} publicado para producto {}", tipo, producto.getId());
    } catch (AmqpException ex) {
        log.error("No se pudo publicar el evento {} del producto {}: {}", tipo, producto.getId(), ex.getMessage());
    }
}
```

Ejemplo del mensaje JSON que se envía:

```json
{
  "tipo": "PRODUCTO_CREADO",
  "productoId": 33,
  "codigo": "AUD-001",
  "nombre": "Monitor",
  "stock": 5,
  "fecha": "2026-10-19T00:25:41.433450100"
}
```

Si RabbitMQ no está disponible, **el producto se guarda igual** y solo se registra el error en el log (no se rompe la creación del producto).

---

### 4.3 inventory-service: consumidor con validación DLQ

**Cambios:**

1. `pom.xml`: se agregó `spring-boot-starter-amqp`.
2. `application.properties`:
   ```properties
   spring.rabbitmq.host=localhost
   spring.rabbitmq.port=5672
   spring.rabbitmq.username=guest
   spring.rabbitmq.password=guest
   # Un mensaje que lanza excepcion se rechaza sin reencolar y RabbitMQ lo deriva a la DLQ
   spring.rabbitmq.listener.simple.default-requeue-rejected=false
   ```
3. `config/RabbitMQConfig.java`: declara `cola.inventario` (con los **mismos argumentos** que en product-service), su DLQ, el exchange `pedidos.dlx` y el conversor JSON.
4. `consumer/InventoryConsumer.java`: el consumidor.

```java
@RabbitListener(queues = RabbitMQConfig.COLA_INVENTARIO)
public void procesarEvento(Map<String, Object> evento) {
    log.info("Evento recibido en {}: {}", RabbitMQConfig.COLA_INVENTARIO, evento);

    long productoId = leerNumero(evento, "productoId");
    String campoCantidad = evento.containsKey("cantidad") ? "cantidad" : "stock";
    long cantidad = leerNumero(evento, campoCantidad);

    if (productoId <= 0 || cantidad <= 0) {
        log.error("[GRAVE] Evento de inventario invalido (productoId={}, {}={}). Se envia a {}", ...);
        throw new IllegalArgumentException("Evento de inventario invalido: ...");
    }

    log.info("Inventario procesado: evento {} del producto {} ({}), {}={}", ...);
}
```

**Regla de validación:** si `productoId <= 0`, si la cantidad (`cantidad` o, si no viene, `stock`) es `<= 0`, o si esos campos no son numéricos → se imprime un log `[GRAVE]` y se lanza `IllegalArgumentException` → el mensaje va a `cola.inventario.dlq`.

> Como el evento `PRODUCTO_CREADO` no trae `cantidad`, se valida el campo `stock`. Por eso **un producto creado con stock 0 termina en la DLQ** (así se fuerza el caso de error en las pruebas).

**¿Por qué se necesitó `default-requeue-rejected=false`?** Por defecto, Spring devuelve el mensaje fallido a la misma cola. El consumidor lo volvería a leer, fallaría otra vez, y así infinitamente — **nunca llegaría a la DLQ**. Con esta propiedad, el mensaje se rechaza definitivamente y RabbitMQ lo deriva al DLX.

**¿Por qué el consumidor declara también la cola?** Para que pueda arrancar aunque product-service todavía no haya creado la topología. Los argumentos deben ser idénticos; si difieren, RabbitMQ rechaza la declaración (`PRECONDITION_FAILED`).

---

### 4.4 report-service: consumidores de Kardex y Auditoría con validación DLQ

**Cambios generales:** los mismos que en inventory-service (dependencia `spring-boot-starter-amqp` y propiedades con `default-requeue-rejected=false`).

**`config/RabbitMQConfig.java`** declara, con sus beans, las **dos colas** que consume este servicio y sus DLQ:

| Bean | Cola / Exchange |
|------|-----------------|
| `pedidosDeadLetterExchange()` | `pedidos.dlx` |
| `colaNotificaciones()` | `cola.notificaciones` → DLX `pedidos.dlx`, routing key `cola.notificaciones.dlq` |
| `colaNotificacionesDlq()` + `notificacionesDlqBinding()` | `cola.notificaciones.dlq` enlazada a `pedidos.dlx` |
| `colaAuditoria()` | `cola.auditoria` → DLX `pedidos.dlx`, routing key `cola.auditoria.dlq` |
| `colaAuditoriaDlq()` + `auditoriaDlqBinding()` | `cola.auditoria.dlq` enlazada a `pedidos.dlx` |
| `jsonMessageConverter()` | Conversor JSON |

#### a) `consumer/ReportConsumer.java` — Kardex (`cola.notificaciones`)

```java
@RabbitListener(queues = RabbitMQConfig.COLA_NOTIFICACIONES)
public void registrarKardex(Map<String, Object> evento) {
    log.info("Evento recibido en {}: {}", RabbitMQConfig.COLA_NOTIFICACIONES, evento);

    Object productoId = evento.get("productoId");
    Object tipo = evento.get("tipo");

    if (!(productoId instanceof Number id) || id.longValue() <= 0
            || !(tipo instanceof String t) || t.isBlank()) {
        log.error("[GRAVE] Evento de Kardex invalido (productoId={}, tipo={}). Se envia a {}", ...);
        throw new IllegalArgumentException("Evento de Kardex invalido: se requiere productoId > 0 y tipo");
    }

    log.info("Kardex actualizado: {} | producto {} ({} - {}) | stock={} | fecha={}", ...);
}
```

**Regla de validación:** si `productoId` falta o es `<= 0`, o si `tipo` falta o está vacío → log `[GRAVE]` + `IllegalArgumentException` → `cola.notificaciones.dlq`.

> report-service **no valida el stock**, por eso un producto con stock 0 sí se registra en el Kardex (mientras que inventory-service lo rechaza).

#### b) `consumer/AuditoriaConsumer.java` — Auditoría (`cola.auditoria`)

```java
@RabbitListener(queues = RabbitMQConfig.COLA_AUDITORIA)
public void registrarAuditoria(Map<String, Object> evento) {
    log.info("Evento recibido en {}: {}", RabbitMQConfig.COLA_AUDITORIA, evento);

    Object productoId = evento.get("productoId");
    Object tipo = evento.get("tipo");
    Object codigo = evento.get("codigo");
    Object fecha = evento.get("fecha");

    if (!(productoId instanceof Number id) || id.longValue() <= 0
            || esTextoVacio(tipo) || esTextoVacio(codigo) || esTextoVacio(fecha)) {
        log.error("[GRAVE] Evento de auditoria invalido (productoId={}, tipo={}, codigo={}, fecha={}). Se envia a {}", ...);
        throw new IllegalArgumentException(
                "Evento de auditoria invalido: se requiere productoId > 0, tipo, codigo y fecha");
    }

    log.info("Auditoria registrada: {} | producto {} ({}) | fecha={}", tipo, productoId, codigo, fecha);
}
```

**Regla de validación:** una auditoría necesita saber **qué pasó, a qué producto y cuándo**. Si `productoId` falta o es `<= 0`, o si `tipo`, `codigo` o `fecha` faltan o están vacíos → log `[GRAVE]` + `IllegalArgumentException` → `cola.auditoria.dlq`.

> Al crear un producto desde la API, `codigo` siempre viene (es obligatorio), así que para probar la DLQ de auditoría se publica a mano un mensaje inválido desde la interfaz de RabbitMQ (ver [Paso 8](#paso-8--prueba-de-la-dlq-de-auditoría)).

---

### 4.5 Commits realizados

| Commit | Mensaje |
|--------|---------|
| `766f487` | feat: agrega clúster RabbitMQ en docker-compose |
| `3ae2536` | fix: corrige la unión de rabbitmq2 al clúster |
| `30de1c9` | feat: productor, admin y config RabbitMQ en product-service |
| `a351925` | feat: consumidores RabbitMQ y validaciones DLQ en inventory y report |
| `7c3680a` | refactor: encapsula RabbitAdmin en service, agrega validaciones y tercera cola de auditoria |
| (último) | feat: activa cola.auditoria con consumidor, refactoriza admin service y actualiza documentacion |

Una copia de este documento está en el repositorio, en `Backend_tec/docs/Implementacion_RabbitMQ_y_Pruebas.md`.

---

## 5. Pruebas realizadas y resultados

Todas estas pruebas se ejecutaron y **pasaron correctamente**.

### Prueba 1 — El clúster se forma con 2 nodos

Comando:
```powershell
docker exec rabbitmq1 rabbitmqctl cluster_status
```
Resultado:
```
Disk Nodes
rabbit@rabbitmq1
rabbit@rabbitmq2

Running Nodes
rabbit@rabbitmq1
rabbit@rabbitmq2
```
- Ambas interfaces web (http://localhost:15672 y http://localhost:15673) mostraban los 2 nodos en ejecución.
- Se reinició `rabbitmq2` (`docker restart rabbitmq2`) y **volvió a unirse solo** al clúster. ✅

### Prueba 2 — Los microservicios arrancan y los consumidores se conectan

| Servicio | Puerto | Estado |
|----------|--------|--------|
| auth-service | 8081 | ✅ Arriba |
| category-service | 8082 | ✅ Arriba |
| product-service | 8083 | ✅ Arriba |
| inventory-service | 8084 | ✅ Arriba |
| report-service | 8085 | ✅ Arriba |

En RabbitMQ (`rabbitmqctl list_queues name consumers messages`):

| Cola | Consumidores |
|------|-------------|
| `cola.inventario` | 1 (inventory-service) ✅ |
| `cola.notificaciones` | 1 (report-service) ✅ |
| `cola.auditoria` | 1 (report-service) ✅ |

### Prueba 3 — Producto válido: el evento llega a los tres consumidores

Se creó el producto **AUD-001 (Monitor), stock 5** → id 33.

| Servicio | Línea en el log |
|----------|-----------------|
| product-service | `Evento PRODUCTO_CREADO publicado para producto 33` |
| inventory-service | `Inventario procesado: evento PRODUCTO_CREADO del producto 33 (AUD-001), stock=5` |
| report-service (Kardex) | `Kardex actualizado: PRODUCTO_CREADO \| producto 33 (AUD-001 - Monitor) \| stock=5 \| fecha=2026-10-19T00:25:41.433450100` |
| report-service (Auditoría) | `Auditoria registrada: PRODUCTO_CREADO \| producto 33 (AUD-001) \| fecha=2026-10-19T00:25:41.433450100` |

Colas principales: todas en **0 mensajes** (se consumieron al instante). ✅

(Antes de agregar la cola de auditoría también se probó con `RMQ-001`, `PRUEBA-01` y `PRUEBA-02`, con el mismo resultado en inventario y Kardex.)

### Prueba 4 — Producto inválido (stock 0): el mensaje va a la DLQ de inventario

Se creó el producto **PRUEBA-03 (Teclado), stock 0** → id 8.

| Servicio | Línea en el log |
|----------|-----------------|
| product-service | `Evento PRODUCTO_CREADO publicado para producto 8` |
| inventory-service | `ERROR ... [GRAVE] Evento de inventario invalido (productoId=8, stock=0). Se envia a cola.inventario.dlq` |
| report-service | `Kardex actualizado: PRODUCTO_CREADO \| producto 8 (PRUEBA-03 - Teclado) \| stock=0 \| fecha=...` |

Después: **`cola.inventario.dlq` = 1 mensaje**, el resto de las colas en 0. ✅

Esto demuestra que el mensaje inválido **no se perdió ni quedó en un bucle infinito**: RabbitMQ lo apartó en la DLQ.

### Prueba 5 — Mensaje de auditoría inválido: va a la DLQ de auditoría

Desde la API HTTP de administración de RabbitMQ se publicó en `pedidos.direct` (routing key `cola.auditoria`) un mensaje **sin `codigo`**:

```json
{"tipo":"PRODUCTO_CREADO","productoId":99,"fecha":"2026-10-19T00:30:00"}
```

| Dónde | Resultado |
|-------|-----------|
| report-service | `ERROR ... [GRAVE] Evento de auditoria invalido (productoId=99, tipo=PRODUCTO_CREADO, codigo=null, fecha=2026-10-19T00:30:00). Se envia a cola.auditoria.dlq` |
| `cola.auditoria` | 0 mensajes |
| **`cola.auditoria.dlq`** | **1 mensaje** ✅ |

### Prueba 6 — Administración de RabbitMQ y validación de nombres

| # | Petición | Respuesta |
|---|----------|-----------|
| 1 | `POST /queues/cola%20mala` (nombre con espacio) | **400** `"El nombre de la cola no puede contener espacios: 'cola mala'"` ✅ |
| 2 | `POST /queues/%20%20` (nombre en blanco) | **400** `"El nombre de la cola es obligatorio"` ✅ |
| 3 | `POST /queues/cola.prueba` | **201** `{"queue":"cola.prueba","status":"creada"}` ✅ |
| 4 | `POST /exchanges/pedidos.prueba?type=fanout` | **201** `{"exchange":"pedidos.prueba","type":"fanout","status":"creado"}` ✅ |
| 5 | `POST /exchanges/ex.malo?type=otro` | **400** `"Tipo de exchange invalido: 'otro'. Valores permitidos: direct, topic, fanout"` ✅ |
| 6 | `POST /bindings?queue=cola.prueba&exchange=pedidos.prueba` | **201**; el binding `pedidos.prueba → cola.prueba` apareció en RabbitMQ ✅ |
| 7 | `POST /bindings?queue=cola.prueba` (sin exchange) | **400** `"Falta el parametro obligatorio 'exchange'"` ✅ |
| 8 | `DELETE /bindings?queue=cola.prueba&exchange=pedidos.prueba` | **204** ✅ |
| 9 | `DELETE /exchanges/pedidos.prueba` | **204** ✅ |
| 10 | `DELETE /queues/cola.prueba` | **204**; la cola y el exchange de prueba desaparecieron de RabbitMQ ✅ |
| 11 | `POST /queues/x` **sin token** | **401** ✅ |

(Todas las rutas empiezan con `http://localhost:8083/api/v1/admin/rabbitmq`.)

---

## 6. Cómo probarlo tú mismo paso a paso

### Requisitos previos

- **Docker Desktop** instalado y **abierto** (el ícono de la ballena debe estar activo).
- **Java 17** y **Maven** instalados.
- **Postman** instalado.

---

### Paso 1 — Levantar el clúster RabbitMQ

1. Abre PowerShell.
2. Ve a la carpeta del backend:
   ```powershell
   cd C:\Users\Marti\5to_semestre\Cloud_Native_I\CLOUD_NATIVE_I\Backend_tec
   ```
3. Levanta los contenedores:
   ```powershell
   docker compose up -d
   ```
4. Espera unos 30–60 segundos y verifica que ambos estén arriba:
   ```powershell
   docker ps
   ```
   Deben aparecer `rabbitmq1` (con `healthy`) y `rabbitmq2`.

### Paso 2 — Verificar que el clúster se formó

```powershell
docker exec rabbitmq1 rabbitmqctl cluster_status
```

✅ En la sección **Running Nodes** deben aparecer **`rabbit@rabbitmq1` y `rabbit@rabbitmq2`**.

Si solo aparece `rabbitmq1`, espera unos segundos más y repite (el nodo 2 tarda un poco en unirse).

### Paso 3 — Abrir la interfaz web de RabbitMQ

1. Abre el navegador en **http://localhost:15672**.
2. Usuario: `guest` — Contraseña: `guest`.
3. En la pestaña **Overview**, sección **Nodes**, verás los 2 nodos.
4. Ve a la pestaña **Queues and Streams** (o **Queues**). Déjala abierta.

(También puedes entrar al nodo 2 en http://localhost:15673 y verás la misma información, porque es un clúster.)

### Paso 4 — Levantar los microservicios

Abre **una ventana de PowerShell por cada servicio** (5 ventanas). En cada una ejecuta:

**Ventana 1 — auth-service:**
```powershell
cd C:\Users\Marti\5to_semestre\Cloud_Native_I\CLOUD_NATIVE_I\Backend_tec\auth-service
mvn spring-boot:run
```

**Ventana 2 — category-service:**
```powershell
cd C:\Users\Marti\5to_semestre\Cloud_Native_I\CLOUD_NATIVE_I\Backend_tec\category-service
mvn spring-boot:run
```

**Ventana 3 — product-service:**
```powershell
cd C:\Users\Marti\5to_semestre\Cloud_Native_I\CLOUD_NATIVE_I\Backend_tec\product-service
mvn spring-boot:run
```

**Ventana 4 — inventory-service:**
```powershell
cd C:\Users\Marti\5to_semestre\Cloud_Native_I\CLOUD_NATIVE_I\Backend_tec\inventory-service
mvn spring-boot:run
```

**Ventana 5 — report-service:**
```powershell
cd C:\Users\Marti\5to_semestre\Cloud_Native_I\CLOUD_NATIVE_I\Backend_tec\report-service
mvn spring-boot:run
```

✅ Cada servicio está listo cuando aparece una línea como:
```
Started ProductServiceApplication in 13.427 seconds
```

> **Estas ventanas son los logs.** Ahí verás en vivo los mensajes que se envían y reciben. Para las pruebas, fíjate sobre todo en las ventanas **3 (product)**, **4 (inventory)** y **5 (report)**.

✅ En la interfaz de RabbitMQ (pestaña Queues), `cola.inventario`, `cola.notificaciones` y `cola.auditoria` deben mostrar **Consumers = 1**.

### Paso 5 — Obtener el token en Postman

1. En Postman, crea una petición nueva.
2. Método: **POST**
3. URL: `http://localhost:8081/api/auth/login`
4. Pestaña **Body** → selecciona **raw** → a la derecha cambia **Text** por **JSON**.
5. Pega:
   ```json
   {
       "username": "admin",
       "password": "admin123"
   }
   ```
6. Clic en **Send**.
7. En la respuesta, copia el valor de `"token"` (el texto largo que empieza con `eyJ...`), **sin las comillas**.

El token dura 8 horas.

### Paso 6 — Prueba de mensaje válido (crear producto)

1. Crea otra petición nueva en Postman.
2. Método: **POST** (⚠️ no GET; GET solo lista productos).
3. URL: `http://localhost:8083/api/productos`
4. Pestaña **Authorization** → **Auth Type: Bearer Token** → pega el token.
5. Pestaña **Body** → **raw** → **JSON**:
   ```json
   {
       "codigo": "PRUEBA-10",
       "nombre": "Mouse",
       "categoriaId": 1,
       "precio": 5000,
       "stock": 10,
       "stockMinimo": 2
   }
   ```
6. Clic en **Send**.

> Los códigos de producto no se pueden repetir. Si `PRUEBA-10` ya existe, usa otro (`PRUEBA-11`, `PRUEBA-12`, ...).

**✅ Qué debes ver:**

| Dónde | Qué buscar |
|-------|-----------|
| Postman | **201 Created** y los datos del producto con su `id` |
| Ventana product-service | `Evento PRODUCTO_CREADO publicado para producto <id>` |
| Ventana inventory-service | `Inventario procesado: evento PRODUCTO_CREADO del producto <id> (PRUEBA-10), stock=10` |
| Ventana report-service | `Kardex actualizado: PRODUCTO_CREADO \| producto <id> (PRUEBA-10 - Mouse) \| stock=10 ...` **y** `Auditoria registrada: PRODUCTO_CREADO \| producto <id> (PRUEBA-10) \| fecha=...` |
| RabbitMQ (F5) | `cola.inventario`, `cola.notificaciones` y `cola.auditoria` en **0 mensajes** |

> ¿Por qué las colas quedan en 0? Porque los consumidores leen el mensaje al instante. Si se acumularan mensajes en alguna cola principal, significaría que su consumidor no está funcionando.

### Paso 7 — Prueba de la DLQ de inventario (stock 0)

1. En la misma petición de Postman, cambia el Body por (fíjate en **`"stock": 0`** y en un **código nuevo**):
   ```json
   {
       "codigo": "PRUEBA-20",
       "nombre": "Teclado",
       "categoriaId": 1,
       "precio": 8000,
       "stock": 0,
       "stockMinimo": 2
   }
   ```
2. Clic en **Send**.

**✅ Qué debes ver:**

| Dónde | Qué buscar |
|-------|-----------|
| Postman | **201 Created** (el producto **sí se guarda**; lo que falla es solo el procesamiento en inventory) |
| Ventana product-service | `Evento PRODUCTO_CREADO publicado para producto <id>` |
| Ventana inventory-service | Línea **ERROR** con `[GRAVE] Evento de inventario invalido (productoId=<id>, stock=0). Se envia a cola.inventario.dlq`, seguida de varias líneas de error (stack trace). **Es normal**: es la excepción provocada a propósito. |
| Ventana report-service | `Kardex actualizado: ... stock=0` y `Auditoria registrada: ...` (report no valida el stock) |
| RabbitMQ (F5) | **`cola.inventario.dlq` sube en 1 mensaje** |

### Paso 8 — Prueba de la DLQ de auditoría

Como al crear productos desde la API el `codigo` siempre viene, aquí se publica **a mano** un mensaje inválido desde la interfaz de RabbitMQ:

1. En http://localhost:15672, ve a la pestaña **Exchanges** y haz clic en **`pedidos.direct`**.
2. Baja hasta la sección **Publish message**.
3. Completa:
   - **Routing key:** `cola.auditoria`
   - **Properties:** escribe `content_type` en el primer campo y `application/json` en el segundo.
   - **Payload** (fíjate que **no** tiene `codigo`):
     ```json
     {"tipo":"PRODUCTO_CREADO","productoId":99,"fecha":"2026-10-19T00:30:00"}
     ```
4. Clic en **Publish message**. Debe aparecer el aviso **"Message published"**.

**✅ Qué debes ver:**

| Dónde | Qué buscar |
|-------|-----------|
| Ventana report-service | Línea **ERROR** con `[GRAVE] Evento de auditoria invalido (productoId=99, tipo=PRODUCTO_CREADO, codigo=null, fecha=...). Se envia a cola.auditoria.dlq` (más el stack trace, es normal) |
| RabbitMQ → Queues (F5) | **`cola.auditoria.dlq` sube en 1 mensaje** |

### Paso 9 — Ver los mensajes que cayeron en una DLQ

1. En RabbitMQ, pestaña **Queues**, haz clic en **`cola.inventario.dlq`** (o **`cola.auditoria.dlq`**).
2. Baja hasta la sección **Get messages**.
3. Clic en **Get Message(s)**.
4. Verás el JSON del mensaje rechazado. En sus propiedades aparece el header `x-death`, que indica de qué cola vino y por qué fue rechazado.

### Paso 10 — Probar la administración de RabbitMQ y la validación de nombres

En todas estas peticiones usa en Postman **Authorization → Bearer Token** con tu token, y **Body vacío**.

**a) Validación: nombre con espacio → 400**
1. Método **POST**, URL: `http://localhost:8083/api/v1/admin/rabbitmq/queues/cola mala`
   (Postman convierte el espacio en `%20` automáticamente.)
2. **Send** → debe responder **400 Bad Request** con `"El nombre de la cola no puede contener espacios: 'cola mala'"`.

**b) Crear una cola válida → 201**
1. Método **POST**, URL: `http://localhost:8083/api/v1/admin/rabbitmq/queues/cola.prueba`
2. **Send** → **201** con `{"queue":"cola.prueba","status":"creada"}`.
3. En RabbitMQ (F5) aparece la cola `cola.prueba`.

**c) Crear un exchange → 201**
1. Método **POST**, URL: `http://localhost:8083/api/v1/admin/rabbitmq/exchanges/pedidos.prueba?type=fanout`
2. **Send** → **201**. En RabbitMQ → **Exchanges** aparece `pedidos.prueba` de tipo `fanout`.

**d) Enlazar la cola al exchange → 201**
1. Método **POST**, URL: `http://localhost:8083/api/v1/admin/rabbitmq/bindings?queue=cola.prueba&exchange=pedidos.prueba`
2. **Send** → **201**. En RabbitMQ, al abrir `cola.prueba`, sección **Bindings**, aparece el enlace desde `pedidos.prueba`.

**e) Limpiar (borrar en orden inverso) → 204**
1. Método **DELETE**, URL: `http://localhost:8083/api/v1/admin/rabbitmq/bindings?queue=cola.prueba&exchange=pedidos.prueba` → **204**
2. Método **DELETE**, URL: `http://localhost:8083/api/v1/admin/rabbitmq/exchanges/pedidos.prueba` → **204**
3. Método **DELETE**, URL: `http://localhost:8083/api/v1/admin/rabbitmq/queues/cola.prueba` → **204**
4. En RabbitMQ (F5), la cola y el exchange de prueba ya no aparecen.

> ⚠️ No borres las colas o exchanges del sistema (`cola.inventario`, `pedidos.direct`, etc.), o los mensajes dejarán de llegar hasta que reinicies los servicios.

### Paso 11 (opcional) — Probar la tolerancia del clúster

1. Reinicia el nodo 2:
   ```powershell
   docker restart rabbitmq2
   ```
2. Espera ~30 segundos y verifica:
   ```powershell
   docker exec rabbitmq1 rabbitmqctl cluster_status
   ```
3. ✅ `rabbit@rabbitmq2` vuelve a aparecer en **Running Nodes**.

### Alternativa: hacer las pruebas desde PowerShell (sin Postman)

```powershell
# 1. Obtener el token
$login = Invoke-RestMethod -Method Post http://localhost:8081/api/auth/login -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$h = @{ Authorization = "Bearer $($login.token)" }

# 2. Producto válido (llega a inventario, Kardex y auditoría)
Invoke-RestMethod -Method Post http://localhost:8083/api/productos -Headers $h -ContentType "application/json" -Body '{"codigo":"PRUEBA-30","nombre":"Mouse","categoriaId":1,"precio":5000,"stock":10,"stockMinimo":2}'

# 3. Producto inválido para inventario (va a cola.inventario.dlq)
Invoke-RestMethod -Method Post http://localhost:8083/api/productos -Headers $h -ContentType "application/json" -Body '{"codigo":"PRUEBA-31","nombre":"Teclado","categoriaId":1,"precio":8000,"stock":0,"stockMinimo":2}'

# 4. Mensaje inválido para auditoría (va a cola.auditoria.dlq)
$pub = '{"properties":{"content_type":"application/json"},"routing_key":"cola.auditoria","payload":"{\"tipo\":\"PRODUCTO_CREADO\",\"productoId\":99,\"fecha\":\"2026-10-19T00:30:00\"}","payload_encoding":"string"}'
$rabbit = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("guest:guest")) }
Invoke-RestMethod -Method Post "http://localhost:15672/api/exchanges/%2F/pedidos.direct/publish" -Headers $rabbit -ContentType "application/json" -Body $pub

# 5. Validación de nombre (debe fallar con 400)
try { Invoke-RestMethod -Method Post "http://localhost:8083/api/v1/admin/rabbitmq/queues/cola%20mala" -Headers $h } catch { $_.Exception.Response.StatusCode.value__ }

# 6. Ver cuántos mensajes y consumidores hay en cada cola
docker exec rabbitmq1 rabbitmqctl list_queues name consumers messages
```

---

## 7. Errores comunes y cómo resolverlos

| Síntoma | Causa | Solución |
|---------|-------|----------|
| `failed to connect to the docker API ... dockerDesktopLinuxEngine` | Docker Desktop está cerrado | Abre Docker Desktop y espera a que termine de iniciar |
| `Port 8083 was already in use` al levantar un servicio | Ese servicio ya está corriendo en otra ventana o en segundo plano | Cierra la otra instancia (Ctrl + C) o detén el proceso `java` que usa el puerto |
| Login responde **415** | Falta `Content-Type: application/json` | En Postman: Body → **raw** → **JSON** |
| Crear producto responde **401** | Falta el token, está mal copiado (con comillas o espacios) o venció | Repite el login (Paso 5) y pega el token en Authorization → Bearer Token |
| Al "crear" el producto solo devuelve una lista | Se usó **GET** en vez de **POST** | Cambia el método a **POST** |
| **400** "Ya existe un producto con el codigo..." | El código ya se usó | Usa otro código |
| **400** con mensaje de validación del producto | Falta un campo obligatorio o tiene un valor inválido | Revisa que el JSON tenga `codigo`, `nombre`, `categoriaId`, `precio`, `stock`, `stockMinimo` |
| **400** "El nombre de la cola no puede contener espacios" | El nombre de la cola tiene un espacio | Usa puntos o guiones (`cola.prueba`, `cola-prueba`) |
| El producto con stock 0 **no** llegó a la DLQ | Se dejó `"stock": 10` | Asegúrate de poner `"stock": 0` |
| El mensaje publicado a mano no llegó a `cola.auditoria.dlq` | Se publicó en otro exchange o con otra routing key | Publica en `pedidos.direct` con routing key `cola.auditoria` |
| En product-service: `No se pudo publicar el evento...` | RabbitMQ está apagado o no se puede conectar | Revisa `docker ps` y levanta el clúster (Paso 1) |
| Mensajes acumulándose en una cola principal | Su consumidor (inventory o report) no está corriendo | Revisa que su ventana esté activa y sin errores |
| `cluster_status` muestra solo `rabbitmq1` | El nodo 2 aún no termina de unirse | Espera unos segundos y repite; revisa `docker logs rabbitmq2` |

---

## 8. Cómo detener todo

1. **Microservicios:** en cada una de las 5 ventanas de PowerShell presiona **Ctrl + C**.
2. **Clúster RabbitMQ:**
   ```powershell
   cd C:\Users\Marti\5to_semestre\Cloud_Native_I\CLOUD_NATIVE_I\Backend_tec
   docker compose down
   ```

> `docker compose down` borra los contenedores, y con ellos las colas y los mensajes (incluidos los de las DLQ). La próxima vez que levantes todo, las colas se vuelven a crear automáticamente.
