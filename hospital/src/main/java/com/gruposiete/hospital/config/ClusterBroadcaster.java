package com.gruposiete.hospital.config;

import com.gruposiete.hospital.infrastructure.EstadoCluster;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Publica el estado del cluster por WebSocket (STOMP) cada 400 ms.
 * El frontend se suscribe a /topic/cluster y recibe un JSON con:
 *  - nodeIds      : lista de IDs de nodos conocidos (vivos)
 *  - allNodeIds   : todos los nodos configurados (incluyendo caídos)
 *  - coordinador  : ID del coordinador actual (-1 si ninguno)
 *  - tokenEn      : ID del nodo que tiene el token (-1 si ninguno)
 *  - tokenEnUso   : boolean
 *  - yo           : ID propio de este nodo
 *  - estado       : NORMAL | EN_ELECCION | COORDINADOR | DESCONECTADO
 */
@Component
@EnableScheduling
public class ClusterBroadcaster {

    private final SimpMessagingTemplate messaging;
    private final EstadoCluster estadoCluster;

    public ClusterBroadcaster(SimpMessagingTemplate messaging, EstadoCluster estadoCluster) {
        this.messaging = messaging;
        this.estadoCluster = estadoCluster;
    }

    @Scheduled(fixedDelay = 400)
    public void broadcast() {
        if (!estadoCluster.estaInicializado()) return;

        Map<String, Object> payload = new LinkedHashMap<>();

        // Nodos vivos actualmente
        List<Integer> vivos = new ArrayList<>(estadoCluster.getPeers().keySet());
        Collections.sort(vivos);
        payload.put("nodeIds", vivos);

        // Todos los nodos alguna vez conocidos
        List<Integer> todos = new ArrayList<>(estadoCluster.getTodosLosPeers().keySet());
        Collections.sort(todos);
        payload.put("allNodeIds", todos);

        payload.put("coordinador", estadoCluster.getCoordinadorActual());
        payload.put("tokenEn", estadoCluster.tieneToken() ? estadoCluster.getIdPropio() : -1);
        payload.put("tokenEnUso", estadoCluster.isTokenEnUso());
        payload.put("yo", estadoCluster.getIdPropio());
        payload.put("estado", estadoCluster.getEstado().name());
        payload.put("siguienteEnAnillo", estadoCluster.getSiguienteEnAnillo());
        payload.put("ts", System.currentTimeMillis());

        messaging.convertAndSend("/topic/cluster", (Object) payload);
    }
}
