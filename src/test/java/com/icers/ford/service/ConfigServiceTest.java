package com.icers.ford.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icers.ford.dto.response.ConfigResponse;
import com.icers.ford.model.Config;
import com.icers.ford.model.Usuario;
import com.icers.ford.repository.ConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes - ConfigService")
public class ConfigServiceTest {

    @Mock
    private ConfigRepository configRepository;

    // ObjectMapper real (não mockado) — é uma lib bem estabelecida, mockar
    // writeValueAsString/readValue exigiria replicar o contrato JSON à mão
    // sem testar o comportamento real de serialização.
    private ObjectMapper objectMapper;

    private ConfigService configService;

    private Usuario admin;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        configService = new ConfigService(configRepository, objectMapper);

        admin = Usuario.builder()
                .id(1L)
                .email("admin@specradar.com")
                .build();
    }

    private Config configComAtributos(String json, int intervalo) {
        return Config.builder()
                .id(1L)
                .atributosPadraoJson(json)
                .intervaloReverificacaoDias(intervalo)
                .atualizadoEm(LocalDateTime.now())
                .atualizadoPor(admin)
                .build();
    }

    // getAtributosPadrao

    @Test
    @DisplayName("Deve retornar os atributos salvos quando a config existe")
    public void testGetAtributosPadraoComConfigExistente() {
        Config config = configComAtributos("[\"motor\",\"potencia\"]", 15);
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        List<String> resultado = configService.getAtributosPadrao();

        assertEquals(List.of("motor", "potencia"), resultado);
    }

    @Test
    @DisplayName("Deve retornar o fallback quando a tabela sr_config está vazia")
    public void testGetAtributosPadraoSemConfig() {
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        List<String> resultado = configService.getAtributosPadrao();

        assertTrue(resultado.contains("motor"));
        assertTrue(resultado.contains("modos_amortecedor"));
    }

    @Test
    @DisplayName("Deve retornar o fallback quando o JSON salvo está corrompido")
    public void testGetAtributosPadraoComJsonCorrompido() {
        Config config = configComAtributos("isso não é um JSON válido", 15);
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        List<String> resultado = configService.getAtributosPadrao();

        assertTrue(resultado.contains("motor"));
    }

    // getIntervaloReverificacaoDias

    @Test
    @DisplayName("Deve retornar o intervalo salvo quando a config existe")
    public void testGetIntervaloComConfigExistente() {
        Config config = configComAtributos("[\"motor\"]", 20);
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        assertEquals(20, configService.getIntervaloReverificacaoDias());
    }

    @Test
    @DisplayName("Deve retornar o fallback de 15 dias quando a tabela sr_config está vazia")
    public void testGetIntervaloSemConfig() {
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertEquals(15, configService.getIntervaloReverificacaoDias());
    }

    // getConfig

    @Test
    @DisplayName("Deve retornar ConfigResponse completo quando a config existe")
    public void testGetConfigComConfigExistente() {
        LocalDateTime atualizadoEm = LocalDateTime.now().minusDays(1);
        Config config = Config.builder()
                .id(1L)
                .atributosPadraoJson("[\"motor\",\"torque\"]")
                .intervaloReverificacaoDias(18)
                .atualizadoEm(atualizadoEm)
                .atualizadoPor(admin)
                .build();
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        ConfigResponse resultado = configService.getConfig();

        assertEquals(List.of("motor", "torque"), resultado.atributosPadrao());
        assertEquals(18, resultado.intervaloReverificacaoDias());
        assertEquals(atualizadoEm, resultado.atualizadoEm());
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException quando a tabela sr_config está vazia")
    public void testGetConfigSemConfigLancaExcecao() {
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> configService.getConfig());

        assertTrue(ex.getMessage().contains("V6"));
    }

    // atualizarConfig

    @Test
    @DisplayName("Deve atualizar config existente e retornar os novos valores")
    public void testAtualizarConfigExistente() {
        Config configExistente = configComAtributos("[\"motor\"]", 15);
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(configExistente));
        when(configRepository.save(any(Config.class))).thenReturn(configExistente);

        List<String> novosAtributos = List.of("torque", "consumo");
        ConfigResponse resultado = configService.atualizarConfig(novosAtributos, 25, admin);

        assertEquals(novosAtributos, resultado.atributosPadrao());
        assertEquals(25, resultado.intervaloReverificacaoDias());
        assertNotNull(resultado.atualizadoEm());
    }

    @Test
    @DisplayName("Deve gravar os campos corretos na entidade antes de salvar")
    public void testAtualizarConfigGravaCamposCorretos() {
        Config configExistente = configComAtributos("[\"motor\"]", 15);
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(configExistente));
        when(configRepository.save(any(Config.class))).thenReturn(configExistente);

        List<String> novosAtributos = List.of("torque", "consumo");
        configService.atualizarConfig(novosAtributos, 25, admin);

        ArgumentCaptor<Config> captor = ArgumentCaptor.forClass(Config.class);
        verify(configRepository, times(1)).save(captor.capture());

        Config salvo = captor.getValue();
        assertEquals("[\"torque\",\"consumo\"]", salvo.getAtributosPadraoJson());
        assertEquals(25, salvo.getIntervaloReverificacaoDias());
        assertEquals(admin, salvo.getAtualizadoPor());
        assertNotNull(salvo.getAtualizadoEm());
    }

    @Test
    @DisplayName("Deve criar uma config nova quando a tabela sr_config está vazia")
    public void testAtualizarConfigCriaNovaQuandoNaoExiste() {
        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(configRepository.save(any(Config.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> novosAtributos = List.of("motor");
        ConfigResponse resultado = configService.atualizarConfig(novosAtributos, 10, admin);

        assertEquals(novosAtributos, resultado.atributosPadrao());
        assertEquals(10, resultado.intervaloReverificacaoDias());
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException se a serialização dos atributos falhar")
    public void testAtualizarConfigFalhaNaSerializacaoLancaExcecao() throws Exception {
        ObjectMapper objectMapperComFalha = mock(ObjectMapper.class);
        when(objectMapperComFalha.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("falha simulada") {});
        ConfigService servicoComFalha = new ConfigService(configRepository, objectMapperComFalha);

        when(configRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> servicoComFalha.atualizarConfig(List.of("motor"), 15, admin));

        verify(configRepository, never()).save(any());
    }
}
