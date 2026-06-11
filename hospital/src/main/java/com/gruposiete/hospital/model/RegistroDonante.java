package com.gruposiete.hospital.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*id	Entero (PK)	ID único del registro.
nombre	Texto	Nombre del donante.
tipoSangre	Texto	Tipo de sangre (O+, A-, etc.).
organo	Texto	El órgano (ej: "Corazón", "Riñón").
activo	Booleano	Este es el campo clave. true significa disponible; false significa ya reservado */
@Entity

@Table(name = "registros_donantes")
public class RegistroDonante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String tipoSangre;
    private String organo;
    private boolean disponible;

    public RegistroDonante() {
    }

    public RegistroDonante(Long id, String nombre, String tipoSangre, String organo, boolean disponible) {
        this.id = id;
        this.nombre = nombre;
        this.tipoSangre = tipoSangre;
        this.organo = organo;
        this.disponible = disponible;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipoSangre() {
        return tipoSangre;
    }

    public void setTipoSangre(String tipoSangre) {
        this.tipoSangre = tipoSangre;
    }

    public String getOrgano() {
        return organo;
    }

    public void setOrgano(String organo) {
        this.organo = organo;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }
}
