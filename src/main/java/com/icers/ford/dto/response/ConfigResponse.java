package com.icers.ford.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Configuração atual do sistema")
public record ConfigResponse(

        @Schema(description = "Atributos usados por padrão quando o chat não identifica nenhum na mensagem")
        List<String> atributosPadrao,

        @Schema(description = "Dias até uma ficha ser considerada desatualizada (2 a 31). " +
                "Fichas já existentes adotam este valor na sua próxima reverificação " +
                "(quando expiram), não imediatamente.")
        Integer intervaloReverificacaoDias,

        @Schema(description = "Data e hora da última alteração")
        LocalDateTime atualizadoEm
) {
    public static ConfigResponse of(List<String> atributosPadrao,
                                    Integer intervaloReverificacaoDias,
                                    LocalDateTime atualizadoEm) {
        return new ConfigResponse(atributosPadrao, intervaloReverificacaoDias, atualizadoEm);
    }
}