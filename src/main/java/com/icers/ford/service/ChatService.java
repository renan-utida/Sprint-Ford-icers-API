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
    public ChatResponse processar(String mensagem, Usuario usuario, String ip) {
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
                    intencao.versao() != null ? intencao.versao() : "base",
                    intencao.atributos().isEmpty()
                            ? ATRIBUTOS_PADRAO
                            : intencao.atributos()
            );

            SpecResponse ficha = specService.query(request, usuario, ip);
            String resposta = montarRespostaConversacional(ficha);
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
                "bmw", "audi", "volvo", "land rover", "caoa chery",
                "kia", "subaru", "suzuki"
        };

        for (String marca : marcas) {
            if (msg.contains(marca)) {
                return marca.substring(0, 1).toUpperCase() + marca.substring(1);
            }
        }
        return null;
    }

    private String extrairModelo(String msg, String marca) {
        if (marca == null) return null;

        // Modelos mais consultados por marca
        String[][] modelosPorMarca = {
                {"toyota", "hilux", "corolla", "yaris", "sw4", "rav4",
                        "land cruiser", "prius"},
                {"ford", "ranger", "territory", "bronco", "maverick",
                        "edge", "expedition"},
                {"chevrolet", "s10", "tracker", "onix", "cruze",
                        "trailblazer", "equinox", "blazer"},
                {"volkswagen", "amarok", "polo", "virtus", "t-cross",
                        "taos", "tiguan", "nivus"},
                {"fiat", "toro", "strada", "fastback", "pulse",
                        "cronos", "doblo", "mobi"},
                {"hyundai", "creta", "tucson", "hb20", "santa fe",
                        "ioniq", "kona"},
                {"jeep", "compass", "commander", "renegade", "wrangler",
                        "gladiator"},
                {"nissan", "frontier", "kicks", "sentra", "versa"},
                {"mitsubishi", "l200", "outlander", "eclipse cross",
                        "pajero"},
                {"ram", "rampage", "1500", "2500"},
                {"honda", "civic", "hrv", "crv", "wrv", "city"},
                {"renault", "duster", "kwid", "captur", "oroch"},
                {"kia", "sportage", "carnival", "cerato", "stinger"},
                {"bmw", "x1", "x3", "x5", "320i", "m3"},
                {"mercedes", "gla", "glc", "gle", "c200", "a200"},
                {"audi", "q3", "q5", "a3", "a4", "q8"}
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
                "srx", "srv", "storm", "midnight", "black",
                "tungsten", "titanium", "sport", "sr",
                "trailhawk", "overland", "rubicon", "sahara",
                "r-line", "highline", "comfortline", "trendline",
                "launch edition", "premier", "ltz", "ltz+",
                "lobo", "facelift"
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
                {"motorização", "motor"},
                {"potência", "potencia"},
                {"potencia", "potencia"},
                {"cv", "potencia"},
                {"cavalos", "potencia"},
                {"hp", "potencia"},
                {"torque", "torque"},
                {"nm", "torque"},
                {"câmbio", "transmissao"},
                {"cambio", "transmissao"},
                {"transmissão", "transmissao"},
                {"transmissao", "transmissao"},
                {"tração", "tracao"},
                {"tracao", "tracao"},
                {"4x4", "tracao"},
                {"4wd", "tracao"},
                {"awd", "tracao"},
                {"consumo", "consumo"},
                {"km/l", "consumo"},
                {"preço", "preco"},
                {"preco", "preco"},
                {"valor", "preco"},
                {"custo", "preco"},
                {"suspensão", "suspensao"},
                {"suspensao", "suspensao"},
                {"amortecedor", "suspensao"},
                {"freio", "freios"},
                {"abs", "freios"},
                {"dimensão", "dimensoes"},
                {"dimensoes", "dimensoes"},
                {"comprimento", "dimensoes"},
                {"largura", "dimensoes"},
                {"altura", "dimensoes"},
                {"tamanho", "dimensoes"},
                {"porta-malas", "porta-malas"},
                {"bagageiro", "porta-malas"},
                {"capacidade", "capacidade_carga"},
                {"carga", "capacidade_carga"},
                {"reboque", "capacidade_reboque"},
                {"aceleração", "aceleracao"},
                {"aceleracao", "aceleracao"},
                {"0-100", "aceleracao"},
                {"velocidade", "velocidade_maxima"},
                {"segurança", "seguranca"},
                {"airbag", "seguranca"},
                {"ncap", "seguranca"},
                {"garantia", "garantia"},
                {"peso", "peso"},
                {"rodas", "rodas"},
                {"aro", "rodas"},
                {"pneu", "pneus"},
                {"farol", "iluminacao"},
                {"led", "iluminacao"},
                {"tela", "multimidia"},
                {"multimídia", "multimidia"},
                {"carplay", "multimidia"},
                {"android auto", "multimidia"}
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

    private String montarRespostaConversacional(SpecResponse ficha) {
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