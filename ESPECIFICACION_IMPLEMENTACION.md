# Especificación de Implementación — Cluster Hospital 5 Nodos

## Arquitectura general

```
┌─────────────────────────────────────────────────────────────┐
│                    Cluster (5 nodos)                         │
│                                                              │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│  │ Nodo 1   │   │ Nodo 2   │   │ Nodo 3   │   │ Nodo 4   │   │ Nodo 5   │
│  │ :8081    │   │ :8082    │   │ :8083    │   │ :8084    │   │ :8085    │
│  │ :9000    │   │ :9000    │   │ :9000    │   │ :9000    │   │ :9000    │
│  └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘   └────┬─────┘
│       │              │              │              │              │
│       └──────────────┴──────────────┴──────────────┴──────────────┘
│                          TCP sockets (puerto 9000)
└─────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │                   │
            PostgreSQL          HTTP (API REST)
          (BD compartida)       :8081-8085
```

**Comunicación dual por nodo:**
- **HTTP** (Spring Boot, puertos 8081-8085) — API REST pública (`/api/donantes`, `/api/reservas`)
- **TCP** (ServerSocket, puerto 9000) — mensajes internos del cluster (HEARTBEAT, ELECTION, TOKEN, TIME_REQUEST, etc.)

**BD compartida:** una sola PostgreSQL, todos los nodos apuntan a la misma base de datos. La exclusión mutua del Token Ring opera a nivel de aplicación, no de base de datos.

---

## Protocolo de mensajes (TCP, puerto 9000)

### Formato
```
TIPO|origen|destino|payload
```

Separador: `|` (pipe). Todo en una línea de texto terminada en `\n`.

### Tipos de mensaje

| Tipo | Origen → Destino | Payload | Propósito |
|---|---|---|---|---|
| `HEARTBEAT` | nodo → coordinador | — | Latido periódico (cada 2s) |
| `HEARTBEAT_OK` | coordinador → nodo | — | Confirmación de heartbeat |
| `ELECTION` | iniciador → nodo_mayor | — | Inicia elección Bully (un socket por destino) |
| `OK` | nodo_mayor → iniciador | — | "Sigo vivo, continúa elección" |
| `COORDINATOR` | ganador → `*` | `orden_anillo=1,2,4,5` | Broadcast de nuevo coordinador tras elección Bully |
| `RING_UPDATE` | coordinador → `*` | `orden_anillo=1,2,4,5` | Reconfiguración del anillo por caída (sin cambio de líder) |
| `TOKEN` | actual → siguiente | — | Paso del testigo en el anillo |
| `TOKEN_LOST` | reportante → coordinador | `nodo_sospechoso=X` | Reporte de token no confirmado |
| `TOKEN_RETRY` | coordinador → reportante | `destino=X` | "Falso positivo, reintenta envío a X" |
| `TOKEN_RESEND` | coordinador → reportante | `destino=Y` | "X cayó, reenvía token al nuevo siguiente Y" |
| `TIME_REQUEST` | nodo → servidor_tiempo | — | Solicitud de timestamp (Cristian) |
| `TIME_RESPONSE` | servidor → nodo | `timestamp` | Respuesta con timestamp actual |

### Ejemplos de mensajes
```
HEARTBEAT|3|5|
ELECTION|2|4|
COORDINATOR|5|*|orden_anillo=1,2,4,5
RING_UPDATE|5|*|orden_anillo=1,2,4,5
TOKEN_LOST|3|5|nodo_sospechoso=2
TOKEN_RESEND|5|3|destino=4
TIME_RESPONSE|5|3|1718000000123
```

---

## 1. `MensajeCluster.java` — `model/` ✅ IMPLEMENTADO

### Propósito
Representa un mensaje intercambiado entre nodos del cluster. Define el parseo y serialización del protocolo de texto plano.

### Enumeración `TipoMensaje`
```java
HEARTBEAT, HEARTBEAT_OK, ELECTION, OK, COORDINATOR, RING_UPDATE,
TOKEN, TOKEN_LOST, TOKEN_RETRY, TOKEN_RESEND,
TIME_REQUEST, TIME_RESPONSE
```

