package com.gruposiete.hospital.service;

import com.gruposiete.hospital.model.RegistroDonante;
import com.gruposiete.hospital.model.RepositorioDonantes;
import com.gruposiete.hospital.infrastructure.NodeIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class GestionDonantes {

    private static final Logger logger = LoggerFactory.getLogger(GestionDonantes.class);
    private final RepositorioDonantes repositorio;
    private final NodeIdentity nodeIdentity;

    public GestionDonantes(RepositorioDonantes repositorio, NodeIdentity nodeIdentity) {
        this.repositorio = repositorio;
        this.nodeIdentity = nodeIdentity;
    }

    public RegistroDonante registrarDonante(String nombre, String tipoSangre, String organo) {
        RegistroDonante d = new RegistroDonante(null, nombre, tipoSangre, organo, true);
        RegistroDonante registrado = repositorio.save(d);
        logger.info("Nodo {}: Registró un nuevo donante", nodeIdentity.getNodeId());
        return registrado;
    }

    public Optional<RegistroDonante> buscarDonante(Long id) {
        Optional<RegistroDonante> resultado = repositorio.findById(id);
        if (resultado.isPresent()) {
            RegistroDonante d = resultado.get();
            logger.info("Nodo {}: Consultó donante por ID", nodeIdentity.getNodeId());
        } else {
            logger.info("Nodo {}: Consultó donante por ID", nodeIdentity.getNodeId());
        }
        return resultado;
    }

    public List<RegistroDonante> listarDonantes() {
        List<RegistroDonante> resultado = repositorio.findAll();
        logger.info("Nodo {}: Listó todos los donantes", nodeIdentity.getNodeId());
        return resultado;
    }

    public List<RegistroDonante> listarDonantesDisponibles() {
        List<RegistroDonante> disponibles = repositorio.findByDisponibleTrue();
        logger.info("Nodo {}: Listó donantes disponibles", nodeIdentity.getNodeId());
        return disponibles;
    }

    public List<RegistroDonante> listarDonantesReservados() {
        List<RegistroDonante> resultado = repositorio.findByDisponibleFalse();
        logger.info("Nodo {}: Listó donantes reservados", nodeIdentity.getNodeId());
        return resultado;
    }

    public List<RegistroDonante> listarDonantesPorTipoOrgano(String tipoOrgano) {
        List<RegistroDonante> resultado = repositorio.findByOrgano(tipoOrgano);
        logger.info("Nodo {}: Listó donantes por tipo de órgano", nodeIdentity.getNodeId());
        return resultado;
    }

    public Optional<RegistroDonante> reservarDonante(Long id) {
        Optional<RegistroDonante> opt = repositorio.findById(id);
        if (opt.isEmpty()) {
            logger.info("Nodo {}: Reservó un donante", nodeIdentity.getNodeId());
            return Optional.empty();
        }
        RegistroDonante d = opt.get();
        if (!d.isDisponible()) {
            logger.info("Nodo {}: Reservó un donante", nodeIdentity.getNodeId());
            return Optional.empty();
        }
        d.setDisponible(false);
        RegistroDonante reservado = repositorio.save(d);
        logger.info("Nodo {}: Reservó un donante", nodeIdentity.getNodeId());
        return Optional.of(reservado);
    }

    public Optional<RegistroDonante> liberarDonante(Long id) {
        Optional<RegistroDonante> opt = repositorio.findById(id);
        if (opt.isEmpty()) {
            logger.info("Nodo {}: Liberó un donante", nodeIdentity.getNodeId());
            return Optional.empty();
        }
        RegistroDonante d = opt.get();
        if (d.isDisponible()) {
            logger.info("Nodo {}: Liberó un donante", nodeIdentity.getNodeId());
            return Optional.empty();
        }
        d.setDisponible(true);
        RegistroDonante liberado = repositorio.save(d);
        logger.info("Nodo {}: Liberó un donante", nodeIdentity.getNodeId());
        return Optional.of(liberado);
    }

    public List<RegistroDonante> buscarCompatibles(String organo, String tipoSangre) {
        List<RegistroDonante> resultado = repositorio.buscarCompatibles(organo, tipoSangre);
        logger.info("Nodo {}: Buscó donantes compatibles", nodeIdentity.getNodeId());
        return resultado;
    }

    public Map<String, Long> obtenerEstadisticas() {
        Map<String, Long> stats = new HashMap<>();
        long disponibles = repositorio.countByDisponibleTrue();
        long reservados = repositorio.countByDisponibleFalse();
        long total = repositorio.count();
        stats.put("disponibles", disponibles);
        stats.put("reservados", reservados);
        stats.put("total", total);
        logger.info("Nodo {}: Consultó estadísticas", nodeIdentity.getNodeId());
        return stats;
    }
}
