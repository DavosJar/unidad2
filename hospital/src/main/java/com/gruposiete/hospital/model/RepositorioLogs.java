package com.gruposiete.hospital.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RepositorioLogs extends JpaRepository<LogEntry, Long> {
    List<LogEntry> findAllByOrderByTimestampDesc();
}
