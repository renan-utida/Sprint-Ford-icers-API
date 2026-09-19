package com.icers.ford.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Value("${llm.api.timeout.seconds:30}")
    private int timeoutSeconds;

    @Value("${llm.api.timeout-pdf.seconds:120}")
    private int timeoutPdfSeconds;

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return construir(timeoutSeconds);
    }

    /**
     * RestTemplate dedicado a chamadas multimodais (PDF via inlineData).
     * Timeout bem maior que o de texto — investigação do Grupo 9 mostrou
     * respostas multimodais levando bem mais tempo (ou nem retornando,
     * caso em que o timeout maior só evita um 503/timeout precoce do
     * lado do cliente antes do Gemini ter chance de responder).
     * Nome do bean vem do nome do método ("restTemplatePdf") — é isso
     * que o @Qualifier("restTemplatePdf") no LlmClient casa, não uma
     * anotação aqui.
     */
    @Bean
    public RestTemplate restTemplatePdf() {
        return construir(timeoutPdfSeconds);
    }

    private RestTemplate construir(int timeoutSegundos) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        int timeoutMs = timeoutSegundos * 1000;

        // Timeout para estabelecer conexão
        factory.setConnectTimeout(timeoutMs);

        // Timeout para receber resposta — LLMs podem demorar
        factory.setReadTimeout(timeoutMs);

        return new RestTemplate(factory);
    }
}