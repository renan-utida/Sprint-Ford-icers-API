package com.icers.ford.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConfigRequest(

        @NotEmpty(message = "A lista de atributos padrão não pode ser vazia")
        @Size(max = 20, message = "Máximo de 20 atributos padrão")
        List<String> atributosPadrao
) {}