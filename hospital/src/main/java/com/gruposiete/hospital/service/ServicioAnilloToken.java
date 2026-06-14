package com.gruposiete.hospital.service;

import com.gruposiete.hospital.infrastructure.EstadoCluster;
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
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ServicioAnilloToken {

    private static final Logger log = LoggerFactory.getLogger(ServicioAnilloToken.class);

    @Value("${cluster.port:9000}")
    private int clusterPort;

    private final EstadoCluster estadoCluster;
    private final GestionLogs gestionLogs;
    private ScheduledExecutorService scheduler;
    private volatile long timestampTokenRecibido = 0;
    private volatile long timestampFrozen = 0;
    private volatile long ultimoLogToken = 0;

    public ServicioAnilloToken(EstadoCluster estadoCluster, @Lazy GestionLogs gestionLogs) {
        this.estadoCluster = estadoCluster;
        this.gestionLogs = gestionLogs;
    }

    @PostConstruct
    public void iniciar() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::intentarPasarToken, 6000, 2000, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::verificarTimeoutToken, 7000, 2000, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void detener() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void intentarPasarToken() {
        if (!estadoCluster.estaInicializado()) return;
        if (!estadoCluster.tieneToken()) return;
        long ahora = System.currentTimeMillis();
        if (ahora - timestampTokenRecibido < 2000) return;
        int destino = estadoCluster.getSiguienteEnAnillo();
        if (destino == -1 || destino == estadoCluster.getIdPropio()) return;
        String host = estadoCluster.getPeers().get(destino);
        if (host == null) return;
        try {
            MensajeCluster msg = new MensajeCluster(
                TipoMensaje.TOKEN, estadoCluster.getIdPropio(), destino, "");
            MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(msg, host, clusterPort, 4000, 1);
            if (res.fueExitoso()) {
                estadoCluster.quitarToken();
                log.debug("Token pasado a nodo {}", destino);
                if (System.currentTimeMillis() - ultimoLogToken > 10000) {
                    ultimoLogToken = System.currentTimeMillis();
                    log.info("Token circulando: nodo {} → nodo {}", estadoCluster.getIdPropio(), destino);
                    try {
                        gestionLogs.registrar("Token circulando: nodo " + estadoCluster.getIdPropio() + " → nodo " + destino);
                    } catch (Exception e) {
                        log.warn("No se pudo persistir log: {}", e.getMessage());
                    }
                }
            } else {
                manejarFalloToken(destino);
            }
        } catch (Exception e) {
            manejarFalloToken(destino);
        }
    }

    private void manejarFalloToken(int destino) {
        int coord = estadoCluster.getCoordinadorActual();
        if (coord == -1) {
            log.error("No hay coordinador conocido, no se puede reportar TOKEN_LOST");
            return;
        }
        String host = estadoCluster.getPeers().get(coord);
        if (host == null) return;
        estadoCluster.congelarToken();
        estadoCluster.setNodoCongeladoReportante(estadoCluster.getIdPropio());
        timestampFrozen = System.currentTimeMillis();
        try {
            MensajeCluster reporte = new MensajeCluster(
                TipoMensaje.TOKEN_LOST, estadoCluster.getIdPropio(), coord,
                "nodo_sospechoso=" + destino);
            MensajeCluster.enviarSinRespuesta(reporte, host, clusterPort, 1000);
            log.warn("TOKEN_LOST reportado a coordinador {}, nodo sospechoso {}", coord, destino);
        } catch (IOException e) {
            log.error("Error enviando TOKEN_LOST: {}", e.getMessage());
        }
    }

    private void verificarTimeoutToken() {
        if (!estadoCluster.estaInicializado()) return;
        if (timestampTokenRecibido == 0) return;
        if (estadoCluster.tieneToken()) return;
        long diff = System.currentTimeMillis() - timestampTokenRecibido;
        if (diff > 6000) {
            timestampTokenRecibido = 0;
        }
        if (timestampFrozen > 0 && System.currentTimeMillis() - timestampFrozen > 6000) {
            log.warn("Timeout frozen, reenviando TOKEN_LOST al coordinador actual");
            int coord = estadoCluster.getCoordinadorActual();
            if (coord != -1) {
                timestampFrozen = System.currentTimeMillis();
                String host = estadoCluster.getPeers().get(coord);
                if (host != null) {
                    try {
                        MensajeCluster reporte = new MensajeCluster(
                            TipoMensaje.TOKEN_LOST, estadoCluster.getIdPropio(), coord, "nodo_sospechoso=-1");
                        MensajeCluster.enviarSinRespuesta(reporte, host, clusterPort, 1000);
                    } catch (IOException e) {
                        log.error("Error reenviando TOKEN_LOST: {}", e.getMessage());
                    }
                }
            }
        }
    }

    public synchronized Optional<MensajeCluster> procesarToken(MensajeCluster msg) {
        estadoCluster.darToken();
        estadoCluster.limpiarNodoCongeladoReportante();
        timestampTokenRecibido = System.currentTimeMillis();
        timestampFrozen = 0;
        log.debug("Token recibido de nodo {}", msg.getOrigen());
        MensajeCluster ack = new MensajeCluster(
            TipoMensaje.TOKEN, estadoCluster.getIdPropio(), msg.getOrigen(), "OK");
        return Optional.of(ack);
    }

    public synchronized void procesarMensaje(MensajeCluster msg) {
        switch (msg.getTipo()) {
            case TOKEN_RETRY:
                procesarTokenRetry(msg);
                break;
            case TOKEN_RESEND:
                procesarTokenResend(msg);
                break;
        }
    }

    private void procesarTokenRetry(MensajeCluster msg) {
        log.info("Reintentando envio de token por orden del coordinador");
        int destino = extraerDestino(msg.getPayload());
        if (destino == -1) return;
        String host = estadoCluster.getPeers().get(destino);
        if (host == null) return;
        try {
            MensajeCluster token = new MensajeCluster(
                TipoMensaje.TOKEN, estadoCluster.getIdPropio(), destino, "");
            MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(token, host, clusterPort, 4000, 1);
            if (res.fueExitoso()) {
                estadoCluster.quitarToken();
                estadoCluster.limpiarNodoCongeladoReportante();
                timestampFrozen = 0;
                log.debug("Token reenviado a nodo {}", destino);
            }
        } catch (Exception e) {
            log.error("Error en reintento de token a {}: {}", destino, e.getMessage());
        }
    }

    private void procesarTokenResend(MensajeCluster msg) {
        log.info("Reenviando token a nuevo destino por orden del coordinador");
        int nuevoDestino = extraerDestino(msg.getPayload());
        if (nuevoDestino == -1) return;
        String host = estadoCluster.getPeers().get(nuevoDestino);
        if (host == null) return;
        try {
            MensajeCluster token = new MensajeCluster(
                TipoMensaje.TOKEN, estadoCluster.getIdPropio(), nuevoDestino, "");
            MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(token, host, clusterPort, 4000, 1);
            if (res.fueExitoso()) {
                estadoCluster.quitarToken();
                estadoCluster.limpiarNodoCongeladoReportante();
                timestampFrozen = 0;
                log.debug("Token reenviado a nuevo nodo {}", nuevoDestino);
            }
        } catch (Exception e) {
            log.error("Error en reenvio de token a {}: {}", nuevoDestino, e.getMessage());
        }
    }

    private int extraerDestino(String payload) {
        if (payload == null || !payload.startsWith("destino=")) return -1;
        try {
            return Integer.parseInt(payload.substring("destino=".length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
