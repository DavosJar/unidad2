package com.gruposiete.hospital;

import com.gruposiete.hospital.service.GestionLogs;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class HospitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(HospitalApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup(ApplicationReadyEvent event) {
        GestionLogs logs = event.getApplicationContext().getBean(GestionLogs.class);
        logs.registrar("Aplicación iniciada");
    }
}
