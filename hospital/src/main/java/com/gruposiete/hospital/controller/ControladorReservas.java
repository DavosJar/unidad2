package com.gruposiete.hospital.controller;

import com.gruposiete.hospital.model.Reserva;
import com.gruposiete.hospital.service.GestionReservas;
import com.gruposiete.hospital.service.GestionLogs;
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
    private final GestionLogs gestionLogs;

    public ControladorReservas(GestionReservas gestion, NodeIdentity nodeIdentity, GestionLogs gestionLogs) {
        this.gestion = gestion;
        this.nodeIdentity = nodeIdentity;
        this.gestionLogs = gestionLogs;
    }

    @PostMapping
    public ResponseEntity<Reserva> registrarReserva(@RequestParam Long idDonante,
                                                     @RequestParam String paciente) {
        logger.info("Nodo {}: Registró una nueva reserva", nodeIdentity.getNodeId());
        gestionLogs.registrar("Registró una nueva reserva");
        return ResponseEntity.ok(gestion.registrarReserva(idDonante, paciente));
    }

    @GetMapping
    public List<Reserva> listarReservas() {
        List<Reserva> resultado = gestion.listarReservas();
        logger.info("Nodo {}: Listó todas las reservas", nodeIdentity.getNodeId());
        gestionLogs.registrar("Listó todas las reservas");
        return resultado;
    }

    @GetMapping("/donante/{idDonante}")
    public List<Reserva> listarPorDonante(@PathVariable Long idDonante) {
        List<Reserva> resultado = gestion.listarReservasPorDonante(idDonante);
        logger.info("Nodo {}: Listó reservas por donante", nodeIdentity.getNodeId());
        gestionLogs.registrar("Listó reservas por donante");
        return resultado;
    }

    @GetMapping("/donante/{idDonante}/count")
    public long contarReservas(@PathVariable Long idDonante) {
        long cantidad = gestion.contarReservasPorDonante(idDonante);
        logger.info("Nodo {}: Contó reservas de un donante", nodeIdentity.getNodeId());
        gestionLogs.registrar("Contó reservas de un donante");
        return cantidad;
    }
}
