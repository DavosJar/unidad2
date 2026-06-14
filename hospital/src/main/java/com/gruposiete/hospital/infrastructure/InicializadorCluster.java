package com.gruposiete.hospital.infrastructure;

import com.gruposiete.hospital.service.GestionLogs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class InicializadorCluster {

    private static final Logger log = LoggerFactory.getLogger(InicializadorCluster.class);

    @Value("${node.id}")
    private String nodeId;

    @Value("${cluster.peers}")
    private String clusterPeers;

    @Value("${cluster.port:9000}")
    private int clusterPort;

    private final EstadoCluster estadoCluster;
    private final GestionLogs gestionLogs;

    public InicializadorCluster(EstadoCluster estadoCluster, @Lazy GestionLogs gestionLogs) {
        this.estadoCluster = estadoCluster;
        this.gestionLogs = gestionLogs;
    }

    @PostConstruct
    public void init() {
        int idPropio = Integer.parseInt(nodeId);
        String[] ips = clusterPeers.split(",");
        Map<Integer, String> peers = new HashMap<>();
        for (int i = 0; i < ips.length; i++) {
            peers.put(i + 1, ips[i].trim());
        }
        estadoCluster.inicializar(idPropio, peers);
        log.info("Nodo {} inicializado. Peers: {}", idPropio, peers);
        try {
            gestionLogs.registrar("Nodo " + idPropio + " inicializado con " + peers.size() + " peers");
        } catch (Exception e) {
            log.warn("No se pudo persistir log: {}", e.getMessage());
        }
    }
}
