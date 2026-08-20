package com.prueba.factura;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FacturaApplication {

	public static void main(String[] args) {
		SpringApplication.run(FacturaApplication.class, args);
	}

}
