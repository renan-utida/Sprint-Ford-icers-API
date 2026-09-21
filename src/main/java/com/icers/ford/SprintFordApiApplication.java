package com.icers.ford;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SprintFordApiApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context =
				SpringApplication.run(SprintFordApiApplication.class, args);

		Environment env = context.getEnvironment();

		String porta = env.getProperty("server.port", "8080");
		String perfil = env.getProperty("spring.profiles.active", "dev");
		boolean sslAtivo = Boolean.parseBoolean(env.getProperty("server.ssl.enabled", "false"));
		boolean swaggerAtivo = Boolean.parseBoolean(env.getProperty("springdoc.swagger-ui.enabled", "true"));
		String protocolo = sslAtivo ? "https" : "http";
		// dev-h2 é um perfil ADICIONAL (Fase C) — dev continua Oracle por
		// enquanto. Quando a virada de verdade acontecer (dev = H2, fim do
		// projeto), migrar esse bloco todo pra dentro do "if" do perfil dev.
		boolean isDevH2 = "dev-h2".equalsIgnoreCase(perfil);

		System.out.println("\n========================================================");
		System.out.println("			SpecRadar API — Ford FIAP 2026				");
		System.out.println("		  Inteligência Competitiva Automotiva			");
		System.out.println("========================================================");

		if (swaggerAtivo) {
			System.out.println("Swagger UI:   " + protocolo + "://localhost:" + porta + "/swagger-ui.html");
			System.out.println("API Docs:     " + protocolo + "://localhost:" + porta + "/v3/api-docs");
		} else {
			System.out.println("Swagger UI:   desabilitado neste perfil (" + perfil + ")");
		}

		System.out.println("--------------------------------------------------------");
		System.out.println("Login (ANALYST): 	analyst@specradar.com / Analyst@2026");
		System.out.println("Login (ADMIN):    	  admin@specradar.com   / Admin@2026");
		System.out.println("--------------------------------------------------------");
		System.out.println("Perfil ativo:  " + perfil.toUpperCase() + " (" + protocolo.toUpperCase() + ", porta " + porta + ")");
		if (isDevH2) {
			System.out.println("Banco:         H2 em memória (usuário: sa)");
		} else {
			System.out.println("Banco:         Oracle FIAP (usuário: " + env.getProperty("spring.datasource.username") + ")");
		}
		System.out.println("LLM:           Google Gemini 3.7 Flash (gratuito)");

		if (isDevH2) {
			System.out.println("--------------------------------------------------------");
			System.out.println("H2 Console:    " + protocolo + "://localhost:" + porta + "/h2-console");
			System.out.println("JDBC URL:      jdbc:h2:mem:specradar");
			System.out.println("Usuário:       sa (senha em branco)");
		}

		if (sslAtivo) {
			System.out.println("--------------------------------------------------------");
			System.out.println("AVISO SSL: certificado self-signed — o navegador exibirá");
			System.out.println("aviso de segurança. Clique em \"Avançado\" e \"Prosseguir");
			System.out.println("assim mesmo\" para acessar. Isso é esperado.");
		}

		System.out.println("========================================================\n");
	}
}
