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
    private volatile long ultimoAvisoDeToken = System.currentTimeMillis();

    public ServicioAnilloToken(EstadoCluster estadoCluster, @Lazy GestionLogs gestionLogs) {
        this.estadoCluster = estadoCluster;
        this.gestionLogs = gestionLogs;
    }

    @PostConstruct
    public void iniciar() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::intentarPasarToken, 2000, 300, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::verificarTimeoutToken, 2500, 500, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void detener() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void intentarPasarToken() {
        if (!estadoCluster.estaInicializado()) return;
        if (estadoCluster.getEstado() == EstadoNodo.DESCONECTADO) return;
        if (!estadoCluster.tieneToken()) return;
        if (estadoCluster.isTokenEnUso()) return;
        long ahora = System.currentTimeMillis();
        if (ahora - timestampTokenRecibido < 300) return;
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
        int miId = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();

        if (estadoCluster.getEstado() == EstadoNodo.DESCONECTADO) {
            log.debug("Nodo {} en DESCONECTADO, ignora fallo de token a {}", miId, destino);
            return;
        }

        if (miId == coord) {
            estadoCluster.removerNodo(destino);
            List<Integer> vivos = new ArrayList<>(estadoCluster.getPeers().keySet());
            Collections.sort(vivos);
            StringBuilder ordenPayload = new StringBuilder("orden_anillo=");
            for (int i = 0; i < vivos.size(); i++) {
                if (i > 0) ordenPayload.append(",");
                ordenPayload.append(vivos.get(i));
            }
            estadoCluster.configurarAnillo(vivos);
            for (int idDest : vivos) {
                if (idDest == miId) continue;
                String host = estadoCluster.getPeers().get(idDest);
                if (host == null) continue;
                try {
                    MensajeCluster update = new MensajeCluster(
                        TipoMensaje.RING_UPDATE, miId, idDest, ordenPayload.toString());
                    MensajeCluster.enviarSinRespuesta(update, host, clusterPort, 3000);
                } catch (IOException e) {
                    log.debug("Error enviando RING_UPDATE a {}: {}", idDest, e.getMessage());
                }
            }
            int nuevoDestino = estadoCluster.getSiguienteEnAnillo();
            if (nuevoDestino != -1 && nuevoDestino != miId) {
                String host = estadoCluster.getPeers().get(nuevoDestino);
                if (host != null) {
                    try {
                        MensajeCluster token = new MensajeCluster(
                            TipoMensaje.TOKEN, miId, nuevoDestino, "");
                        MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(token, host, clusterPort, 4000, 1);
                        if (res.fueExitoso()) {
                            estadoCluster.quitarToken();
                            log.warn("Token pasado a nuevo sucesor {} tras caida de {}", nuevoDestino, destino);
                            try { gestionLogs.registrar("Coordinador " + miId + " removio nodo " + destino + " del anillo, token a " + nuevoDestino); }
                            catch (Exception e) { log.warn("No se pudo persistir log: {}", e.getMessage()); }
                            return;
                        } else {
                            log.warn("Fallo pasando token a nuevo sucesor {}. Reintentando...", nuevoDestino);
                            manejarFalloToken(nuevoDestino);
                            return;
                        }
                    } catch (Exception e) {
                        log.error("Error pasando token a nuevo sucesor {}: {}", nuevoDestino, e.getMessage());
                        manejarFalloToken(nuevoDestino);
                        return;
                    }
                }
            }
            log.warn("Coordinador no pudo pasar token a nadie (solo quedo yo o no hay mas nodos), reteniendo");
            return;
        }

        int hostCoord = coord;
        if (hostCoord == -1) {
            log.warn("No hay coordinador, reteniendo token");
            estadoCluster.darToken();
            return;
        }
        String host = estadoCluster.getPeers().get(hostCoord);
        if (host == null || !estadoCluster.estaVivo(hostCoord)) {
            log.warn("Coordinador {} no alcanzable, reteniendo token", hostCoord);
            estadoCluster.darToken();
            timestampFrozen = 0;
            return;
        }
        estadoCluster.congelarToken();
        estadoCluster.setNodoCongeladoReportante(miId);
        timestampFrozen = System.currentTimeMillis();
        try {
            MensajeCluster reporte = new MensajeCluster(
                TipoMensaje.TOKEN_LOST, miId, hostCoord,
                "nodo_sospechoso=" + destino);
            MensajeCluster.enviarSinRespuesta(reporte, host, clusterPort, 1000);
            log.warn("TOKEN_LOST reportado a coordinador {}, nodo sospechoso {}", hostCoord, destino);
        } catch (IOException e) {
            log.error("Error enviando TOKEN_LOST a coordinador {}: {}, reteniendo token", hostCoord, e.getMessage());
            estadoCluster.darToken();
            estadoCluster.limpiarNodoCongeladoReportante();
            timestampFrozen = 0;
        }
    }

    private void verificarTimeoutToken() {
        if (!estadoCluster.estaInicializado()) return;
        if (estadoCluster.tieneToken()) return;

        // Regeneracion de token por el coordinador si se pierde por completo
        if (estadoCluster.getCoordinadorActual() == estadoCluster.getIdPropio()) {
            long silencioGlobal = System.currentTimeMillis() - ultimoAvisoDeToken;
            if (silencioGlobal > 15000 && !estadoCluster.tieneToken()) {
                log.warn("Coordinador detecta que el token se perdio ({}ms de silencio). Regenerando token...", silencioGlobal);
                estadoCluster.darToken();
                ultimoAvisoDeToken = System.currentTimeMillis();
                try {
                    gestionLogs.registrar("Coordinador " + estadoCluster.getIdPropio() + " regenera token tras 15s de perdida");
                } catch (Exception e) {
                    log.warn("No se pudo persistir log: {}", e.getMessage());
                }
                return; // Token regenerated, no need to do anything else this cycle
            }
        }

        if (timestampTokenRecibido == 0) return;
        
        long diff = System.currentTimeMillis() - timestampTokenRecibido;
        if (diff > 3000) {
            timestampTokenRecibido = 0;
        }


        if (timestampFrozen > 0 && System.currentTimeMillis() - timestampFrozen > 3000) {
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
        ultimoAvisoDeToken = System.currentTimeMillis();
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
            } else {
                manejarFalloToken(destino);
            }
        } catch (Exception e) {
            log.error("Error en reintento de token a {}: {}", destino, e.getMessage());
            manejarFalloToken(destino);
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
            } else {
                manejarFalloToken(nuevoDestino);
            }
        } catch (Exception e) {
            log.error("Error en reenvio de token a {}: {}", nuevoDestino, e.getMessage());
            manejarFalloToken(nuevoDestino);
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
