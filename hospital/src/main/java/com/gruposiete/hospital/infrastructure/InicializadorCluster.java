package com.gruposiete.hospital.infrastructure;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    public InicializadorCluster(EstadoCluster estadoCluster) {
        this.estadoCluster = estadoCluster;
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
    }
}
