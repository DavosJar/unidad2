package com.gruposiete.hospital.service;

import com.gruposiete.hospital.model.LogEntry;
import com.gruposiete.hospital.model.RepositorioLogs;
import com.gruposiete.hospital.infrastructure.NodeIdentity;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class GestionLogs {

    private final RepositorioLogs repositorio;
    private final NodeIdentity nodeIdentity;

    public GestionLogs(RepositorioLogs repositorio, NodeIdentity nodeIdentity) {
        this.repositorio = repositorio;
        this.nodeIdentity = nodeIdentity;
    }

    public void registrar(String accion) {
        LogEntry entry = new LogEntry(nodeIdentity.getNodeId(), accion);
        repositorio.save(entry);
    }

    public List<LogEntry> obtenerTodos() {
        return repositorio.findAllByOrderByTimestampDesc();
    }
}
