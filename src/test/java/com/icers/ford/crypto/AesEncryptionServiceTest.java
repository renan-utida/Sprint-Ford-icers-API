package com.icers.ford.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes - AesEncryptionService")
public class AesEncryptionServiceTest {

    // 32 bytes decodificados (AES-256) — chave de teste, nunca usada em produção
    private static final String CHAVE_VALIDA =
            "i7fb6Pa3QzQ1fQ85dmp34TggCSlg8jEi3+nPPIYTFJ8=";

    // Outra chave de 32 bytes, com material diferente da CHAVE_VALIDA
    private static final String CHAVE_VALIDA_ALTERNATIVA =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    // 16 bytes decodificados (AES-128) — tamanho errado pra este serviço
    private static final String CHAVE_16_BYTES =
            "MDAwMDAwMDAwMDAwMDAwMA==";

    // 24 bytes decodificados (AES-192) — tamanho errado pra este serviço
    private static final String CHAVE_24_BYTES =
            "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAw";

    private AesEncryptionService service;

    @BeforeEach
    public void setUp() {
        service = new AesEncryptionService(CHAVE_VALIDA);
    }

    // Construtor

    @Test
    @DisplayName("Deve construir normalmente com chave de 32 bytes")
    public void testConstrutorComChaveValida() {
        assertDoesNotThrow(() -> new AesEncryptionService(CHAVE_VALIDA));
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException com chave de 16 bytes (AES-128)")
    public void testConstrutorComChave16Bytes() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AesEncryptionService(CHAVE_16_BYTES));

        assertTrue(ex.getMessage().contains("32 bytes"));
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException com chave de 24 bytes (AES-192)")
    public void testConstrutorComChave24Bytes() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AesEncryptionService(CHAVE_24_BYTES));

        assertTrue(ex.getMessage().contains("32 bytes"));
    }

    // encrypt/decrypt — casos nulos

    @Test
    @DisplayName("encrypt(null) deve retornar null")
    public void testEncryptNulo() {
        assertNull(service.encrypt(null));
    }

    @Test
    @DisplayName("decrypt(null) deve retornar null")
    public void testDecryptNulo() {
        assertNull(service.decrypt(null));
    }

    // Ida e volta

    @Test
    @DisplayName("Deve criptografar e descriptografar de volta pro texto original")
    public void testRoundTrip() {
        String textoPlano = "Ford Ranger Raptor — motor V6 3.0L EcoBoost, 397cv";

        String cifrado = service.encrypt(textoPlano);
        String decifrado = service.decrypt(cifrado);

        assertNotNull(cifrado);
        assertNotEquals(textoPlano, cifrado);
        assertEquals(textoPlano, decifrado);
    }

    @Test
    @DisplayName("Deve fazer round-trip corretamente com string vazia")
    public void testRoundTripStringVazia() {
        String cifrado = service.encrypt("");
        assertEquals("", service.decrypt(cifrado));
    }

    @Test
    @DisplayName("Deve fazer round-trip corretamente com caracteres UTF-8 (acentos)")
    public void testRoundTripUtf8() {
        String textoPlano = "Configuração de atributos: preço, potência, dimensões, dois pontos: teste";

        String cifrado = service.encrypt(textoPlano);
        String decifrado = service.decrypt(cifrado);

        assertEquals(textoPlano, decifrado);
    }

    @Test
    @DisplayName("Saída do encrypt deve ser Base64 válido")
    public void testSaidaEhBase64Valido() {
        String cifrado = service.encrypt("qualquer texto");

        assertDoesNotThrow(() -> Base64.getDecoder().decode(cifrado));
    }

    // Propriedade de segurança: IV aleatório a cada chamada

    @Test
    @DisplayName("Duas criptografias do mesmo texto devem gerar cifrados diferentes (IV aleatório)")
    public void testDuasCriptografiasGeramCifradosDiferentes() {
        String textoPlano = "mesmo texto";

        String cifrado1 = service.encrypt(textoPlano);
        String cifrado2 = service.encrypt(textoPlano);

        assertNotEquals(cifrado1, cifrado2);
        // mas os dois continuam decifrando pro mesmo texto original
        assertEquals(textoPlano, service.decrypt(cifrado1));
        assertEquals(textoPlano, service.decrypt(cifrado2));
    }

    // Integridade e falhas de descriptografia

    @Test
    @DisplayName("Deve lançar IllegalStateException ao descriptografar Base64 inválido")
    public void testDecryptBase64Invalido() {
        assertThrows(IllegalStateException.class,
                () -> service.decrypt("isso não é base64 válido!!"));
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException ao descriptografar dado adulterado (tag GCM inválida)")
    public void testDecryptDadoAdulterado() {
        String cifrado = service.encrypt("texto original");
        byte[] bytes = Base64.getDecoder().decode(cifrado);

        // Adultera o último byte (parte do texto cifrado/tag de autenticação,
        // nunca o IV, que ocupa só os 12 primeiros bytes)
        bytes[bytes.length - 1] ^= 0x01;
        String cifradoAdulterado = Base64.getEncoder().encodeToString(bytes);

        assertThrows(IllegalStateException.class,
                () -> service.decrypt(cifradoAdulterado));
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException ao descriptografar com chave diferente da usada na criptografia")
    public void testDecryptComChaveDiferente() {
        String cifrado = service.encrypt("dado sensível");

        AesEncryptionService servicoComOutraChave =
                new AesEncryptionService(CHAVE_VALIDA_ALTERNATIVA);

        assertThrows(IllegalStateException.class,
                () -> servicoComOutraChave.decrypt(cifrado));
    }
}
