package com.gruposiete.hospital.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@Component
public class NodeIdentity {

    private static final Logger logger = LoggerFactory.getLogger(NodeIdentity.class);

    @Value("${node.id:auto}")
    private String nodeId;

    private int numericId;

    @PostConstruct
    public void init() {
        if ("auto".equals(nodeId)) {
            numericId = generateNumericId();
            nodeId = String.valueOf(numericId);
            logger.info("NODE_ID auto-generado: {} (a partir de IP del contenedor)", nodeId);
        } else {
            try {
                numericId = Integer.parseInt(nodeId);
            } catch (NumberFormatException e) {
                numericId = nodeId.hashCode() & Integer.MAX_VALUE;
                logger.warn("NODE_ID '{}' no es numérico, usando hash: {}", nodeId, numericId);
                nodeId = String.valueOf(numericId);
            }
        }
    }

    private int generateNumericId() {
        try {
            // Buscar IP de la red overlay (eth0 o la que tenga 10.x.x.x)
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String ip = addr.getHostAddress();
                    if (ip.startsWith("192.168.")) {
                        // Tomar el último octeto como ID
                        String[] parts = ip.split("\\.");
                        int id = Integer.parseInt(parts[2]) * 256 + Integer.parseInt(parts[3]);
                        if (id > 0) {
                            return id;
                        }
                    }
                }
            }
            // Fallback: último recurso
            String hostname = InetAddress.getLocalHost().getHostName();
            return (hostname.hashCode() & Integer.MAX_VALUE) % 1000 + 1;
        } catch (Exception e) {
            logger.warn("No se pudo determinar IP, usando ID aleatorio", e);
            return (int) (Math.random() * 1000) + 1;
        }
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getNumericId() {
        return numericId;
    }

    @Override
    public String toString() {
        return "NodeIdentity{nodeId='" + nodeId + "', numericId=" + numericId + "}";
    }
}
