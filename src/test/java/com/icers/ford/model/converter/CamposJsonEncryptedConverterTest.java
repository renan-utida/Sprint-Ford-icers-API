package com.icers.ford.model.converter;

import com.icers.ford.crypto.AesEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes - CamposJsonEncryptedConverter")
public class CamposJsonEncryptedConverterTest {

    @Mock
    private AesEncryptionService aesEncryptionService;

    @InjectMocks
    private CamposJsonEncryptedConverter converter;

    private static final String TEXTO_PLANO = "{\"motor\":\"V6 3.0L EcoBoost\"}";
    private static final String TEXTO_CIFRADO = "cifrado-base64-simulado==";

    // convertToDatabaseColumn (persistindo)

    @Test
    @DisplayName("Deve delegar a criptografia pro AesEncryptionService e retornar o resultado")
    public void testConvertToDatabaseColumnDelega() {
        when(aesEncryptionService.encrypt(TEXTO_PLANO)).thenReturn(TEXTO_CIFRADO);

        String resultado = converter.convertToDatabaseColumn(TEXTO_PLANO);

        assertEquals(TEXTO_CIFRADO, resultado);
        verify(aesEncryptionService, times(1)).encrypt(TEXTO_PLANO);
        verifyNoMoreInteractions(aesEncryptionService);
    }

    @Test
    @DisplayName("Deve repassar null pro AesEncryptionService sem lógica própria de null-check")
    public void testConvertToDatabaseColumnComNull() {
        when(aesEncryptionService.encrypt(null)).thenReturn(null);

        String resultado = converter.convertToDatabaseColumn(null);

        assertNull(resultado);
        verify(aesEncryptionService, times(1)).encrypt(null);
    }

    @Test
    @DisplayName("Deve propagar exceção do AesEncryptionService sem capturar")
    public void testConvertToDatabaseColumnPropagaExcecao() {
        when(aesEncryptionService.encrypt(anyString()))
                .thenThrow(new IllegalStateException("Falha ao criptografar dado sensível"));

        assertThrows(IllegalStateException.class,
                () -> converter.convertToDatabaseColumn(TEXTO_PLANO));
    }

    // convertToEntityAttribute (lendo)

    @Test
    @DisplayName("Deve delegar a descriptografia pro AesEncryptionService e retornar o resultado")
    public void testConvertToEntityAttributeDelega() {
        when(aesEncryptionService.decrypt(TEXTO_CIFRADO)).thenReturn(TEXTO_PLANO);

        String resultado = converter.convertToEntityAttribute(TEXTO_CIFRADO);

        assertEquals(TEXTO_PLANO, resultado);
        verify(aesEncryptionService, times(1)).decrypt(TEXTO_CIFRADO);
        verifyNoMoreInteractions(aesEncryptionService);
    }

    @Test
    @DisplayName("Deve repassar null pro AesEncryptionService sem lógica própria de null-check")
    public void testConvertToEntityAttributeComNull() {
        when(aesEncryptionService.decrypt(null)).thenReturn(null);

        String resultado = converter.convertToEntityAttribute(null);

        assertNull(resultado);
        verify(aesEncryptionService, times(1)).decrypt(null);
    }

    @Test
    @DisplayName("Deve propagar exceção do AesEncryptionService sem capturar (dado corrompido/chave mudou)")
    public void testConvertToEntityAttributePropagaExcecao() {
        when(aesEncryptionService.decrypt(anyString()))
                .thenThrow(new IllegalStateException("Falha ao descriptografar dado sensível"));

        assertThrows(IllegalStateException.class,
                () -> converter.convertToEntityAttribute(TEXTO_CIFRADO));
    }
}
