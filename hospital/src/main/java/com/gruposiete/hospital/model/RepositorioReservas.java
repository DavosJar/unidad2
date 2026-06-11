package com.gruposiete.hospital.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RepositorioReservas extends JpaRepository<Reserva, Long> {
    List<Reserva> findByidDonante(Long idDonante);
    long countByidDonante(Long idDonante);
}