### Atributos
```java
private TipoMensaje tipo;
private int origen;
private int destino;       // -1 para broadcast (*)
private String payload;    // null si no aplica
```

### Métodos
- `static MensajeCluster parsear(String linea)` — parsea línea `"TIPO|origen|destino|payload"` desde el socket
- `String aTextoPlano()` — serializa a `"TIPO|origen|destino|payload\n"`
- Getters para todos los campos

### Casos borde
- Payload vacío → string `""`, no `null`
- Destino `*` → se almacena como `-1` internamente
- Línea mal formada → lanza `IllegalArgumentException`

---

## 2. `EstadoCluster.java` — `infrastructure/` ✅ IMPLEMENTADO

### Propósito
Fuente de verdad local del estado del nodo en el cluster. Toda modificación es `synchronized` porque es compartida entre hilos (listener, servicios, scheduler).

### Enumeración `EstadoNodo`
```java
NORMAL, EN_ELECCION, COORDINADOR
```

### Atributos
```java
private int idPropio;
private Map<Integer, String> peers;           // ID -> "IP:9000"
private int coordinadorActual;                // ID del coordinador actual
private EstadoNodo estado;                    // NORMAL | EN_ELECCION | COORDINADOR
private int siguienteEnAnillo;                // ID del siguiente en el anillo
private boolean tieneToken;                   // ¿Posee este nodo el token?
private Long offsetReloj;                     // Offset de Cristian (ms), null si no sincronizado
private Integer nodoCongeladoReportante;       // ID del nodo que reportó TOKEN_LOST (nullable)
```

### Métodos principales

| Método | Descripción |
|---|---|
| `void inicializar(int idPropio, Map<Integer, String> peers)` | Configura nodo: fija coordinador inicial = ID más alto de `peers`; siguiente en anillo = ID más cercano superior (o el menor si es el mayor); token = true solo si es el ID más bajo |
| `synchronized void marcarCoordinador(int id)` | Actualiza `coordinadorActual` y `estado` |
| `synchronized void configurarAnillo(List<Integer> orden)` | Recibe lista ordenada y calcula `siguienteEnAnillo` |
| `synchronized boolean tieneToken()` | Consulta si posee el token |
| `synchronized void darToken()` | Marca `tieneToken = true` |
| `synchronized void quitarToken()` | Marca `tieneToken = false` |
| `synchronized void congelarToken()` | Marca estado "en espera de instrucción del coordinador" (no asume tener ni no tener el token) |
| `synchronized void ajustarOffsetReloj(long offset)` | Guarda offset calculado por Cristian |
| `synchronized List<Integer> nodosConIdMayor(int id)` | Devuelve IDs de peers vivos con ID > `id` |
| `synchronized void removerNodo(int id)` | Elimina nodo caído de `peers` |
| `synchronized boolean estaVivo(int id)` | Consulta si un nodo está en la tabla de miembros |
| `synchronized void setNodoCongeladoReportante(Integer id)` | Guarda el nodo que reportó TOKEN_LOST y espera instrucción |
| `synchronized Integer getNodoCongeladoReportante()` | Recupera el nodo congelado (null si no hay) |
| `synchronized void limpiarNodoCongeladoReportante()` | Resetea después de resolver el TOKEN_LOST |

### Coordinador inicial
En `inicializar()`, el coordinador inicial es el nodo con el **ID más alto** de la lista de peers (coherente con Bully, que elige al de mayor ID).

### Token inicial
Lo tiene el nodo con el **ID más bajo** al arrancar (regla determinista, asimétrica respecto a Bully — decisión de diseño consciente).

---

## 3. `ClusterSocketListener.java` — `infrastructure/` ✅ IMPLEMENTADO

### Propósito
Implementa un `ServerSocket` en puerto `9000` que acepta conexiones entrantes de otros nodos, parsea el mensaje y lo despacha al servicio correspondiente.

### Comportamiento
- Implementa `CommandLineRunner` (se ejecuta tras arrancar Spring Boot)
- Lanza un `ExecutorService` con un hilo dedicado al loop de `accept()`
- `ServerSocket` con `SO_TIMEOUT = 1000ms` para poder interrumpir limpiamente
- Por cada conexión entrante: lee una línea con `BufferedReader`, parsea con `MensajeCluster.parsear()`, y redirige:

