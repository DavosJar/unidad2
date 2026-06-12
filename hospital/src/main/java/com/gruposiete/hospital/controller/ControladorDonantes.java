package com.gruposiete.hospital.controller;

import com.gruposiete.hospital.model.RegistroDonante;
import com.gruposiete.hospital.service.GestionDonantes;
import com.gruposiete.hospital.infrastructure.NodeIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/donantes")
public class ControladorDonantes {

    private static final Logger logger = LoggerFactory.getLogger(ControladorDonantes.class);
    private final GestionDonantes gestion;
    private final NodeIdentity nodeIdentity;

    public ControladorDonantes(GestionDonantes gestion, NodeIdentity nodeIdentity) {
        this.gestion = gestion;
        this.nodeIdentity = nodeIdentity;
    }

    @PostMapping
    public ResponseEntity<RegistroDonante> registrar(@RequestParam String nombre,
                                                       @RequestParam String tipoSangre,
                                                       @RequestParam String organo) {
        logger.info("Nodo {}: POST /api/donantes - Registrando nuevo donante (Órgano: {})", 
            nodeIdentity.getNodeId(), organo);
        return ResponseEntity.ok(gestion.registrarDonante(nombre, tipoSangre, organo));
    }

    @GetMapping
    public List<RegistroDonante> listarTodos() {
        List<RegistroDonante> resultado = gestion.listarDonantes();
        logger.info("Nodo {}: GET /api/donantes - {} donantes listados", 
            nodeIdentity.getNodeId(), resultado.size());
        return resultado;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegistroDonante> buscar(@PathVariable Long id) {
        logger.info("Nodo {}: GET /api/donantes/{} - Buscando donante", nodeIdentity.getNodeId(), id);
        return gestion.buscarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/disponibles")
    public List<RegistroDonante> listarDisponibles() {
        List<RegistroDonante> resultado = gestion.listarDonantesDisponibles();
        logger.info("Nodo {}: GET /api/donantes/disponibles - {} órganos disponibles", 
            nodeIdentity.getNodeId(), resultado.size());
        return resultado;
    }

    @GetMapping("/reservados")
    public List<RegistroDonante> listarReservados() {
        List<RegistroDonante> resultado = gestion.listarDonantesReservados();
        logger.info("Nodo {}: GET /api/donantes/reservados - {} donantes reservados", 
            nodeIdentity.getNodeId(), resultado.size());
        return resultado;
    }

    @GetMapping("/tipo/{tipo}")
    public List<RegistroDonante> listarPorTipo(@PathVariable String tipo) {
        List<RegistroDonante> resultado = gestion.listarDonantesPorTipoOrgano(tipo);
        logger.info("Nodo {}: GET /api/donantes/tipo/{} - {} donantes encontrados", 
            nodeIdentity.getNodeId(), tipo, resultado.size());
        return resultado;
    }

    @PutMapping("/{id}/reservar")
    public ResponseEntity<RegistroDonante> reservar(@PathVariable Long id) {
        logger.info("Nodo {}: PUT /api/donantes/{}/reservar - Intentando reservar donante", nodeIdentity.getNodeId(), id);
        return gestion.reservarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/liberar")
    public ResponseEntity<RegistroDonante> liberar(@PathVariable Long id) {
        logger.info("Nodo {}: PUT /api/donantes/{}/liberar - Intentando liberar donante", nodeIdentity.getNodeId(), id);
        return gestion.liberarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/compatibles")
    public List<RegistroDonante> buscarCompatibles(@RequestParam String organo,
                                                     @RequestParam String sangre) {
        List<RegistroDonante> resultado = gestion.buscarCompatibles(organo, sangre);
        logger.info("Nodo {}: GET /api/donantes/compatibles - {} compatibles encontrados", 
            nodeIdentity.getNodeId(), resultado.size());
        return resultado;
    }

    @GetMapping("/estadisticas")
    public Map<String, Long> estadisticas() {
        logger.info("Nodo {}: GET /api/donantes/estadisticas - Obteniendo estadísticas", 
            nodeIdentity.getNodeId());
        return gestion.obtenerEstadisticas();
    }
}
