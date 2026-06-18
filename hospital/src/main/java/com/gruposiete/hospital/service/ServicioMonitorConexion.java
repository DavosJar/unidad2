package com.gruposiete.hospital.service;

import com.gruposiete.hospital.infrastructure.EstadoCluster;
import com.gruposiete.hospital.infrastructure.EstadoCluster.EstadoNodo;
import com.gruposiete.hospital.model.MensajeCluster;
import com.gruposiete.hospital.model.MensajeCluster.TipoMensaje;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ServicioMonitorConexion {

    private static final Logger log = LoggerFactory.getLogger(ServicioMonitorConexion.class);

    @Value("${cluster.port:9000}")
    private int clusterPort;

    @Value("${network.router.ip:192.168.101.1}")
    private String routerIp;

    private final EstadoCluster estadoCluster;
    private final GestionLogs gestionLogs;
    private ScheduledExecutorService scheduler;

    // Callbacks: el bully registra esto para notificar cuando reconectamos
    private Runnable onReconnectProclamar;
    private java.util.function.IntConsumer onReconnectAceptarCoordinador;

    public ServicioMonitorConexion(EstadoCluster estadoCluster, GestionLogs gestionLogs) {
        this.estadoCluster = estadoCluster;
        this.gestionLogs = gestionLogs;
    }

    public void registrarCallbacks(Runnable onProclamar, java.util.function.IntConsumer onAceptarCoord) {
        this.onReconnectProclamar = onProclamar;
        this.onReconnectAceptarCoordinador = onAceptarCoord;
    }

    @PostConstruct
    public void iniciar() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::verificarEstado, 5000, 5000, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void detener() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public boolean pingRouter() {
        try {
            Process p = Runtime.getRuntime().exec("ping -c 1 -W 2 " + routerIp);
            int exitCode = p.waitFor();
            boolean reachable = (exitCode == 0);
            log.debug("Ping a router {}: {}", routerIp, reachable ? "OK" : "FALLO");
            return reachable;
        } catch (Exception e) {
            log.debug("Error ping a router {}: {}", routerIp, e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si debo entrar en DESCONECTADO o si ya estando DESCONECTADO debo reconectar.
     * Llamado desde el scheduler cada 5s.
     */
    private void verificarEstado() {
        if (!estadoCluster.estaInicializado()) return;

        if (estadoCluster.getEstado() == EstadoNodo.DESCONECTADO) {
            verificarReconexion();
            return;
        }

        // Chequeo de silencio: si soy coordinador o deberia serlo y no recibo nada de nadie
        int idPropio = estadoCluster.getIdPropio();
        int coord = estadoCluster.getCoordinadorActual();
        boolean soyCandidato = (idPropio == coord || coord == -1);
        long diffSilencio = System.currentTimeMillis() - estadoCluster.getUltimoMensajeRecibido();

        if (diffSilencio > 10000 && soyCandidato) {
            log.debug("Nodo {} sin mensajes por {}ms, verificando conectividad...", idPropio, diffSilencio);
            if (!pingRouter()) {
                log.warn("Nodo {} no puede alcanzar el router -> DESCONECTADO", idPropio);
                entrarDesconectado();
            } else {
                // Router responde pero nadie mas: cluster vacio.
                // Reiniciar contador para no repetir el chequeo cada 5s
                estadoCluster.notificarMensajeRecibido();
            }
        }
    }

    public void entrarDesconectado() {
        int idPropio = estadoCluster.getIdPropio();
        log.warn("Nodo {} entra en estado DESCONECTADO", idPropio);
        estadoCluster.marcarCoordinador(-1);
        estadoCluster.setEstado(EstadoNodo.DESCONECTADO);
        try {
            gestionLogs.registrar("Nodo " + idPropio + " se desconecto del cluster (router no responde)");
        } catch (Exception e) {
            log.warn("No se pudo persistir log: {}", e.getMessage());
        }
    }

    private void verificarReconexion() {
        if (!pingRouter()) return;

        int idPropio = estadoCluster.getIdPropio();
        log.info("Nodo {} detecta router disponible, saliendo de DESCONECTADO", idPropio);
        estadoCluster.setEstado(EstadoNodo.NORMAL);
        try {
            gestionLogs.registrar("Nodo " + idPropio + " recupero conexion al router, escaneando peers");
        } catch (Exception e) {
            log.warn("No se pudo persistir log: {}", e.getMessage());
        }
        escanearPeersDescendente();
    }

    /**
     * Escanea peers en orden DESCENDENTE (de ID mayor a menor).
     * El primer peer que responda determina el resultado.
     */
    private void escanearPeersDescendente() {
        int idPropio = estadoCluster.getIdPropio();
        List<Integer> ids = new ArrayList<>(estadoCluster.getTodosLosPeers().keySet());
        Collections.sort(ids, Collections.reverseOrder());

        for (int peerId : ids) {
            if (peerId == idPropio) continue;
            String host = estadoCluster.getTodosLosPeers().get(peerId);
            if (host == null) continue;

            try {
                MensajeCluster probe = new MensajeCluster(TipoMensaje.HEARTBEAT, idPropio, peerId, "");
                MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(probe, host, clusterPort, 2000, 0);
                if (res.fueExitoso()) {
                    if (peerId < idPropio) {
                        log.info("Nodo {} escaneo: peer {} responde con ID menor -> me proclamo coordinador",
                                 idPropio, peerId);
                        if (onReconnectProclamar != null) onReconnectProclamar.run();
                    } else {
                        log.info("Nodo {} escaneo: peer {} responde con ID mayor -> acepto su coordinacion",
                                 idPropio, peerId);
                        if (onReconnectAceptarCoordinador != null) onReconnectAceptarCoordinador.accept(peerId);
                    }
                    return;
                }
            } catch (Exception e) {
                log.debug("Nodo {} no responde en escaneo: {}", peerId, e.getMessage());
            }
        }
        log.info("Nodo {} escaneo: ningun peer responde, el cluster parece vacio", idPropio);
    }
}
