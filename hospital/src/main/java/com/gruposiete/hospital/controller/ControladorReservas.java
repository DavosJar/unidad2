package com.gruposiete.hospital.controller;

import com.gruposiete.hospital.model.Reserva;
import com.gruposiete.hospital.service.GestionReservas;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reservas")
public class ControladorReservas {

    private final GestionReservas gestion;

    public ControladorReservas(GestionReservas gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    public ResponseEntity<Reserva> registrarReserva(@RequestParam Long idDonante,
                                                     @RequestParam String paciente) {
        return ResponseEntity.ok(gestion.registrarReserva(idDonante, paciente));
    }

    @GetMapping
    public List<Reserva> listarReservas() {
        return gestion.listarReservas();
    }

    @GetMapping("/donante/{idDonante}")
    public List<Reserva> listarPorDonante(@PathVariable Long idDonante) {
        return gestion.listarReservasPorDonante(idDonante);
    }

    @GetMapping("/donante/{idDonante}/count")
    public long contarReservas(@PathVariable Long idDonante) {
        return gestion.contarReservasPorDonante(idDonante);
    }
}
