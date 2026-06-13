package com.gruposiete.hospital.service;

import com.gruposiete.hospital.infrastructure.EstadoCluster;
import com.gruposiete.hospital.model.RepositorioReservas;
import com.gruposiete.hospital.model.Reserva;
import com.gruposiete.hospital.infrastructure.NodeIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class GestionReservas {

    private static final Logger logger = LoggerFactory.getLogger(GestionReservas.class);
    private final RepositorioReservas repositorioReservas;
    private final NodeIdentity nodeIdentity;
    private final EstadoCluster estadoCluster;

    public GestionReservas(RepositorioReservas repositorioReservas, NodeIdentity nodeIdentity, EstadoCluster estadoCluster) {
        this.repositorioReservas = repositorioReservas;
        this.nodeIdentity = nodeIdentity;
        this.estadoCluster = estadoCluster;
    }

    public Reserva registrarReserva(Long idDonante, String nombrePaciente) {
        if (!estadoCluster.tieneToken()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                "Este nodo no posee el token de exclusion mutua");
        }
        Reserva reserva = new Reserva(idDonante, nombrePaciente, LocalDateTime.now());
        Reserva reservaGuardada = repositorioReservas.save(reserva);
        logger.info("Nodo {}: Registró una nueva reserva", nodeIdentity.getNodeId());
        return reservaGuardada;
    }

    public List<Reserva> listarReservas() {
        List<Reserva> reservas = repositorioReservas.findAll();
        logger.info("Nodo {}: Listó todas las reservas", nodeIdentity.getNodeId());
        return reservas;
    }

    public List<Reserva> listarReservasPorDonante(Long idDonante) {
        List<Reserva> resultado = repositorioReservas.findByidDonante(idDonante);
        logger.info("Nodo {}: Listó reservas por donante", nodeIdentity.getNodeId());
        return resultado;
    }

    public long contarReservasPorDonante(Long idDonante) {
        long cantidad = repositorioReservas.countByidDonante(idDonante);
        logger.info("Nodo {}: Contó reservas de un donante", nodeIdentity.getNodeId());
        return cantidad;
    }
}
