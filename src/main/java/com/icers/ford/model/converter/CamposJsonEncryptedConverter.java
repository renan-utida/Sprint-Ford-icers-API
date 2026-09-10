package com.icers.ford.model.converter;

import com.icers.ford.crypto.AesEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Converte campos_json de/para o banco, criptografando com AES-256-GCM.
 * <p>
 * Transparente para o resto do código: FichaTecnica.getCamposJson()/
 * setCamposJson() continuam funcionando com texto plano normalmente —
 * só o valor gravado fisicamente no banco vem cifrado. SpecService e
 * todo o resto do sistema não precisam saber que isso existe.
 * <p>
 * autoApply=false: aplicado só onde explicitamente anotado com
 * @Convert, não em qualquer String da aplicação (evita criptografar
 * campos que não deveriam, como email ou nomes).
 */
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class CamposJsonEncryptedConverter implements AttributeConverter<String, String> {

    private final AesEncryptionService aesEncryptionService;

    @Override
    public String convertToDatabaseColumn(String atributoEmTextoPlano) {
        return aesEncryptionService.encrypt(atributoEmTextoPlano);
    }

    @Override
    public String convertToEntityAttribute(String valorCifradoNoBanco) {
        return aesEncryptionService.decrypt(valorCifradoNoBanco);
    }
}