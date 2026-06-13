package com.gruposiete.hospital.controller;

import com.gruposiete.hospital.model.RegistroDonante;
import com.gruposiete.hospital.service.GestionDonantes;
import com.gruposiete.hospital.service.GestionLogs;
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
    private final GestionLogs gestionLogs;

    public ControladorDonantes(GestionDonantes gestion, NodeIdentity nodeIdentity, GestionLogs gestionLogs) {
        this.gestion = gestion;
        this.nodeIdentity = nodeIdentity;
        this.gestionLogs = gestionLogs;
    }

    @PostMapping
    public ResponseEntity<RegistroDonante> registrar(@RequestParam String nombre,
                                                       @RequestParam String tipoSangre,
                                                       @RequestParam String organo) {
        logger.info("Nodo {}: Registró un nuevo donante", nodeIdentity.getNodeId());
        gestionLogs.registrar("Registró un nuevo donante");
        return ResponseEntity.ok(gestion.registrarDonante(nombre, tipoSangre, organo));
    }

    @GetMapping
    public List<RegistroDonante> listarTodos() {
        List<RegistroDonante> resultado = gestion.listarDonantes();
        logger.info("Nodo {}: Listó todos los donantes", nodeIdentity.getNodeId());
        gestionLogs.registrar("Listó todos los donantes");
        return resultado;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegistroDonante> buscar(@PathVariable Long id) {
        logger.info("Nodo {}: Consultó donante por ID", nodeIdentity.getNodeId());
        gestionLogs.registrar("Consultó donante por ID");
        return gestion.buscarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/disponibles")
    public List<RegistroDonante> listarDisponibles() {
        List<RegistroDonante> resultado = gestion.listarDonantesDisponibles();
        logger.info("Nodo {}: Listó donantes disponibles", nodeIdentity.getNodeId());
        gestionLogs.registrar("Listó donantes disponibles");
        return resultado;
    }

    @GetMapping("/reservados")
    public List<RegistroDonante> listarReservados() {
        List<RegistroDonante> resultado = gestion.listarDonantesReservados();
        logger.info("Nodo {}: Listó donantes reservados", nodeIdentity.getNodeId());
        gestionLogs.registrar("Listó donantes reservados");
        return resultado;
    }

    @GetMapping("/tipo/{tipo}")
    public List<RegistroDonante> listarPorTipo(@PathVariable String tipo) {
        List<RegistroDonante> resultado = gestion.listarDonantesPorTipoOrgano(tipo);
        logger.info("Nodo {}: Listó donantes por tipo de órgano", nodeIdentity.getNodeId());
        gestionLogs.registrar("Listó donantes por tipo de órgano");
        return resultado;
    }

    @PutMapping("/{id}/reservar")
    public ResponseEntity<RegistroDonante> reservar(@PathVariable Long id) {
        logger.info("Nodo {}: Reservó un donante", nodeIdentity.getNodeId());
        gestionLogs.registrar("Reservó un donante");
        return gestion.reservarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/liberar")
    public ResponseEntity<RegistroDonante> liberar(@PathVariable Long id) {
        logger.info("Nodo {}: Liberó un donante", nodeIdentity.getNodeId());
        gestionLogs.registrar("Liberó un donante");
        return gestion.liberarDonante(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/compatibles")
    public List<RegistroDonante> buscarCompatibles(@RequestParam String organo,
                                                     @RequestParam String sangre) {
        List<RegistroDonante> resultado = gestion.buscarCompatibles(organo, sangre);
        logger.info("Nodo {}: Buscó donantes compatibles", nodeIdentity.getNodeId());
        gestionLogs.registrar("Buscó donantes compatibles");
        return resultado;
    }

    @GetMapping("/estadisticas")
    public Map<String, Long> estadisticas() {
        logger.info("Nodo {}: Consultó estadísticas", nodeIdentity.getNodeId());
        gestionLogs.registrar("Consultó estadísticas");
        return gestion.obtenerEstadisticas();
    }
}
