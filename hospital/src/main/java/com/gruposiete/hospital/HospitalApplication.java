package com.gruposiete.hospital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HospitalApplication {

	public static void main(String[] args) {
		System.out.println("Iniciando aplicacion...Hospi");
		SpringApplication.run(HospitalApplication.class, args);
	}

}
