package com.icers.ford;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SprintFordApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SprintFordApiApplication.class, args);
	}

}
