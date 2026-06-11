package com.gruposiete.hospital.controller;

import com.gruposiete.hospital.model.RegistroDonante;
import com.gruposiete.hospital.service.GestionDonantes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/donantes")
public class ControladorDonantes {

    private final GestionDonantes gestion;

    public ControladorDonantes(GestionDonantes gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    public ResponseEntity<RegistroDonante> registrar(@RequestParam String nombre,
                                                       @RequestParam String tipoSangre,
                                                       @RequestParam String organo) {
        return ResponseEntity.ok(gestion.registrarDonante(nombre, tipoSangre, organo));
    }

    @GetMapping
    public List<RegistroDonante> listarTodos() {
        return gestion.listarDonantes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegistroDonante> buscar(@PathVariable Long id) {
        return gestion.buscarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/disponibles")
    public List<RegistroDonante> listarDisponibles() {
        return gestion.listarDonantesDisponibles();
    }

    @GetMapping("/reservados")
    public List<RegistroDonante> listarReservados() {
        return gestion.listarDonantesReservados();
    }

    @GetMapping("/tipo/{tipo}")
    public List<RegistroDonante> listarPorTipo(@PathVariable String tipo) {
        return gestion.listarDonantesPorTipoOrgano(tipo);
    }

    @PutMapping("/{id}/reservar")
    public ResponseEntity<RegistroDonante> reservar(@PathVariable Long id) {
        return gestion.reservarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/liberar")
    public ResponseEntity<RegistroDonante> liberar(@PathVariable Long id) {
        return gestion.liberarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/compatibles")
    public List<RegistroDonante> buscarCompatibles(@RequestParam String organo,
                                                     @RequestParam String sangre) {
        return gestion.buscarCompatibles(organo, sangre);
    }

    @GetMapping("/estadisticas")
    public Map<String, Long> estadisticas() {
        return gestion.obtenerEstadisticas();
    }
}
