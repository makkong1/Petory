package com.linkup.Petory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;

@SpringBootApplication
@ConfigurationProperties(prefix = "app")
public class PetoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(PetoryApplication.class, args);
	}

}
