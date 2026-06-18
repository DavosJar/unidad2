package com.gruposiete.hospital.service;

import com.gruposiete.hospital.infrastructure.EstadoCluster;
import com.gruposiete.hospital.infrastructure.EstadoCluster.EstadoNodo;
import com.gruposiete.hospital.model.MensajeCluster;
import com.gruposiete.hospital.model.MensajeCluster.TipoMensaje;
import com.gruposiete.hospital.service.GestionLogs;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ServicioEleccionBully {

    private static final Logger log = LoggerFactory.getLogger(ServicioEleccionBully.class);

    @Value("${cluster.port:9000}")
    private int clusterPort;

    private final EstadoCluster estadoCluster;
    private final GestionLogs gestionLogs;
    private final ServicioMonitorConexion monitorConexion;
    private ScheduledExecutorService scheduler;
    private volatile long ultimoHeartbeatRecibido;
    private volatile long inicioEleccion = 0;
    private volatile boolean esperandoOk = false;

    public ServicioEleccionBully(EstadoCluster estadoCluster, @Lazy GestionLogs gestionLogs,
                                  ServicioMonitorConexion monitorConexion) {
        this.estadoCluster = estadoCluster;
        this.gestionLogs = gestionLogs;
        this.monitorConexion = monitorConexion;
    }

    @PostConstruct
    public void iniciar() {
        this.ultimoHeartbeatRecibido = System.currentTimeMillis();
        if (estadoCluster.estaInicializado()) {
            descubrirCoordinadorInicial();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cicloHeartbeat, 5000, 5000, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::verificarHeartbeats, 5000, 5000, TimeUnit.MILLISECONDS);
        monitorConexion.registrarCallbacks(
            this::proclamarseCoordinador,
            (id) -> estadoCluster.marcarCoordinador(id)
        );
    }

    private void descubrirCoordinadorInicial() {
        int idPropio = estadoCluster.getIdPropio();
        boolean descubierto = false;
        for (int id : estadoCluster.getPeers().keySet()) {
            if (id == idPropio) continue;
            String host = estadoCluster.getPeers().get(id);
            if (host == null) continue;
            MensajeCluster probe = new MensajeCluster(TipoMensaje.HEARTBEAT, idPropio, id, "");
            MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(probe, host, clusterPort, 2000, 0);
            if (res.fueExitoso() && res.getRespuesta() != null) {
                MensajeCluster respuesta = res.getRespuesta();
                String payload = respuesta.getPayload();
                int coordDescubierto = -1;
                boolean coordEsResponder = false;
                if (payload != null && payload.startsWith("orden_anillo=")) {
                    // El que responde ES el coordinador
                    coordDescubierto = respuesta.getOrigen();
                    coordEsResponder = true;
                    List<Integer> orden = parsearOrden(payload.substring("orden_anillo=".length()));
                    if (!orden.isEmpty()) {
                        estadoCluster.configurarAnillo(orden);
                    }
                } else if (payload != null && payload.startsWith("coord=")) {
                    try {
                        coordDescubierto = Integer.parseInt(payload.substring("coord=".length()));
                    } catch (NumberFormatException e) {
                        coordDescubierto = -1;
                    }
                }
                if (coordDescubierto != -1 && coordDescubierto != idPropio) {
                    if (coordDescubierto > idPropio) {
                        estadoCluster.marcarCoordinador(coordDescubierto);
                        ultimoHeartbeatRecibido = System.currentTimeMillis();
                        log.info("Nodo {} descubrio coordinador {} durante inicio", idPropio, coordDescubierto);
                    } else if (coordDescubierto < idPropio) {
                        log.info("Nodo {} descubrio coordinador {} con ID menor durante inicio, me proclamo", idPropio, coordDescubierto);
                        scheduler.schedule(this::iniciarEleccion, 500, TimeUnit.MILLISECONDS);
                    }
                } else if (!coordEsResponder && respuesta.getOrigen() != idPropio) {
                    // No hay coordinador conocido (coord=-1), el responder es un peer normal
                    // Marcar temporalmente para establecer comunicación
                    estadoCluster.marcarCoordinador(respuesta.getOrigen());
                    ultimoHeartbeatRecibido = System.currentTimeMillis();
                    log.info("Nodo {} marca temporalmente coordinador {} durante inicio", idPropio, respuesta.getOrigen());
                }
                descubierto = true;
                break;
            }
        }
        if (!descubierto) {
            log.info("Nodo {} no descubrio coordinador, mantiene coord={}", idPropio, estadoCluster.getCoordinadorActual());
        }
    }

    @PreDestroy
    public void detener() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void cicloHeartbeat() {
        if (!estadoCluster.estaInicializado()) return;
        // Si estoy desconectado, no envio heartbeats
        if (estadoCluster.getEstado() == EstadoNodo.DESCONECTADO) return;
        int idPropio = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();
        if (idPropio == coord || coord == -1) {
            // Discovery mode: probe one peer per cycle to find the real coordinator
            for (int id : estadoCluster.getPeers().keySet()) {
                if (id == idPropio) continue;
                String host = estadoCluster.getPeers().get(id);
                if (host == null) continue;
                try {
                    MensajeCluster msg = new MensajeCluster(
                        TipoMensaje.HEARTBEAT, idPropio, id, "");
                    MensajeCluster.enviarSinRespuesta(msg, host, clusterPort, 3000);
                } catch (IOException e) {
                    log.debug("Discovery heartbeat a {} fallo: {}", id, e.getMessage());
                }
                break; // one probe per cycle
            }
            return;
        }
        String host = estadoCluster.getPeers().get(coord);
        if (host == null) return;
        try {
            MensajeCluster msg = new MensajeCluster(
                TipoMensaje.HEARTBEAT, idPropio, coord, "");
            MensajeCluster.enviarSinRespuesta(msg, host, clusterPort, 3000);
        } catch (IOException e) {
            log.debug("Heartbeat a {} fallo: {}", coord, e.getMessage());
        }
    }

    private void verificarHeartbeats() {
        if (!estadoCluster.estaInicializado()) return;
        EstadoNodo estado = estadoCluster.getEstado();

        // Si estoy DESCONECTADO, el monitor de conexion maneja la reconexion
        if (estado == EstadoNodo.DESCONECTADO) return;

        int idPropio = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();

        // Logica original de heartbeats
        if (idPropio == coord) {
            ultimoHeartbeatRecibido = System.currentTimeMillis();
            return;
        }
        if (coord == -1 && estado != EstadoNodo.EN_ELECCION) {
            long diff = System.currentTimeMillis() - ultimoHeartbeatRecibido;
            if (diff > 20000) {
                log.warn("Nodo {} sin coordinador por 20s, iniciando eleccion", idPropio);
                iniciarEleccion();
            }
            return;
        }
        long diff = System.currentTimeMillis() - ultimoHeartbeatRecibido;
        if (diff > 15000 && estado != EstadoNodo.EN_ELECCION) {
            log.warn("Nodo {} detecta caida del coordinador {}", idPropio, coord);
            iniciarEleccion();
        }
        if (estado == EstadoNodo.EN_ELECCION) {
            if (esperandoOk && System.currentTimeMillis() - inicioEleccion > 8000) {
                log.info("Timeout esperando OK, auto-proclamando nodo {}", idPropio);
                proclamarseCoordinador();
            } else if (!esperandoOk && System.currentTimeMillis() - inicioEleccion > 10000) {
                log.info("Timeout esperando COORDINATOR, auto-proclamando nodo {}", idPropio);
                proclamarseCoordinador();
            }
        }
    }

    private synchronized void iniciarEleccion() {
        int idPropio = estadoCluster.getIdPropio();
        int coordViejo = estadoCluster.getCoordinadorActual();
        if (coordViejo != -1 && coordViejo != idPropio) {
            estadoCluster.removerNodo(coordViejo);
            try {
                gestionLogs.registrar("Nodo " + idPropio + " detecto caida de coordinador " + coordViejo + ", inicia eleccion");
            } catch (Exception e) {
                log.warn("No se pudo persistir log: {}", e.getMessage());
            }
        }
        estadoCluster.marcarCoordinador(-1);
        estadoCluster.setEstado(EstadoNodo.EN_ELECCION);
        List<Integer> mayores = estadoCluster.nodosConIdMayor(idPropio);
        if (mayores.isEmpty()) {
            proclamarseCoordinador();
            return;
        }
        inicioEleccion = System.currentTimeMillis();
        esperandoOk = false;
        for (int idMayor : mayores) {
            String host = estadoCluster.getPeers().get(idMayor);
            if (host == null) continue;
            try {
                MensajeCluster msg = new MensajeCluster(
                    TipoMensaje.ELECTION, idPropio, idMayor, "");
                MensajeCluster.enviarSinRespuesta(msg, host, clusterPort, 3000);
                esperandoOk = true;
            } catch (IOException e) {
                log.debug("Nodo {} no responde a ELECTION: {}", idMayor, e.getMessage());
            }
        }
        if (!esperandoOk) {
            proclamarseCoordinador();
        }
    }

    private void proclamarseCoordinador() {
        int idPropio = estadoCluster.getIdPropio();
        estadoCluster.marcarCoordinador(idPropio);
        List<Integer> vivos = new ArrayList<>(estadoCluster.getPeers().keySet());
        Collections.sort(vivos);
        StringBuilder ordenPayload = new StringBuilder("orden_anillo=");
        for (int i = 0; i < vivos.size(); i++) {
            if (i > 0) ordenPayload.append(",");
            ordenPayload.append(vivos.get(i));
        }
        estadoCluster.configurarAnillo(vivos);
        for (int idDest : vivos) {
            if (idDest == idPropio) continue;
            String host = estadoCluster.getPeers().get(idDest);
            if (host == null) continue;
            try {
                MensajeCluster msg = new MensajeCluster(
                    TipoMensaje.COORDINATOR, idPropio, idDest, ordenPayload.toString());
                MensajeCluster.enviarSinRespuesta(msg, host, clusterPort, 3000);
            } catch (IOException e) {
                log.debug("Error enviando COORDINATOR a {}: {}", idDest, e.getMessage());
            }
        }
        Integer congelado = estadoCluster.getNodoCongeladoReportante();
        if (congelado != null) {
            String host = estadoCluster.getPeers().get(congelado);
            if (host != null) {
                int nuevoSiguiente = calcularSiguiente(vivos, congelado);
                try {
                    MensajeCluster msg = new MensajeCluster(
                        TipoMensaje.TOKEN_RESEND, idPropio, congelado,
                        "destino=" + nuevoSiguiente);
                    MensajeCluster.enviarSinRespuesta(msg, host, clusterPort, 3000);
                } catch (IOException e) {
                    log.debug("Error enviando TOKEN_RESEND a {}: {}", congelado, e.getMessage());
                }
            }
            estadoCluster.limpiarNodoCongeladoReportante();
        }
        log.info("Nodo {} es el nuevo coordinador", idPropio);
        try {
            gestionLogs.registrar("Nodo " + idPropio + " es el nuevo coordinador");
        } catch (Exception e) {
            log.warn("No se pudo persistir log: {}", e.getMessage());
        }
    }

    public synchronized void procesarMensaje(MensajeCluster msg) {
        // Si estaba DESCONECTADO y alguien me habla, la red volvio
        if (estadoCluster.getEstado() == EstadoNodo.DESCONECTADO) {
            log.info("Nodo {} recibe mensaje en DESCONECTADO de {}, la red volvio",
                     estadoCluster.getIdPropio(), msg.getOrigen());
            estadoCluster.setEstado(EstadoNodo.NORMAL);
            // No escaneamos, el mensaje mismo iniciara el flujo bully normal
        }
        switch (msg.getTipo()) {
            case HEARTBEAT:
                procesarHeartbeat(msg);
                break;
            case HEARTBEAT_OK:
                procesarHeartbeatOk(msg);
                break;
            case ELECTION:
                procesarElection(msg);
                break;
            case OK:
                procesarOk(msg);
                break;
            case COORDINATOR:
                procesarCoordinator(msg);
                break;
            case RING_UPDATE:
                procesarRingUpdate(msg);
                break;
            case TOKEN_LOST:
                procesarTokenLost(msg);
                break;
        }
    }

    private void procesarHeartbeat(MensajeCluster msg) {
        int idPropio = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();

        // Si soy coordinador y el que envia tiene mayor ID, debe ceder
        if (idPropio == coord && msg.getOrigen() > idPropio) {
            log.info("Nodo {} recibe HEARTBEAT de nodo superior {}, cediendo coordinacion",
                     idPropio, msg.getOrigen());
            // Responder con orden_anillo para que el otro sepa el estado actual
            // Pero no marcar como coordinador aqui - lo hara el cuando procese HEARTBEAT_OK
            // No obstante, si el otro tiene mayor ID, deberia automaticamente ganar
        }

        StringBuilder payload = new StringBuilder();
        if (idPropio == coord) {
            List<Integer> vivos = new ArrayList<>(estadoCluster.getPeers().keySet());
            Collections.sort(vivos);
            payload.append("orden_anillo=");
            for (int i = 0; i < vivos.size(); i++) {
                if (i > 0) payload.append(",");
                payload.append(vivos.get(i));
            }
            estadoCluster.configurarAnillo(vivos);
        } else if (coord != -1) {
            payload.append("coord=").append(coord);
            // Si el que envia tiene mayor ID que mi coordinador, reportarlo
            if (msg.getOrigen() > coord && msg.getOrigen() != idPropio) {
                payload.append(";superior=").append(msg.getOrigen());
            }
        }
        try {
            MensajeCluster respuesta = new MensajeCluster(
                TipoMensaje.HEARTBEAT_OK, idPropio, msg.getOrigen(), payload.toString());
            String host = estadoCluster.getPeers().get(msg.getOrigen());
            if (host != null) {
                MensajeCluster.enviarSinRespuesta(respuesta, host, clusterPort, 3000);
            }
        } catch (IOException e) {
            log.debug("Error enviando HEARTBEAT_OK a {}: {}", msg.getOrigen(), e.getMessage());
        }
    }

    private void procesarHeartbeatOk(MensajeCluster msg) {
        ultimoHeartbeatRecibido = System.currentTimeMillis();
        String payload = msg.getPayload();
        int idPropio = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();

        if (payload == null || payload.isEmpty()) return;

        if (payload.startsWith("orden_anillo=")) {
            List<Integer> orden = parsearOrden(payload.substring("orden_anillo=".length()));
            if (!orden.isEmpty()) {
                estadoCluster.configurarAnillo(orden);
            }
            procesarHeartbeatOkConConflicto(msg.getOrigen(), idPropio, coord);
        } else if (payload.startsWith("coord=")) {
            String resto = payload;
            int coordFromPayload = -1;
            int superiorFromPayload = -1;

            // Parsear coord=X
            int coordEnd = resto.indexOf(';');
            String coordPart = (coordEnd == -1) ? resto : resto.substring(0, coordEnd);
            try {
                coordFromPayload = Integer.parseInt(coordPart.substring("coord=".length()));
            } catch (NumberFormatException e) {
                log.warn("coord invalido en payload: {}", payload);
                return;
            }

            // Parsear ;superior=Y si existe
            if (coordEnd != -1) {
                String resto2 = resto.substring(coordEnd + 1);
                if (resto2.startsWith("superior=")) {
                    try {
                        superiorFromPayload = Integer.parseInt(resto2.substring("superior=".length()));
                    } catch (NumberFormatException e) {
                        // ignorar
                    }
                }
            }

            // Procesar coordinador
            if (coordFromPayload != -1 && coordFromPayload != idPropio) {
                procesarHeartbeatOkConConflicto(coordFromPayload, idPropio, coord);
            }

            // Si hay un nodo superior vivo y yo no soy ese superior
            if (superiorFromPayload != -1 && superiorFromPayload != idPropio) {
                if (idPropio > coord && (coord == -1 || idPropio > coord)) {
                    // Yo soy aun mayor, no necesito hacer nada especial
                } else if (superiorFromPayload > coord) {
                    log.info("Nodo {} detecta nodo superior {} via HEARTBEAT_OK payload",
                             idPropio, superiorFromPayload);
                    // Si yo mismo soy el superior, ya procesarHeartbeatOkConConflicto lo maneja
                }
            }
        }
    }

    /**
     * Logica central para resolver conflictos de liderazgo:
     * - Si no tengo coordinador (coord==-1) → acepto si el remoto tiene mayor ID, me proclamo si tiene menor
     * - Si tengo coordinador (coord!=-1) y el remoto es DIFERENTE:
     *   - Si remoto > coord → el cluster ya reconocio a alguien de mayor ID, acepto
     *   - Si remoto < coord → el remoto no es legitimo, no hago nada (mi coordinador actual es de mayor ID)
     * - Si soy yo mismo quien deberia ser coordinador (idPropio > coord y coord != idPropio):
     *   - Me proclamo coordinador (tengo mayor ID que mi propio coordinador)
     */
    private void procesarHeartbeatOkConConflicto(int coordRemoto, int idPropio, int coord) {
        if (coordRemoto == idPropio) return;

        if (coord == -1) {
            // No tengo coordinador
            if (coordRemoto > idPropio) {
                estadoCluster.marcarCoordinador(coordRemoto);
                log.info("Nodo {} descubre coordinador {} via HEARTBEAT_OK", idPropio, coordRemoto);
            } else {
                // El remoto tiene menor ID, yo deberia ser coordinador
                log.info("Nodo {} descubre coordinador {} con ID menor via HEARTBEAT_OK, me proclamo",
                         idPropio, coordRemoto);
                proclamarseCoordinador();
            }
            return;
        }

        // YA tengo coordinador
        if (coordRemoto != coord) {
            // Conflicto: dos coordinadores diferentes
            if (coordRemoto > coord) {
                // El remoto tiene mayor ID que mi coordinador actual → el cluster ya se actualizo
                log.info("Nodo {} actualiza coordinador {} -> {} via HEARTBEAT_OK",
                         idPropio, coord, coordRemoto);
                estadoCluster.marcarCoordinador(coordRemoto);
                return;
            }
            // coordRemoto < coord: mi coordinador actual tiene mayor ID, el remoto es ilegitimo
            log.debug("Nodo {} ignora coordinador remoto {} (mi coordinador {} tiene mayor ID)",
                      idPropio, coordRemoto, coord);
            return;
        }

        // coordRemoto == coord: mismo coordinador, verificar si YO deberia serlo
        if (idPropio > coord) {
            log.info("Nodo {} detecta que deberia ser coordinador (ID {} > {}), me proclamo",
                     idPropio, idPropio, coord);
            proclamarseCoordinador();
        }
    }

    private void procesarElection(MensajeCluster msg) {
        // Siempre responder OK para confirmar que estoy vivo
        try {
            MensajeCluster ok = new MensajeCluster(
                TipoMensaje.OK, estadoCluster.getIdPropio(), msg.getOrigen(), "");
            String host = estadoCluster.getPeers().get(msg.getOrigen());
            if (host != null) {
                MensajeCluster.enviarSinRespuesta(ok, host, clusterPort, 3000);
            }
        } catch (IOException e) {
            log.debug("Error enviando OK a {}: {}", msg.getOrigen(), e.getMessage());
        }

        int idPropio = estadoCluster.getIdPropio();

        // Si ya soy el coordinador
        if (idPropio == estadoCluster.getCoordinadorActual()) {
            // Si el que pregunta tiene mayor ID, dejar que él tome el control
            if (msg.getOrigen() > idPropio) {
                log.info("Nodo {} recibe ELECTION de nodo superior {}, cediendo autoridad", idPropio, msg.getOrigen());
                return;
            }
            // Si tiene menor ID, reafirmo mi autoridad con COORDINATOR
            try {
                MensajeCluster coord = new MensajeCluster(
                    TipoMensaje.COORDINATOR, idPropio, msg.getOrigen(), "");
                String host = estadoCluster.getPeers().get(msg.getOrigen());
                if (host != null) {
                    MensajeCluster.enviarSinRespuesta(coord, host, clusterPort, 3000);
                }
            } catch (IOException e) {
                log.debug("Error enviando COORDINATOR a {}: {}", msg.getOrigen(), e.getMessage());
            }
            return;
        }

        // Si no soy coordinador y el que pregunta tiene menor ID, iniciar eleccion
        if (msg.getOrigen() < idPropio
                && estadoCluster.getEstado() != EstadoNodo.EN_ELECCION) {
            log.info("Nodo {} inicia eleccion por ELECTION de {}", idPropio, msg.getOrigen());
            iniciarEleccion();
        }
    }

    private void procesarOk(MensajeCluster msg) {
        if (estadoCluster.getEstado() == EstadoNodo.EN_ELECCION) {
            log.info("Nodo {} recibio OK de {}, esperando COORDINATOR", estadoCluster.getIdPropio(), msg.getOrigen());
            esperandoOk = false;
            inicioEleccion = System.currentTimeMillis();
        }
    }

    private void procesarCoordinator(MensajeCluster msg) {
        int idPropio = estadoCluster.getIdPropio();
        // Si el que envió COORDINATOR tiene menor ID, rechazar y auto-proclamarse
        if (msg.getOrigen() < idPropio) {
            log.info("Nodo {} rechaza COORDINATOR de nodo inferior {} (yo tengo mayor ID), me proclamo coordinador",
                     idPropio, msg.getOrigen());
            proclamarseCoordinador();
            return;
        }
        // Si el que envió COORDINATOR tiene mayor ID, siempre aceptar (autoridad incuestionable)
        if (msg.getOrigen() > idPropio) {
            log.info("Nodo {} acepta COORDINATOR de nodo superior {}", idPropio, msg.getOrigen());
            estadoCluster.marcarCoordinador(msg.getOrigen());
            ultimoHeartbeatRecibido = System.currentTimeMillis();
            String payload = msg.getPayload();
            if (payload != null && payload.startsWith("orden_anillo=")) {
                List<Integer> orden = parsearOrden(payload.substring("orden_anillo=".length()));
                if (!orden.isEmpty()) {
                    estadoCluster.configurarAnillo(orden);
                }
            }
            try {
                gestionLogs.registrar("Nodo " + idPropio + " reconoce coordinador nodo " + msg.getOrigen());
            } catch (Exception e) {
                log.warn("No se pudo persistir log: {}", e.getMessage());
            }
            return;
        }
        // msg.getOrigen() == idPropio → ignorar mensaje propio
        log.debug("Nodo {} ignora COORDINATOR de si mismo", idPropio);
    }

    private void procesarRingUpdate(MensajeCluster msg) {
        String payload = msg.getPayload();
        if (payload != null && payload.startsWith("orden_anillo=")) {
            List<Integer> orden = parsearOrden(payload.substring("orden_anillo=".length()));
            if (!orden.isEmpty()) {
                estadoCluster.configurarAnillo(orden);
                log.info("Nodo {} actualiza anillo: {}", estadoCluster.getIdPropio(), orden);
            }
        }
    }

    private List<Integer> parsearOrden(String csv) {
        List<Integer> orden = new ArrayList<>();
        for (String s : csv.split(",")) {
            try {
                orden.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                log.warn("Formato invalido en orden anillo: {}", s);
            }
        }
        return orden;
    }

    private void procesarTokenLost(MensajeCluster msg) {
        int idPropio = estadoCluster.getIdPropio();
        if (idPropio != estadoCluster.getCoordinadorActual()) return;
        String payload = msg.getPayload();
        int nodoSospechoso = -1;
        if (payload != null && payload.startsWith("nodo_sospechoso=")) {
            try {
                nodoSospechoso = Integer.parseInt(payload.substring("nodo_sospechoso=".length()));
            } catch (NumberFormatException e) {
                return;
            }
        }
        if (nodoSospechoso == -1) {
            List<Integer> vivos = new ArrayList<>(estadoCluster.getPeers().keySet());
            Collections.sort(vivos);
            int siguienteDeReportante = calcularSiguiente(vivos, msg.getOrigen());
            String hostReporter = estadoCluster.getPeers().get(msg.getOrigen());
            if (hostReporter != null) {
                try {
                    MensajeCluster resend = new MensajeCluster(
                        TipoMensaje.TOKEN_RESEND, idPropio, msg.getOrigen(),
                        "destino=" + siguienteDeReportante);
                    MensajeCluster.enviarSinRespuesta(resend, hostReporter, clusterPort, 3000);
                } catch (IOException e) {
                    log.debug("Error en TOKEN_RESEND resync a {}: {}", msg.getOrigen(), e.getMessage());
                }
            }
            return;
        }
        String hostSospechoso = estadoCluster.getPeers().get(nodoSospechoso);
        if (hostSospechoso != null) {
            try {
                MensajeCluster heartbeatCheck = new MensajeCluster(
                    TipoMensaje.HEARTBEAT, idPropio, nodoSospechoso, "");
                MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(heartbeatCheck, hostSospechoso, clusterPort, 3000, 1);
                if (res.fueExitoso()) {
                    log.info("TOKEN_LOST falso positivo, nodo {} esta vivo", nodoSospechoso);
                    String hostReporter = estadoCluster.getPeers().get(msg.getOrigen());
                    if (hostReporter != null) {
                        MensajeCluster retry = new MensajeCluster(
                            TipoMensaje.TOKEN_RETRY, idPropio, msg.getOrigen(),
                            "destino=" + nodoSospechoso);
                        MensajeCluster.enviarSinRespuesta(retry, hostReporter, clusterPort, 3000);
                    }
                    return;
                }
            } catch (IOException e) {
                log.debug("Heartbeat a nodo sospechoso {} fallo: {}", nodoSospechoso, e.getMessage());
            }
        }
        log.warn("Nodo {} confirmado caido por TOKEN_LOST", nodoSospechoso);
        try {
            gestionLogs.registrar("Nodo " + nodoSospechoso + " confirmado caido por TOKEN_LOST");
        } catch (Exception e) {
            log.warn("No se pudo persistir log: {}", e.getMessage());
        }
        estadoCluster.removerNodo(nodoSospechoso);
        List<Integer> vivos = new ArrayList<>(estadoCluster.getPeers().keySet());
        Collections.sort(vivos);
        StringBuilder ordenPayload = new StringBuilder("orden_anillo=");
        for (int i = 0; i < vivos.size(); i++) {
            if (i > 0) ordenPayload.append(",");
            ordenPayload.append(vivos.get(i));
        }
        estadoCluster.configurarAnillo(vivos);
        for (int idDest : vivos) {
            if (idDest == idPropio) continue;
            String host = estadoCluster.getPeers().get(idDest);
            if (host == null) continue;
            try {
                MensajeCluster update = new MensajeCluster(
                    TipoMensaje.RING_UPDATE, idPropio, idDest, ordenPayload.toString());
                MensajeCluster.enviarSinRespuesta(update, host, clusterPort, 3000);
            } catch (IOException e) {
                log.debug("Error enviando RING_UPDATE a {}: {}", idDest, e.getMessage());
            }
        }
        String hostReporter = estadoCluster.getPeers().get(msg.getOrigen());
        if (hostReporter != null) {
            int nuevoSiguiente = calcularSiguiente(vivos, msg.getOrigen());
            try {
                MensajeCluster resend = new MensajeCluster(
                    TipoMensaje.TOKEN_RESEND, idPropio, msg.getOrigen(),
                    "destino=" + nuevoSiguiente);
                MensajeCluster.enviarSinRespuesta(resend, hostReporter, clusterPort, 3000);
            } catch (IOException e) {
                log.debug("Error enviando TOKEN_RESEND a {}: {}", msg.getOrigen(), e.getMessage());
            }
        }
    }

    private int calcularSiguiente(List<Integer> orden, int idNodo) {
        for (int i = 0; i < orden.size(); i++) {
            if (orden.get(i) == idNodo) {
                return orden.get((i + 1) % orden.size());
            }
        }
        return orden.isEmpty() ? -1 : orden.get(0);
    }

    public long getUltimoHeartbeatRecibido() { return ultimoHeartbeatRecibido; }
}
