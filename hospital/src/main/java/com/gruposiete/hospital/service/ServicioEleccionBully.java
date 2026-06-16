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
    private ScheduledExecutorService scheduler;
    private volatile long ultimoHeartbeatRecibido;
    private volatile long inicioEleccion = 0;
    private volatile boolean esperandoOk = false;

    public ServicioEleccionBully(EstadoCluster estadoCluster, @Lazy GestionLogs gestionLogs) {
        this.estadoCluster = estadoCluster;
        this.gestionLogs = gestionLogs;
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
                estadoCluster.marcarCoordinador(res.getRespuesta().getOrigen());
                ultimoHeartbeatRecibido = System.currentTimeMillis();
                log.info("Nodo {} descubrio coordinador {} durante inicio", idPropio, res.getRespuesta().getOrigen());
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
        int idPropio = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();
        if (idPropio == coord) {
            ultimoHeartbeatRecibido = System.currentTimeMillis();
            return;
        }
        if (coord == -1 && estadoCluster.getEstado() != EstadoNodo.EN_ELECCION) {
            return; // No coordinator yet — discovery via cicloHeartbeat, not Bully
        }
        long diff = System.currentTimeMillis() - ultimoHeartbeatRecibido;
        if (diff > 15000 && estadoCluster.getEstado() != EstadoNodo.EN_ELECCION) {
            log.warn("Nodo {} detecta caida del coordinador {}", idPropio, coord);
            iniciarEleccion();
        }
        if (estadoCluster.getEstado() == EstadoNodo.EN_ELECCION) {
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
        if (estadoCluster.getIdPropio() != estadoCluster.getCoordinadorActual()) return;
        List<Integer> vivos = new ArrayList<>(estadoCluster.getPeers().keySet());
        Collections.sort(vivos);
        StringBuilder sb = new StringBuilder("orden_anillo=");
        for (int i = 0; i < vivos.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(vivos.get(i));
        }
        estadoCluster.configurarAnillo(vivos);
        try {
            MensajeCluster respuesta = new MensajeCluster(
                TipoMensaje.HEARTBEAT_OK, estadoCluster.getIdPropio(), msg.getOrigen(), sb.toString());
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
        if (payload != null && payload.startsWith("orden_anillo=")) {
            List<Integer> orden = parsearOrden(payload.substring("orden_anillo=".length()));
            if (!orden.isEmpty()) {
                estadoCluster.configurarAnillo(orden);
            }
        }
        int idPropio = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();
        if ((coord == -1 || coord == idPropio) && msg.getOrigen() != idPropio) {
            estadoCluster.marcarCoordinador(msg.getOrigen());
            log.info("Nodo {} reconoce coordinador {} via HEARTBEAT_OK", idPropio, msg.getOrigen());
        }
    }

    private void procesarElection(MensajeCluster msg) {
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

        // Si ya soy el coordinador, confirmo autoridad con COORDINATOR
        if (estadoCluster.getIdPropio() == estadoCluster.getCoordinadorActual()) {
            try {
                MensajeCluster coord = new MensajeCluster(
                    TipoMensaje.COORDINATOR, estadoCluster.getIdPropio(), msg.getOrigen(), "");
                String host = estadoCluster.getPeers().get(msg.getOrigen());
                if (host != null) {
                    MensajeCluster.enviarSinRespuesta(coord, host, clusterPort, 3000);
                }
            } catch (IOException e) {
                log.debug("Error enviando COORDINATOR a {}: {}", msg.getOrigen(), e.getMessage());
            }
            return;
        }

        if (msg.getOrigen() < estadoCluster.getIdPropio()
                && estadoCluster.getEstado() != EstadoNodo.EN_ELECCION) {
            log.info("Nodo {} inicia eleccion por ELECTION de {}", estadoCluster.getIdPropio(), msg.getOrigen());
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
        if (estadoCluster.getCoordinadorActual() != -1
                && estadoCluster.getEstado() != EstadoNodo.EN_ELECCION) {
            log.info("Nodo {} ignora COORDINATOR de {} (coord actual {})", idPropio, msg.getOrigen(), estadoCluster.getCoordinadorActual());
            return;
        }
        estadoCluster.marcarCoordinador(msg.getOrigen());
        ultimoHeartbeatRecibido = System.currentTimeMillis();
        String payload = msg.getPayload();
        if (payload != null && payload.startsWith("orden_anillo=")) {
            List<Integer> orden = parsearOrden(payload.substring("orden_anillo=".length()));
            if (!orden.isEmpty()) {
                estadoCluster.configurarAnillo(orden);
            }
        }
        log.info("Nodo {} reconoce coordinador {}", idPropio, msg.getOrigen());
        try {
            gestionLogs.registrar("Nodo " + idPropio + " reconoce coordinador nodo " + msg.getOrigen());
        } catch (Exception e) {
            log.warn("No se pudo persistir log: {}", e.getMessage());
        }
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
