package com.gruposiete.hospital.controller;

import com.gruposiete.hospital.model.LogEntry;
import com.gruposiete.hospital.service.GestionLogs;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class ControladorLogs {

    private final GestionLogs gestion;

    public ControladorLogs(GestionLogs gestion) {
        this.gestion = gestion;
    }

    @GetMapping
    public List<LogEntry> listarLogs() {
        return gestion.obtenerTodos();
    }
}
