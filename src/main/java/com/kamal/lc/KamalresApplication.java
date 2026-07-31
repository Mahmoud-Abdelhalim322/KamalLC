package com.kamal.lc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KamalresApplication {

	public static void main(String[] args) {
		SpringApplication.run(KamalresApplication.class, args);
	}

}
