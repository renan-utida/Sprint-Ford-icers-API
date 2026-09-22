package com.icers.ford.security;

import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes - JwtService")
public class JwtServiceTest {

    // 64 bytes — folga confortável acima do mínimo de 32 bytes (HS256)
    private static final String SECRET =
            "12345678901234567890123456789012345678901234567890123456789012";

    private static final String SECRET_ALTERNATIVO =
            "98765432109876543210987654321098765432109876543210987654321098";

    private static final long ACCESS_EXPIRATION_MS = 28_800_000L;  // 8h
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 dias

    private JwtService jwtService;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService(SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    }

    private JwtService jwtServiceComExpiracaoJaVencida() {
        // Expiração negativa — o token já nasce expirado, sem precisar de
        // Thread.sleep pra testar o caminho de expiração de forma determinística.
        return new JwtService(SECRET, -1000L, -1000L);
    }

    // Geração — access token

    @Test
    @DisplayName("Deve gerar access token não nulo, com 3 partes")
    public void testGerarAccessToken() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Deve gerar access tokens diferentes para usuários diferentes")
    public void testAccessTokensDiferentesParaUsuariosDiferentes() {
        String tokenAdmin = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");
        String tokenAnalyst = jwtService.generateAccessToken(2L, "analyst@specradar.com", "ANALYST");

        assertNotEquals(tokenAdmin, tokenAnalyst);
    }

    // Geração — refresh token

    @Test
    @DisplayName("Deve gerar refresh token não nulo, com 3 partes")
    public void testGerarRefreshToken() {
        String token = jwtService.generateRefreshToken("admin@specradar.com");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Deve gerar jti diferente a cada refresh token, mesmo pro mesmo email")
    public void testRefreshTokensTemJtiUnico() {
        String token1 = jwtService.generateRefreshToken("admin@specradar.com");
        String token2 = jwtService.generateRefreshToken("admin@specradar.com");

        assertNotEquals(token1, token2);
        assertNotEquals(jwtService.extractJti(token1), jwtService.extractJti(token2));
    }

    // Extração de claims — access token

    @Test
    @DisplayName("Deve extrair email, userId, role e type corretos do access token")
    public void testExtrairClaimsAccessToken() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertEquals("admin@specradar.com", jwtService.extractEmail(token));
        assertEquals(1L, jwtService.extractUserId(token));
        assertEquals("ADMIN", jwtService.extractRole(token));
        assertEquals("ACCESS", jwtService.extractTokenType(token));
    }

