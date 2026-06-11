package com.gruposiete.hospital.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_donante", nullable = false)
    private Long idDonante;

    @Column(nullable = false)
    private String nombrePaciente;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public Reserva() {}

    public Reserva(Long idDonante, String nombrePaciente, LocalDateTime timestamp) {
        this.idDonante = idDonante;
        this.nombrePaciente = nombrePaciente;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombrePaciente() { return nombrePaciente; }
    public void setNombrePaciente(String nombrePaciente) { this.nombrePaciente = nombrePaciente; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
