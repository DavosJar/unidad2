package com.gruposiete.hospital.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "logs_sistema")
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nodo_id", nullable = false)
    private String nodoId;

    @Column(name = "accion", nullable = false)
    private String accion;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    public LogEntry() {}

    public LogEntry(String nodoId, String accion) {
        this.nodoId = nodoId;
        this.accion = accion;
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }
    public String getNodoId() { return nodoId; }
    public String getAccion() { return accion; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
