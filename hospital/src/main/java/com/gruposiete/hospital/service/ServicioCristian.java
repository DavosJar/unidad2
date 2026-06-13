package com.gruposiete.hospital.service;

import com.gruposiete.hospital.infrastructure.EstadoCluster;
import com.gruposiete.hospital.model.MensajeCluster;
import com.gruposiete.hospital.model.MensajeCluster.TipoMensaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class ServicioCristian implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ServicioCristian.class);

    @Value("${time.server.id:5}")
    private int timeServerId;

    @Value("${cluster.port:9000}")
    private int clusterPort;

    private final EstadoCluster estadoCluster;

    public ServicioCristian(EstadoCluster estadoCluster) {
        this.estadoCluster = estadoCluster;
    }

    @Override
    public void run(String... args) throws Exception {
        while (!estadoCluster.isListenerListo() || !estadoCluster.estaInicializado()) {
            Thread.sleep(500);
        }
        if (estadoCluster.getIdPropio() == timeServerId) {
            estadoCluster.ajustarOffsetReloj(0L);
            log.info("Nodo {} es el servidor de tiempo, offset=0", timeServerId);
            return;
        }
        String serverHost = estadoCluster.getPeers().get(timeServerId);
        if (serverHost == null) {
            log.warn("Servidor de tiempo (nodo {}) no encontrado en peers", timeServerId);
            return;
        }
        int reintentos = 0;
        int maxReintentos = 10;
        while (reintentos < maxReintentos) {
            try {
                long t1 = System.currentTimeMillis();
                MensajeCluster solicitud = new MensajeCluster(
                    TipoMensaje.TIME_REQUEST, estadoCluster.getIdPropio(), timeServerId, "");
                MensajeCluster.RespuestaEnvio res = MensajeCluster.enviar(solicitud, serverHost, clusterPort, 2000, 0);
                if (res.fueExitoso() && res.getRespuesta() != null) {
                    long t2 = System.currentTimeMillis();
                    long rtt = t2 - t1;
                    long tServidor = Long.parseLong(res.getRespuesta().getPayload());
                    long offset = tServidor + (rtt / 2) - t2;
                    estadoCluster.ajustarOffsetReloj(offset);
                    log.info("Cristian sincronizado: offset={}ms, RTT={}ms", offset, rtt);
                    return;
                }
                reintentos++;
                log.warn("Intento {} de {} fallo, reintentando...", reintentos, maxReintentos);
            } catch (Exception e) {
                reintentos++;
                log.warn("Intento {} de {} fallo: {}", reintentos, maxReintentos, e.getMessage());
            }
            if (reintentos < maxReintentos) {
                Thread.sleep(2000);
            }
        }
        log.warn("No se pudo sincronizar tiempo después de {} intentos", maxReintentos);
    }

    public Optional<MensajeCluster> procesarTimeRequest(MensajeCluster msg) {
        long timestamp = System.currentTimeMillis();
        MensajeCluster respuesta = new MensajeCluster(
            TipoMensaje.TIME_RESPONSE, estadoCluster.getIdPropio(), msg.getOrigen(), String.valueOf(timestamp));
        return Optional.of(respuesta);
    }

    public void procesarTimeResponse(MensajeCluster msg) {
        log.debug("TIME_RESPONSE recibido sin solicitud previa de {}", msg.getOrigen());
    }
}
