package com.icers.ford.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration.access}") long accessExpiration,
            @Value("${jwt.expiration.refresh}") long refreshExpiration
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.accessTokenExpiration = accessExpiration;
        this.refreshTokenExpiration = refreshExpiration;
    }

    // GERAÇÃO DE TOKENS

    /**
     * Gera o access token com claims do usuário.
     * Expiração: 8 horas (configurável em application.properties)
     */
    public String generateAccessToken(Long userId, String email, String role) {
        return buildToken(
                Map.of(
                        "userId", userId,
                        "role", role,
                        "type", "ACCESS"
                ),
                email,
                accessTokenExpiration
        );
    }

    /**
     * Gera o refresh token — contém apenas o email e o tipo.
     * Expiração: 7 dias (configurável em application.properties)
     */
    public String generateRefreshToken(String email) {
        return buildToken(
                Map.of("type", "REFRESH"),
                email,
                refreshTokenExpiration
        );
    }

    private String buildToken(Map<String, Object> extraClaims,
                              String subject,
                              long expiration) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(signingKey)
                .compact();
    }

    // EXTRAÇÃO DE CLAIMS

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // VALIDAÇÃO

    /**
     * Valida se o token é um access token válido para o email informado.
     */
    public boolean isAccessTokenValid(String token, String email) {
        try {
            String tokenEmail = extractEmail(token);
            String tokenType = extractTokenType(token);
            return tokenEmail.equals(email)
                    && "ACCESS".equals(tokenType)
                    && !isTokenExpired(token);
        } catch (Exception e) {
            log.warn("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Valida se o token é um refresh token válido.
     */
    public boolean isRefreshTokenValid(String token) {
        try {
            String tokenType = extractTokenType(token);
            return "REFRESH".equals(tokenType) && !isTokenExpired(token);
        } catch (Exception e) {
            log.warn("Refresh token inválido: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}