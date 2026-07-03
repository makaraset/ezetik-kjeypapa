package com.ezetik.kjeypapa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EzetikKjeypapaApplication {

	public static void main(String[] args) {
		SpringApplication.run(EzetikKjeypapaApplication.class, args);
	}

}