    @Test
    @DisplayName("extractJti deve retornar null pro access token (não tem esse claim)")
    public void testExtrairJtiDeAccessTokenRetornaNull() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertNull(jwtService.extractJti(token));
    }

    // Extração de claims — refresh token

    @Test
    @DisplayName("Deve extrair email, jti e type corretos do refresh token")
    public void testExtrairClaimsRefreshToken() {
        String token = jwtService.generateRefreshToken("analyst@specradar.com");

        assertEquals("analyst@specradar.com", jwtService.extractEmail(token));
        assertEquals("REFRESH", jwtService.extractTokenType(token));
        assertNotNull(jwtService.extractJti(token));
    }

    @Test
    @DisplayName("extractRole deve retornar null pro refresh token (não tem esse claim)")
    public void testExtrairRoleDeRefreshTokenRetornaNull() {
        String token = jwtService.generateRefreshToken("analyst@specradar.com");

        assertNull(jwtService.extractRole(token));
    }

    // Extração de expiração

    @Test
    @DisplayName("Deve extrair data de expiração futura do access token")
    public void testExtrairExpiracaoFutura() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        LocalDateTime expiracao = jwtService.extractExpirationAsLocalDateTime(token);

        assertNotNull(expiracao);
        assertTrue(expiracao.isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Deve ter expiração de aproximadamente 8 horas no access token")
    public void testExpiracaoAccessTokenAproximadamenteOitoHoras() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        LocalDateTime expiracao = jwtService.extractExpirationAsLocalDateTime(token);
        LocalDateTime esperado = LocalDateTime.now().plusNanos(ACCESS_EXPIRATION_MS * 1_000_000L);

        long diferencaSegundos = Math.abs(
                java.time.Duration.between(esperado, expiracao).getSeconds());

        // margem de 5s pra absorver o tempo de execução do teste
        assertTrue(diferencaSegundos < 5);
    }

    // Validação — access token

    @Test
    @DisplayName("isAccessTokenValid deve retornar true pro caso correto")
    public void testIsAccessTokenValidoCorreto() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertTrue(jwtService.isAccessTokenValid(token, "admin@specradar.com"));
    }

    @Test
    @DisplayName("isAccessTokenValid deve retornar false se o email não bate")
    public void testIsAccessTokenValidoEmailDiferente() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertFalse(jwtService.isAccessTokenValid(token, "outro@specradar.com"));
    }

    @Test
    @DisplayName("isAccessTokenValid deve retornar false se o token for na verdade um refresh token")
    public void testIsAccessTokenValidoComRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken("admin@specradar.com");

        assertFalse(jwtService.isAccessTokenValid(refreshToken, "admin@specradar.com"));
    }

    @Test
    @DisplayName("isAccessTokenValid deve retornar false pra token expirado")
    public void testIsAccessTokenValidoExpirado() {
        JwtService servicoExpirado = jwtServiceComExpiracaoJaVencida();
        String token = servicoExpirado.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertFalse(servicoExpirado.isAccessTokenValid(token, "admin@specradar.com"));
    }

    @Test
    @DisplayName("isAccessTokenValid deve retornar false (não lançar) pra token malformado")
    public void testIsAccessTokenValidoMalformado() {
        assertFalse(jwtService.isAccessTokenValid("token-invalido-123", "admin@specradar.com"));
    }

    // Validação — refresh token

    @Test
    @DisplayName("isRefreshTokenValid deve retornar true pro caso correto")
    public void testIsRefreshTokenValidoCorreto() {
        String token = jwtService.generateRefreshToken("admin@specradar.com");

        assertTrue(jwtService.isRefreshTokenValid(token));
    }

    @Test
    @DisplayName("isRefreshTokenValid deve retornar false se o token for na verdade um access token")
    public void testIsRefreshTokenValidoComAccessToken() {
        String accessToken = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertFalse(jwtService.isRefreshTokenValid(accessToken));
    }

    @Test
    @DisplayName("isRefreshTokenValid deve retornar false pra token expirado")
    public void testIsRefreshTokenValidoExpirado() {
        JwtService servicoExpirado = jwtServiceComExpiracaoJaVencida();
        String token = servicoExpirado.generateRefreshToken("admin@specradar.com");

        assertFalse(servicoExpirado.isRefreshTokenValid(token));
    }

    @Test
    @DisplayName("isRefreshTokenValid deve retornar false (não lançar) pra token malformado")
    public void testIsRefreshTokenValidoMalformado() {
        assertFalse(jwtService.isRefreshTokenValid("token-invalido-123"));
    }

    // isTokenExpired isolado

    @Test
    @DisplayName("isTokenExpired deve retornar false pra token recém-gerado")
    public void testIsTokenExpiredFalsoParaTokenValido() {
        String token = jwtService.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    @DisplayName("isTokenExpired deve retornar true pra token expirado")
    public void testIsTokenExpiredVerdadeiroParaTokenExpirado() {
        JwtService servicoExpirado = jwtServiceComExpiracaoJaVencida();
        String token = servicoExpirado.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertTrue(servicoExpirado.isTokenExpired(token));
    }

    @Test
    @DisplayName("isTokenExpired deve retornar true (não lançar) pra token malformado")
    public void testIsTokenExpiredVerdadeiroParaTokenMalformado() {
        assertTrue(jwtService.isTokenExpired("token-invalido-123"));
    }

    // Assinatura e integridade

    @Test
    @DisplayName("Deve lançar SignatureException ao extrair claim de token assinado com secret diferente")
    public void testTokenAssinadoComSecretDiferente() {
        JwtService jwtServiceOutraChave =
                new JwtService(SECRET_ALTERNATIVO, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
        String token = jwtServiceOutraChave.generateAccessToken(1L, "admin@specradar.com", "ADMIN");

        assertThrows(SignatureException.class, () -> jwtService.extractEmail(token));
    }

    @Test
    @DisplayName("Deve lançar MalformedJwtException ao extrair claim de token completamente malformado")
    public void testTokenMalformado() {
        assertThrows(MalformedJwtException.class, () -> jwtService.extractEmail("tokeninvalido123"));
    }
}
