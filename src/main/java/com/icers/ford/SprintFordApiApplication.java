package com.icers.ford;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SprintFordApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SprintFordApiApplication.class, args);

		System.out.println("========================================================");
		System.out.println("			SpecRadar API — Ford FIAP 2026				");
		System.out.println("		  Inteligência Competitiva Automotiva			");
		System.out.println("========================================================");
		System.out.println("Swagger UI:   	   http://localhost:8080/swagger-ui.html");
		System.out.println("API Docs:              http://localhost:8080/v3/api-docs");
		System.out.println("--------------------------------------------------------");
		System.out.println("Login (ANALYST): 	analyst@specradar.com / Analyst@2026");
		System.out.println("Login (ADMIN):    	  admin@specradar.com   / Admin@2026");
		System.out.println("--------------------------------------------------------");
		System.out.println("Perfil ativo:  										 DEV");
		System.out.println("Banco:         								 Oracle FIAP");
		System.out.println("LLM:           		  Google Gemini 3.7 Flash (gratuito)");
		System.out.println("========================================================");
	}
}
