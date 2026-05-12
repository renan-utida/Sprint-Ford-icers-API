package com.icers.ford.service;

import com.icers.ford.dto.request.SpecQueryRequest;
import com.icers.ford.dto.response.SpecResponse;
import com.icers.ford.model.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final SpecService specService;

    // Atributos padrão quando o usuário não especifica nenhum
    private static final List<String> ATRIBUTOS_PADRAO = List.of(
            "motor", "potencia", "torque", "transmissao",
            "tracao", "preco", "consumo", "dimensoes"
    );

    /**
     * Processa uma mensagem em linguagem natural.
     * Extrai marca, modelo, versão e atributos da mensagem,
     * delega para o SpecService e monta resposta conversacional.
     */
    public ChatResponse processar(String mensagem, Usuario usuario) {
        log.debug("Processando mensagem: {}", mensagem);

        IntencaoConsulta intencao = extrairIntencao(mensagem);

        if (intencao.marca() == null || intencao.modelo() == null) {
            return ChatResponse.semVeiculo(
                    "Não consegui identificar o veículo na sua mensagem. " +
                            "Tente algo como: 'Especificações da Toyota Hilux 2025' " +
                            "ou 'Motor e potência da Chevrolet S10 High Country'"
            );
        }

        try {
            SpecQueryRequest request = new SpecQueryRequest(
                    intencao.marca(),
                    intencao.modelo(),
                    intencao.versao() != null ? intencao.versao() : "padrão",
                    intencao.atributos().isEmpty()
                            ? ATRIBUTOS_PADRAO
                            : intencao.atributos()
            );

            SpecResponse ficha = specService.query(request, usuario);

            String resposta = montarRespostaConversacional(ficha, intencao);

            return ChatResponse.comFicha(resposta, ficha);

        } catch (Exception e) {
            log.error("Erro ao processar mensagem: {}", e.getMessage());
            return ChatResponse.erro(
                    "Não consegui buscar as especificações agora. " +
                            "Tente novamente em instantes."
            );
        }
    }

    // EXTRAÇÃO DE INTENÇÃO

    /**
     * Extrai marca, modelo, versão e atributos da mensagem.
     * Abordagem baseada em palavras-chave e padrões comuns.
     */
    private IntencaoConsulta extrairIntencao(String mensagem) {
        String msg = mensagem.toLowerCase().trim();

        String marca = extrairMarca(msg);
        String modelo = extrairModelo(msg, marca);
        String versao = extrairVersao(msg);
        List<String> atributos = extrairAtributos(msg);

        return new IntencaoConsulta(marca, modelo, versao, atributos);
    }

    private String extrairMarca(String msg) {
        // Marcas mais comuns no mercado brasileiro
        String[] marcas = {
                "toyota", "ford", "chevrolet", "volkswagen", "fiat",
                "honda", "hyundai", "jeep", "nissan", "mitsubishi",
                "ram", "renault", "peugeot", "citroen", "mercedes",
                "bmw", "audi", "volvo", "land rover", "caoa chery"
        };

        for (String marca : marcas) {
            if (msg.contains(marca)) {
                // Capitaliza a primeira letra
                return marca.substring(0, 1).toUpperCase()
                        + marca.substring(1);
            }
        }
        return null;
    }

    private String extrairModelo(String msg, String marca) {
        if (marca == null) return null;

        // Modelos mais consultados por marca
        String[][] modelosPorMarca = {
                {"toyota", "hilux", "corolla", "yaris", "sw4", "rav4"},
                {"ford", "ranger", "territory", "bronco", "maverick"},
                {"chevrolet", "s10", "tracker", "onix", "cruze", "trailblazer"},
                {"volkswagen", "amarok", "polo", "virtus", "t-cross", "taos"},
                {"fiat", "toro", "strada", "fastback", "pulse", "cronos"},
                {"hyundai", "creta", "tucson", "hb20", "santa fe"},
                {"jeep", "compass", "commander", "renegade", "wrangler"},
                {"nissan", "frontier", "kicks", "sentra"},
                {"mitsubishi", "l200", "outlander", "eclipse cross"},
                {"ram", "rampage", "1500"}
        };

        String marcaLower = marca.toLowerCase();
        for (String[] par : modelosPorMarca) {
            if (par[0].equals(marcaLower)) {
                for (int i = 1; i < par.length; i++) {
                    if (msg.contains(par[i])) {
                        return par[i].substring(0, 1).toUpperCase()
                                + par[i].substring(1);
                    }
                }
            }
        }
        return null;
    }

    private String extrairVersao(String msg) {
        // Padrões comuns de versão: GR-Sport, High Country, Raptor, etc.
        String[] versoes = {
                "raptor", "gr-sport", "gr sport", "high country",
                "wildtrak", "limited", "platinum", "adventure",
                "sr", "srx", "srv", "storm", "midnight",
                "black", "tungsten", "titanium", "sport"
        };

        for (String versao : versoes) {
            if (msg.contains(versao)) {
                return versao.substring(0, 1).toUpperCase()
                        + versao.substring(1);
            }
        }

        // Tenta extrair ano (ex: 2025, 2024)
        Pattern anoPattern = Pattern.compile("\\b(20[2-9][0-9])\\b");
        Matcher matcher = anoPattern.matcher(msg);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private List<String> extrairAtributos(String msg) {
        List<String> atributos = new ArrayList<>();

        // Mapa de palavras-chave para atributos padronizados
        String[][] mapa = {
                {"motor", "motor"},
                {"potência", "potencia"},
                {"potencia", "potencia"},
                {"torque", "torque"},
                {"câmbio", "transmissao"},
                {"transmissão", "transmissao"},
                {"tração", "tracao"},
                {"consumo", "consumo"},
                {"preço", "preco"},
                {"preco", "preco"},
                {"valor", "preco"},
                {"suspensão", "suspensao"},
                {"suspensao", "suspensao"},
                {"freio", "freios"},
                {"dimensão", "dimensoes"},
                {"dimensoes", "dimensoes"},
                {"comprimento", "dimensoes"},
                {"largura", "dimensoes"},
                {"altura", "dimensoes"},
                {"porta-malas", "porta-malas"},
                {"bagageiro", "porta-malas"},
                {"capacidade", "capacidade_carga"},
                {"reboque", "capacidade_reboque"},
                {"aceleração", "aceleracao"},
                {"velocidade", "velocidade_maxima"},
                {"segurança", "seguranca"},
                {"airbag", "seguranca"},
                {"garantia", "garantia"},
                {"peso", "peso"},
                {"rodas", "rodas"},
                {"pneu", "pneus"}
        };

        for (String[] par : mapa) {
            if (msg.contains(par[0])) {
                String atributo = par[1];
                if (!atributos.contains(atributo)) {
                    atributos.add(atributo);
                }
            }
        }

        return atributos;
    }

    // MONTAGEM DA RESPOSTA CONVERSACIONAL

    private String montarRespostaConversacional(SpecResponse ficha,
                                                IntencaoConsulta intencao) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format(
                "Encontrei as especificações da **%s %s %s**:\n\n",
                ficha.marca(), ficha.modelo(), ficha.versao()
        ));

        if (ficha.cacheHit()) {
            sb.append("*(dados do repositório histórico)*\n\n");
        }

        long naoEncontrados = ficha.campos().stream()
                .filter(c -> "NAO_ENCONTRADO".equals(c.confianca()))
                .count();

        if (naoEncontrados > 0) {
            sb.append(String.format(
                    "*%d campo(s) sem dados disponíveis.*\n\n",
                    naoEncontrados
            ));
        }

        return sb.toString();
    }

    // RECORDS INTERNOS

    private record IntencaoConsulta(
            String marca,
            String modelo,
            String versao,
            List<String> atributos
    ) {}

    public record ChatResponse(
            String mensagem,
            SpecResponse ficha,
            boolean sucesso
    ) {
        public static ChatResponse comFicha(String mensagem, SpecResponse ficha) {
            return new ChatResponse(mensagem, ficha, true);
        }

        public static ChatResponse semVeiculo(String mensagem) {
            return new ChatResponse(mensagem, null, false);
        }

        public static ChatResponse erro(String mensagem) {
            return new ChatResponse(mensagem, null, false);
        }
    }
}