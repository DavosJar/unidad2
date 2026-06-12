package com.gruposiete.hospital.controller;

import com.gruposiete.hospital.model.Reserva;
import com.gruposiete.hospital.service.GestionReservas;
import com.gruposiete.hospital.infrastructure.NodeIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reservas")
public class ControladorReservas {

    private static final Logger logger = LoggerFactory.getLogger(ControladorReservas.class);
    private final GestionReservas gestion;
    private final NodeIdentity nodeIdentity;

    public ControladorReservas(GestionReservas gestion, NodeIdentity nodeIdentity) {
        this.gestion = gestion;
        this.nodeIdentity = nodeIdentity;
    }

    @PostMapping
    public ResponseEntity<Reserva> registrarReserva(@RequestParam Long idDonante,
                                                     @RequestParam String paciente) {
        logger.info("Nodo {}: POST /api/reservas - Registrando nueva reserva (Donante: {}, Paciente: {})", 
            nodeIdentity.getNodeId(), idDonante, paciente);
        return ResponseEntity.ok(gestion.registrarReserva(idDonante, paciente));
    }

    @GetMapping
    public List<Reserva> listarReservas() {
        List<Reserva> resultado = gestion.listarReservas();
        logger.info("Nodo {}: GET /api/reservas - {} reservas encontradas", 
            nodeIdentity.getNodeId(), resultado.size());
        return resultado;
    }

    @GetMapping("/donante/{idDonante}")
    public List<Reserva> listarPorDonante(@PathVariable Long idDonante) {
        List<Reserva> resultado = gestion.listarReservasPorDonante(idDonante);
        logger.info("Nodo {}: GET /api/reservas/donante/{} - {} reservas encontradas", 
            nodeIdentity.getNodeId(), idDonante, resultado.size());
        return resultado;
    }

    @GetMapping("/donante/{idDonante}/count")
    public long contarReservas(@PathVariable Long idDonante) {
        long cantidad = gestion.contarReservasPorDonante(idDonante);
        logger.info("Nodo {}: GET /api/reservas/donante/{}/count - {} reservas", 
            nodeIdentity.getNodeId(), idDonante, cantidad);
        return cantidad;
    }
}