| Tipo de mensaje | Servicio destino |
|---|---|---|
| `HEARTBEAT`, `HEARTBEAT_OK`, `ELECTION`, `OK`, `COORDINATOR`, `RING_UPDATE`, `TOKEN_LOST` | `ServicioEleccionBully` |
| `TOKEN` | `ServicioAnilloToken` (con respuesta síncrona en el mismo socket) |
| `TOKEN_RETRY`, `TOKEN_RESEND` | `ServicioAnilloToken` |
| `TIME_REQUEST`, `TIME_RESPONSE` | `Cristian` |

### Shutdown graceful
- `@PreDestroy` en el bean
- `executorService.shutdown()` + `awaitTermination(5s, TimeUnit.SECONDS)`
- `serverSocket.close()` para liberar el puerto

### Atributos
```java
private ServerSocket serverSocket;
private ExecutorService executorService;
private final EstadoCluster estadoCluster;
private final ServicioEleccionBully servicioBully;
private final ServicioAnilloToken servicioAnillo;
private final Cristian servicioTiempo;
```

---

## 4. `Cristian.java` — `service/` ✅ IMPLEMENTADO

### Propósito
Implementa el **Algoritmo de Cristian** para sincronizar el reloj local con un servidor de tiempo (nodo 5, coordinador inicial).

### Cuándo se ejecuta
Una sola vez al arrancar la aplicación.

### Flujo
1. Esperar a que el `ClusterSocketListener` esté listo
2. Enviar `TIME_REQUEST` al servidor de tiempo (nodo servidor configurado)
3. Recibir `TIME_RESPONSE` con timestamp del servidor
4. Calcular offset: `T_servidor + (RTT / 2) - T_local`
5. Almacenar en `EstadoCluster.ajustarOffsetReloj(offset)`
6. Si la conexión falla: reintentar cada 2s hasta 10 intentos

### No depende de
- Bully ni Ring. Corre de forma independiente y es el primer algoritmo en ejecutarse.

### Robustez de arranque
- Los 5 nodos no arrancan al mismo tiempo. El servidor de tiempo podría no estar listo cuando un nodo intenta sincronizar. Por eso los reintentos con backoff simple (2s entre intentos, máx 10).

---

## 5. `ServicioAnilloToken.java` — `service/` ✅ IMPLEMENTADO

### Propósito
Implementa el **Algoritmo de Anillo (Token Ring)** para exclusión mutua a nivel de aplicación. Solo el nodo que posee el token puede procesar una operación de reserva.

### Reglas de circulación
- **Token inicial**: lo tiene el nodo con el ID más bajo al arrancar
- **Paso del token**: el nodo actual envía `TOKEN` al `siguienteEnAnillo`, espera confirmación
- **Retención máxima**: ~2 segundos (si no hay operación pendiente, se pasa igual para mantener circulación)
- **Confirmación**: el nodo destino recibe el `TOKEN`, marca `tieneToken = true`, y responde con `TOKEN|destino|origen|OK`

### Timeout — Flujo TOKEN_LOST (CORREGIDO)

```
Nodo A envía TOKEN → Nodo X          [espera ACK]
Pasan 4 segundos → No llega ACK

Nodo A:
  1. Se CONGELA (no asume tener ni no tener el token)
  2. Envía TOKEN_LOST|A|coordinador|nodo_sospechoso=X
  3. Espera instrucción del coordinador

Coordinador recibe TOKEN_LOST:
  1. Verifica heartbeat de X
     ├─ X responde → FALSO POSITIVO (glitch de red)
     │   └→ Coordinador envía TOKEN_RETRY|coord|A|destino=X
     │      → A reintenta envío de TOKEN a X
     │
     └─ X no responde → CONFIRMADO CAÍDO
         └→ Coordinador:
             1. Remueve X del anillo
             2. Broadcast RING_UPDATE con nuevo orden_anillo (sin X)
             3. Envía TOKEN_RESEND|coord|A|destino=Y
                (Y = nuevo siguiente de A según orden actualizado)
             → A reenvía TOKEN a Y
```

**Principio:** el coordinador **nunca toca el token**. Solo coordina la reconfiguración. El token siempre está en manos de un nodo (A), evitando la duplicación.

