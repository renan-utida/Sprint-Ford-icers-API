package com.icers.ford.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolve o IP real do cliente de forma segura, centralizando uma
 * lógica que antes estava duplicada em 4 lugares diferentes
 * (RateLimitFilter, AuthController, SpecController, UsuarioController).
 * <p>
 * Por padrão, NÃO confia no header X-Forwarded-For — qualquer cliente
 * externo pode mandar esse header com qualquer valor (foi exatamente
 * assim que contornamos o rate limit por IP nos nossos próprios testes).
 * Sem um proxy reverso de verdade na frente — que é a
 * realidade atual do projeto, o Tomcat embutido fica exposto direto
 * na porta 8080 — não existe jeito de diferenciar um
 * X-Forwarded-For legítimo de um forjado, então o mais seguro é
 * ignorá-lo por completo.
 * <p>
 * Só passa a confiar nesse header quando a conexão TCP direta
 * (request.getRemoteAddr()) vier de um IP explicitamente cadastrado
 * em security.trusted-proxies (.env / application.properties) — que
 * fica vazio por padrão. Quando o projeto for implantado atrás de um
 * proxy/load balancer de verdade, basta cadastrar o IP dele ali.
 */
@Slf4j
@Component
public class IpResolver {

    private final Set<String> proxiesConfiaveis;

    public IpResolver(
            @Value("${security.trusted-proxies:}") String proxiesConfiaveisCsv
    ) {
        this.proxiesConfiaveis = proxiesConfiaveisCsv == null || proxiesConfiaveisCsv.isBlank()
                ? Set.of()
                : Arrays.stream(proxiesConfiaveisCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        if (this.proxiesConfiaveis.isEmpty()) {
            log.info("IpResolver: nenhum proxy confiável configurado — " +
                    "X-Forwarded-For será sempre ignorado.");
        }
    }

    public String resolverIp(HttpServletRequest request) {
        String ipDireto = request.getRemoteAddr();

        if (!proxiesConfiaveis.contains(ipDireto)) {
            // Conexão não veio de um proxy que cadastramos como
            // confiável — X-Forwarded-For pode ser forjado por
            // qualquer cliente direto. Ignora e usa o IP da conexão
            // TCP real.
            return ipDireto;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return ipDireto;
        }

        // Primeiro IP da cadeia = cliente original (os demais, se
        // houver, são proxies intermediários adicionais)
        return forwarded.split(",")[0].trim();
    }
}