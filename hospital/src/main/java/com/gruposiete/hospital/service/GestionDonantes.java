package com.gruposiete.hospital.service;

import com.gruposiete.hospital.model.RegistroDonante;
import com.gruposiete.hospital.model.RepositorioDonantes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional
public class GestionDonantes {

    private final RepositorioDonantes repositorio;

    public GestionDonantes(RepositorioDonantes repositorio) {
        this.repositorio = repositorio;
    }

    public RegistroDonante registrarDonante(String nombre, String tipoSangre, String organo) {
        RegistroDonante d = new RegistroDonante(null, nombre, tipoSangre, organo, true);
        return repositorio.save(d);
    }

    public Optional<RegistroDonante> buscarDonante(Long id) {
        return repositorio.findById(id);
    }

    public List<RegistroDonante> listarDonantes() {
        return repositorio.findAll();
    }

    public List<RegistroDonante> listarDonantesDisponibles() {
        return repositorio.findByDisponibleTrue();
    }

    public List<RegistroDonante> listarDonantesReservados() {
        return repositorio.findByDisponibleFalse();
    }

    public List<RegistroDonante> listarDonantesPorTipoOrgano(String tipoOrgano) {
        return repositorio.findByOrgano(tipoOrgano);
    }

    public Optional<RegistroDonante> reservarDonante(Long id) {
        Optional<RegistroDonante> opt = repositorio.findById(id);
        if (opt.isEmpty() || !opt.get().isDisponible()) return Optional.empty();
        RegistroDonante d = opt.get();
        d.setDisponible(false);
        return Optional.of(repositorio.save(d));
    }

    public Optional<RegistroDonante> liberarDonante(Long id) {
        Optional<RegistroDonante> opt = repositorio.findById(id);
        if (opt.isEmpty() || opt.get().isDisponible()) return Optional.empty();
        RegistroDonante d = opt.get();
        d.setDisponible(true);
        return Optional.of(repositorio.save(d));
    }

    public List<RegistroDonante> buscarCompatibles(String organo, String tipoSangre) {
        return repositorio.buscarCompatibles(organo, tipoSangre);
    }

    public Map<String, Long> obtenerEstadisticas() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("disponibles", repositorio.countByDisponibleTrue());
        stats.put("reservados", repositorio.countByDisponibleFalse());
        stats.put("total", repositorio.count());
        return stats;
    }
}