### Integración con `GestionReservas`
- `GestionReservas` debe consultar `EstadoCluster.tieneToken()` antes de procesar una reserva
- Si no tiene el token → responde HTTP `423 Locked`
- Esto es la "capa de exclusión mutua distribuida a nivel de aplicación"

### Atributos
```java
private final EstadoCluster estadoCluster;
private ScheduledExecutorService scheduler;
```

---

## 6. `ServicioEleccionBully.java` — `service/` ✅ IMPLEMENTADO

### Propósito
Implementa el **Algoritmo Bully** para elección de coordinador. Incluye heartbeats, detección de fallos, elección y reconfiguración del anillo.

### Heartbeats

| Parámetro | Valor |
|---|---|
| Intervalo | 2 segundos |
| Timeout sin respuesta | 5-6 segundos (~3 heartbeats perdidos) |
| Socket timeout conexión saliente | 500ms - 1s |
| Reintentos por conexión fallida | 1 reintento |

Mecanismo:
1. Cada 2s, el nodo envía `HEARTBEAT` al coordinador actual
2. El coordinador responde `HEARTBEAT_OK`
3. Si pasan 5-6s sin respuesta: se inicia elección

### Elección Bully

```
Nodo D detecta caída del coordinador:
  1. Obtener lista de nodos con ID mayor (vía EstadoCluster.nodosConIdMayor())
  
  2. SI lista vacía (soy el de mayor ID disponible):
       → Auto-proclamarse coordinador inmediatamente
       → Broadcast COORDINATOR con nuevo orden_anillo
  
  3. SI hay nodos mayores:
       → Enviar ELECTION a cada uno (un socket TCP por destino)
       → Esperar respuestas OK con timeout de 2s
       
       SI algún nodo responde OK:
         → La elección continúa en ese nodo (esperar su COORDINATOR)
       
       SI nadie responde OK:
         → Auto-proclamarse coordinador
         → Broadcast COORDINATOR con nuevo orden_anillo
```

### Al recibir mensajes de elección

| Mensaje recibido | Acción |
|---|---|---|
| `ELECTION` de ID menor | Enviar `OK` al origen. Iniciar propia elección si no estaba ya en una |
| `OK` | Un nodo mayor está vivo → esperar su `COORDINATOR` |
| `COORDINATOR` | Actualizar `coordinadorActual`. Recalcular anillo según `orden_anillo` del payload. Leer `EstadoCluster.getNodoCongeladoReportante()`. Si hay nodo congelado, enviarle `TOKEN_RESEND` con el nuevo destino |
| `RING_UPDATE` | Solo reconfigurar anillo vía `EstadoCluster.configurarAnillo()`. **No** cambiar `coordinadorActual`. Si hay nodo congelado, enviarle `TOKEN_RESEND` |

### Casos borde

| Caso | Comportamiento |
|---|---|
| Auto-proclamación (ID más alto vivo) | No envía ELECTION a nadie, se declara directo |
| Timeout conexión saliente (500ms-1s) + 1 reintento | Si falla, se trata como nodo caído |
| Timeout espera de OK (2s) | Si nadie responde, se auto-proclama |
| Múltiples nodos inician elección simultánea | Bully lo resuelve: el de mayor ID termina ganando |
| Reconfiguración del anillo post-elección | El `COORDINATOR` lleva el nuevo `orden_anillo` en el payload |

### Atributos
```java
private final EstadoCluster estadoCluster;
```

---

## 7. Nuevo: `InicializadorCluster.java` — `infrastructure/` ✅ IMPLEMENTADO

### Propósito
`@Component` con `@PostConstruct` que lee `node.id`, `cluster.peers` y `cluster.port`, construye el `Map<Integer, String>` de peers y llama `estadoCluster.inicializar()`. Corre antes que cualquier `CommandLineRunner` o scheduler gracias al flag `estaInicializado()`.

### Lógica
```java
int idPropio = Integer.parseInt(nodeId);
String[] ips = clusterPeers.split(",");
Map<Integer, String> peers = new HashMap<>();
for (int i = 0; i < ips.length; i++) {
    peers.put(i + 1, ips[i].trim() + ":" + clusterPort);
}
estadoCluster.inicializar(idPropio, peers);
```

