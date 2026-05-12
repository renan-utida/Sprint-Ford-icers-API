package com.icers.ford.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${llm.api.timeout.seconds:30}")
    private int timeoutSeconds;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        int timeoutMs = timeoutSeconds * 1000;

        // Timeout para estabelecer conexão
        factory.setConnectTimeout(timeoutMs);

        // Timeout para receber resposta — LLMs podem demorar
        factory.setReadTimeout(timeoutMs);

        return new RestTemplate(factory);
    }
}