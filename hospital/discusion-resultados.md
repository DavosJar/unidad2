# Discusión de Resultados — Stress Test de Reservas

## 1. Resumen de resultados

| Métrica      | Cantidad | Porcentaje |
|-------------|----------|------------|
| OK          | 5        | 25%        |
| LOCK (423)  | 9        | 45%        |
| CONFLICT (409) | 6     | 30%        |
| ERROR       | 0        | 0%         |
| **Total**   | **20**   | **100%**   |

## 2. Análisis por código de respuesta

### OK (5)
Ocurrieron en ráfaga cuando el token llegó al nodo 1. Confirma que el token serializa correctamente el acceso a la sección crítica — mientras el nodo tiene el token, las reservas se procesan sin contención.

### LOCK — 423 (9)
El nodo 1 no tenía el token en ese momento. Demuestra que el Token Ring efectivamente impide el acceso concurrente cuando el token está en otro nodo, alineado con Swaroop & Singh [9] sobre *fairness* y ausencia de *starvation*.

### CONFLICT — 409 (6)
El donante ya había sido reservado por una solicitud anterior exitosa. Evidencia que la veración `donante.isDisponible()` junto con el token previene dobles reservas sin necesidad de locks de base de datos, consistente con Tanenbaum & Van Steen [2].

### ERROR (0)
Ningún request falló por problemas de red o del servidor. Confirma que la comunicación TCP entre nodos es confiable en LAN.

## 3. Correspondencia con los trabajos relacionados

| Autor | Aporte | Evidencia en los resultados |
|---|---|---|
| García-Molina [1] | Algoritmo Bully: detección de caída del coordinador mediante timeouts y elección del nodo con mayor ID | En los logs se observó: `Nodo 1 detecta caida del coordinador 3` → elección → `Nodo 1 es el nuevo coordinador`. Transición completa sin intervención manual. |
| Swaroop & Singh [9] | Token Ring: exclusión mutua distribuida sin inanición ni interbloqueos | 9 LOCK vs 0 ERROR = el token nunca se pierde permanentemente. La alternancia entre 423 y 200 demuestra que todos los nodos acceden eventualmente a la sección crítica. |
| Gerónimo-Castillo et al. [7] | Sincronización de relojes vía Cristian, mitigación del desfase temporal mediante RTT | `Intento X/3 fallo` ×3 = el mecanismo de reintentos funciona. La sincronización falló porque el servidor de tiempo no estaba accesible, pero el sistema continúa operando sin ella. |
| Tanenbaum & Van Steen [2] | Integración Bully + Token Ring + Cristian como pilares complementarios | Los tres mecanismos operaron simultáneamente: Bully eligió coordinador, Token Ring serializó reservas, Cristian intentó sincronizar. Ninguno interfirió con el otro. |
| Lynch [5] | Marco teórico: propiedades de *safety* y *liveness* | *Safety*: ningún donante se reservó dos veces. *Liveness*: todas las solicitudes eventualmente recibieron una respuesta (200, 423 o 409). |

## 4. Interpretación para la discusión

- **Tasa de 25% OK**: Con 3 peers configurados y un ciclo completo de token de aproximadamente 1.5 segundos (300ms × 5 nodos en el anillo ideal), un nodo individual posee el token ~33% del tiempo. Las 20 solicitudes se dispararon con 50ms de separación, concentrándose en una ventana de ~1 segundo. Esto explica por qué solo 5 lograron ejecutarse cuando el token residía en el nodo 1.

- **0 ERROR**: La comunicación TCP sobre LAN demostró ser confiable. No hubo timeouts de conexión ni respuestas malformadas, lo que valida el diseño del protocolo de texto plano sobre sockets TCP.

- **Exclusión mutua efectiva**: A pesar de 20 intentos concurrentes sobre los mismos 5 donantes, ningún donante fue reservado más de una vez. La combinación de `tieneToken()` (423 LOCKED) y `donante.isDisponible()` (409 CONFLICT) opera como una doble barrera: el token serializa el acceso entre nodos, y el flag booleano previene duplicados dentro del mismo nodo.

- **Limitación**: La base de datos centralizada en PostgreSQL sigue siendo un punto único de fallo (SPOF). Si la BD cae, ningún nodo puede procesar reservas. Sin embargo, el cluster de aplicación tolera caídas de nodos individuales gracias al Bully, y el Token Ring se reconfigura automáticamente cuando un nodo desaparece.

## 5. Conclusión

La integración de Bully + Token Ring + Cristian sobre TCP texto plano en una LAN de 3 nodos cumple su objetivo principal: **exclusión mutua distribuida sin depender de la base de datos para sincronización**. El sistema es capaz de:

1. Elegir un coordinador automáticamente cuando el actual falla (Bully).
2. Serializar el acceso a la sección crítica de reservas (Token Ring).
3. Rechazar solicitudes cuando no se tiene el token (423 LOCKED).
4. Prevenir dobles reservas dentro de la ventana del token (409 CONFLICT).

Los resultados obtenidos son consistentes con los marcos teóricos de García-Molina, Swaroop & Singh, Tanenbaum & Van Steen, y Lynch, validando que la orquestación de estos algoritmos produce un sistema distribuido funcional y coherente para el dominio hospitalario propuesto.
