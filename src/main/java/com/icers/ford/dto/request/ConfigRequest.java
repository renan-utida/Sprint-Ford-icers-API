package com.icers.ford.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConfigRequest(

        @NotEmpty(message = "A lista de atributos padrão não pode ser vazia")
        @Size(max = 20, message = "Máximo de 20 atributos padrão")
        List<String> atributosPadrao,

        @NotNull(message = "Intervalo de reverificação é obrigatório")
        @Min(value = 2, message = "Intervalo mínimo é 2 dias")
        @Max(value = 31, message = "Intervalo máximo é 31 dias")
        Integer intervaloReverificacaoDias
) {}