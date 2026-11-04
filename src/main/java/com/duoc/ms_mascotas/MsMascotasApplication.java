package com.duoc.ms_mascotas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MsMascotasApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsMascotasApplication.class, args);
	}

}
