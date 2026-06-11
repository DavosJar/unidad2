package com.gruposiete.hospital.service;

import com.gruposiete.hospital.model.RepositorioReservas;
import com.gruposiete.hospital.model.Reserva;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class GestionReservas {

    private final RepositorioReservas repositorioReservas;

    public GestionReservas(RepositorioReservas repositorioReservas) {
        this.repositorioReservas = repositorioReservas;
    }

    public Reserva registrarReserva(Long idDonante, String nombrePaciente) {
        Reserva reserva = new Reserva(idDonante, nombrePaciente, LocalDateTime.now());
        return repositorioReservas.save(reserva);
    }

    public List<Reserva> listarReservas() {
        return repositorioReservas.findAll();
    }

    public List<Reserva> listarReservasPorDonante(Long idDonante) {
        return repositorioReservas.findByidDonante(idDonante);
    }

    public long contarReservasPorDonante(Long idDonante) {
        return repositorioReservas.countByidDonante(idDonante);
    }
}
