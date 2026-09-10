package com.icers.ford.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Criptografia simétrica AES-256-GCM para dados sensíveis em repouso.
 * <p>
 * GCM é um modo autenticado — garante não só confidencialidade, mas
 * também integridade: se o texto cifrado for adulterado (ou a chave
 * mudar), a descriptografia falha com exceção em vez de devolver dado
 * corrompido silenciosamente.
 * <p>
 * Formato armazenado: Base64(IV de 12 bytes || texto cifrado + tag de
 * autenticação). O IV não é segredo — só precisa ser único a cada
 * operação, e por isso vai junto com o dado, não junto da chave.
 */
@Component
public class AesEncryptionService {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final SecretKeySpec chave;

    public AesEncryptionService(@Value("${aes.secret.key}") String chaveBase64) {
        byte[] chaveBytes = Base64.getDecoder().decode(chaveBase64);
        if (chaveBytes.length != 32) {
            throw new IllegalStateException(
                    "AES_SECRET_KEY deve decodificar para exatamente 32 bytes (256 bits). " +
                            "Tamanho atual: " + chaveBytes.length + " bytes."
            );
        }
        this.chave = new SecretKeySpec(chaveBytes, "AES");
    }

    public String encrypt(String textoPlano) {
        if (textoPlano == null) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));

            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cifrado.length);
            buffer.put(iv);
            buffer.put(cifrado);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar dado sensível", e);
        }
    }

    public String decrypt(String textoCifradoBase64) {
        if (textoCifradoBase64 == null) {
            return null;
        }
        try {
            byte[] dados = Base64.getDecoder().decode(textoCifradoBase64);

            ByteBuffer buffer = ByteBuffer.wrap(dados);
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));

            byte[] textoPlano = cipher.doFinal(cifrado);
            return new String(textoPlano, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Falha ao descriptografar dado sensível — dado pode estar corrompido, " +
                            "em texto puro de antes da criptografia ser ativada, ou a chave mudou",
                    e
            );
        }
    }
}