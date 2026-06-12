package com.gruposiete.hospital.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Component that represents the unique identity of this node in the distributed system.
 * The node ID is injected from the application properties at startup.
 */
@Component
public class NodeIdentity {

    @Value("${node.id}")
    private String nodeId;

    /**
     * Gets the unique identifier for this node.
     *
     * @return the node ID
     */
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public String toString() {
        return "NodeIdentity{nodeId='" + nodeId + "'}";
    }
}
