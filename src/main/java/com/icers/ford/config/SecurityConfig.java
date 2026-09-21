package com.icers.ford.config;

import com.icers.ford.security.JwtAuthFilter;
import com.icers.ford.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8081}")
    private String allowedOriginsStr;

    /**
     * Chain dedicada e mais permissiva, só para o H2 Console — existe de fato
     * apenas quando o perfil dev-h2 está ativo (spring.h2.console.enabled=true);
     * nos demais perfis a rota nem é registrada, então esta chain nunca casa
     * com nada. Isolada da chain principal (@Order(1) = avaliada primeiro) para
     * que o afrouxamento de CSRF/frame-options fique restrito a este path, sem
     * enfraquecer esses headers no resto da API.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                // AntPathRequestMatcher explícito — o padrão do Spring Security
                // 6.x (MvcRequestMatcher, via string) resolve o caminho através
                // do dispatch do Spring MVC, mas o H2 Console é um Servlet puro
                // (registrado fora do @RequestMapping), então o MvcRequestMatcher
                // nunca reconhece o caminho e a request cai na chain principal
                // (confirmado: 401 vindo da chain errada até essa troca).
                .securityMatcher(new AntPathRequestMatcher("/h2-console/**"))
                // Console usa formulários HTML tradicionais, não JWT — CSRF do
                // Spring Security bloquearia o próprio login do console.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // Console roda em <iframe> — o X-Frame-Options: DENY padrão
                // bloquearia o navegador de renderizá-lo. sameOrigin() ainda
                // impede que outro site nos enquadre (clickjacking).
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API stateless — CSRF desabilitado
                .csrf(AbstractHttpConfigurer::disable)

                // CORS configurado via bean abaixo
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Regras de autorização por endpoint
                .authorizeHttpRequests(auth -> auth

                        // Endpoints públicos — não exigem token
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/error"
                        ).permitAll()

                        // Endpoints de admin — apenas ROLE_ADMIN
                        .requestMatchers(
                                "/api/v1/admin/**"
                        ).hasRole("ADMIN")

                        // Todos os outros endpoints exigem autenticação
                        .anyRequest().authenticated()
                )

                // Sem sessão — stateless JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Provider de autenticação com BCrypt
                .authenticationProvider(authenticationProvider())

                // Filtro JWT antes do filtro padrão do Spring
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"codigo_erro\":\"UNAUTHORIZED\"," +
                                            "\"mensagem\":\"Token ausente ou inválido.\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"codigo_erro\":\"FORBIDDEN\"," +
                                            "\"mensagem\":\"Você não tem permissão para acessar este recurso.\"}"
                            );
                        })
                );

        return http.build();
    }

    /**
     * CORS restrito — apenas origens autorizadas.
     * Em produção, configure CORS_ALLOWED_ORIGINS com os domínios reais.
     * Nunca use allowedOrigins("*") em produção.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Origens permitidas — lidas de variável de ambiente
        List<String> origins = List.of(allowedOriginsStr.split(","));
        config.setAllowedOrigins(origins);

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With"
        ));
        config.setExposedHeaders(List.of(
                "Authorization",
                "X-RateLimit-Remaining",
                "Retry-After"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt com fator 12 — custo computacional adequado para produção.
     * Fator 12 leva ~250ms por hash, tornando brute force inviável.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}