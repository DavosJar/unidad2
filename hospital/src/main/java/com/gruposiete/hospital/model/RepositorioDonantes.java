package com.gruposiete.hospital.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RepositorioDonantes extends JpaRepository<RegistroDonante, Long> {

    List<RegistroDonante> findByDisponibleTrue();

    List<RegistroDonante> findByDisponibleFalse();

    List<RegistroDonante> findByOrgano(String organo);

    @Query("SELECT d FROM RegistroDonante d WHERE d.organo = :organo AND d.tipoSangre = :tipoSangre AND d.disponible = true")
    List<RegistroDonante> buscarCompatibles(@Param("organo") String organo, @Param("tipoSangre") String tipoSangre);

    long countByDisponibleTrue();

    long countByDisponibleFalse();
}