---

## 8. Modificaciones a archivos existentes ✅ IMPLEMENTADO

### `NodeIdentity.java`
- **No modificar** `getNodeId()` existente (sigue siendo `String`, el logging actual no se rompe)
- **Agregado** `int getNodeIdAsInt()` — convierte y retorna el ID numérico para comparaciones

### `GestionReservas.java`
- Inyectado `EstadoCluster`
- Antes de procesar una reserva:
```java
if (!estadoCluster.tieneToken()) {
    throw new ResponseStatusException(HttpStatus.LOCKED,
        "Este nodo no posee el token de exclusion mutua");
}
```

### `application.properties`
```properties
# Agregado:
cluster.peers=${CLUSTER_PEERS:192.168.1.1,192.168.1.2,192.168.1.3,192.168.1.4,192.168.1.5}
cluster.port=9000
time.server.id=5
```

### `EstadoCluster.java`
- Agregado `volatile boolean inicializado`
- Marcado como `true` al final de `inicializar()`
- Método `estaInicializado()`

### Guards agregados en servicios
- `ServicioEleccionBully.cicloHeartbeat()`: `if (!estadoCluster.estaInicializado()) return;`
- `ServicioEleccionBully.verificarHeartbeats()`: `if (!estadoCluster.estaInicializado()) return;`
- `ServicioAnilloToken.intentarPasarToken()`: `if (!estadoCluster.estaInicializado()) return;`
- `ServicioAnilloToken.verificarTimeoutToken()`: `if (!estadoCluster.estaInicializado()) return;`
- `ServicioCristian.run()`: `while (!isListenerListo() || !estaInicializado())`

### `docker-compose.yml`
- Escalar a 5 servicios `app-node1` a `app-node5`
- Cada uno con:
  - `NODE_ID=1..5`
  - Puerto host único: `8081:8080` a `8085:8080`
  - `CLUSTER_PEERS` apuntando a los 5 (incluyéndose opcionalmente)
  - Misma BD compartida (`db`)

---

## Resumen de dependencias

```
ClusterSocketListener (recibe mensajes TCP)
  ├── HEARTBEAT / ELECTION / OK / COORDINATOR / RING_UPDATE / TOKEN_LOST → ServicioEleccionBully
  ├── TOKEN → ServicioAnilloToken (responde en mismo socket)
  ├── TOKEN_RETRY / TOKEN_RESEND → ServicioAnilloToken
  └── TIME_REQUEST / TIME_RESPONSE → Cristian

ServicioEleccionBully
  └── usa → EstadoCluster (consulta/modifica estado, tabla de miembros, nodo congelado)

ServicioAnilloToken
  └── usa → EstadoCluster (consulta token, siguiente en anillo, guarda nodo congelado)

Cristian
  └── usa → EstadoCluster (guarda offset de reloj)

GestionReservas
  └── consulta → EstadoCluster.tieneToken() antes de procesar reservas

NodeIdentity (existente)
  └── getNodeIdAsInt() agregado para compatibilidad

(No hay dependencia circular: Bully y Anillo se comunican solo a través de EstadoCluster)
```

---

## Orden de implementación sugerido

| Orden | Clase | Depende de |
|---|---|---|
| 1 | `MensajeCluster.java` ✅ | — |
| 2 | `EstadoCluster.java` ✅ | `MensajeCluster` |
| 3 | `ClusterSocketListener.java` ✅ | `MensajeCluster`, `EstadoCluster` |
| 4 | `Cristian.java` ✅ | `EstadoCluster` |
| 5 | `ServicioEleccionBully.java` ✅ | `EstadoCluster`, `ClusterSocketListener` |
| 6 | `ServicioAnilloToken.java` ✅ | `EstadoCluster`, `ClusterSocketListener` |
| 7 | `InicializadorCluster.java` (nuevo) ✅ | `EstadoCluster` |
| 8 | Modificar `GestionReservas.java` ✅ | `EstadoCluster` |
| 9 | Modificar `NodeIdentity.java` ✅ | — |
| 10 | Modificar `application.properties` ✅ | — |
| 11 | Modificar `docker-compose.yml` | — |
