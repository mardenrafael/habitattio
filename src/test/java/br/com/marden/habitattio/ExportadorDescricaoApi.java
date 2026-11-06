package br.com.marden.habitattio;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Verifica o conteúdo mínimo da descrição da API (contracts/descricao-api.md, D1 e D2) e a grava
 * em {@code docs/api/openapi.yaml} de forma atômica (D5). Sem Spring.
 */
final class ExportadorDescricaoApi {

    static final Path DESTINO = Path.of("docs", "api", "openapi.yaml");

    /** Operações obrigatórias e os esquemas exigidos juntos (D1); vazio = pública. */
    private static final Map<String, Set<String>> OPERACOES = operacoes();

    private static final List<String> MARCADORES_DO_GUIA = List.of("GET /sessao", "XSRF-TOKEN", "X-XSRF-TOKEN",
            "30 min", "application/problem+json", "404", "./mvnw test", "gerado", "referência");

    private ExportadorDescricaoApi() {
    }

    /** Junta tudo o que falta numa só falha, uma linha por item. */
    static void verificar(OpenAPI descricao) {
        List<String> faltas = new ArrayList<>();
        OPERACOES.forEach((chave, esquemas) -> verificarOperacao(descricao, chave, esquemas, faltas));
        verificarGuia(descricao, faltas);
        verificarEsquemas(descricao, faltas);
        if (!faltas.isEmpty()) {
            throw new AssertionError("Descrição da API incompleta:\n- " + String.join("\n- ", faltas));
        }
    }

    private static void verificarOperacao(OpenAPI descricao, String chave, Set<String> esperados, List<String> faltas) {
        String[] partes = chave.split(" ", 2);
        PathItem item = descricao.getPaths() == null ? null : descricao.getPaths().get(partes[1]);
        Operation operacao = item == null ? null : item.readOperationsMap().get(PathItem.HttpMethod.valueOf(partes[0]));
        if (operacao == null) {
            faltas.add("operação ausente: " + chave);
            return;
        }
        List<SecurityRequirement> exigencias = operacao.getSecurity() != null
                ? operacao.getSecurity() : descricao.getSecurity();
        Set<String> marcados = exigencias == null || exigencias.isEmpty() ? Set.of() : exigencias.get(0).keySet();
        if (!esperados.equals(marcados) || (exigencias != null && exigencias.size() > 1)) {
            faltas.add("segurança errada em " + chave + ": esperava " + esperados + ", veio " + exigencias);
        }
    }

    private static void verificarGuia(OpenAPI descricao, List<String> faltas) {
        String guia = descricao.getInfo() == null ? null : descricao.getInfo().getDescription();
        if (guia == null) {
            faltas.add("guia de sessão e CSRF ausente");
            return;
        }
        MARCADORES_DO_GUIA.stream()
                .filter(marcador -> !guia.contains(marcador))
                .forEach(marcador -> faltas.add("guia de sessão e CSRF sem: " + marcador));
    }

    private static void verificarEsquemas(OpenAPI descricao, List<String> faltas) {
        Map<String, ?> esquemas = descricao.getComponents() == null ? null
                : descricao.getComponents().getSecuritySchemes();
        for (String esquema : List.of("sessao", "csrf")) {
            if (esquemas == null || !esquemas.containsKey(esquema)) {
                faltas.add("esquema de segurança ausente: " + esquema);
            }
        }
    }

    private static Map<String, Set<String>> operacoes() {
        Map<String, Set<String>> operacoes = new LinkedHashMap<>();
        operacoes.put("POST /proprietarios", Set.of("csrf"));
        operacoes.put("GET /sessao", Set.of("sessao"));
        operacoes.put("POST /sessao", Set.of("csrf"));
        operacoes.put("DELETE /sessao", Set.of("sessao", "csrf"));
        operacoes.put("POST /imoveis", Set.of("sessao", "csrf"));
        operacoes.put("GET /imoveis/{id}", Set.of("sessao"));
        operacoes.put("POST /imoveis/{id}/inativacao", Set.of("sessao", "csrf"));
        operacoes.put("GET /actuator/health", Set.of());
        return operacoes;
    }

    /** Grava num temporário do mesmo diretório e move por cima do destino: nunca deixa arquivo parcial. */
    static void exportar(String yaml, Path destino) {
        Path diretorio = destino.toAbsolutePath().getParent();
        Path temporario = null;
        try {
            temporario = Files.createTempFile(diretorio, "openapi", ".yaml");
            Files.writeString(temporario, yaml, StandardCharsets.UTF_8);
            Files.move(temporario, destino, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException e) {
            apagarSemFalhar(temporario);
            throw new UncheckedIOException("Não foi possível gravar " + destino, e);
        }
    }

    private static void apagarSemFalhar(Path arquivo) {
        if (arquivo == null) {
            return;
        }
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignorada) {
            // o erro original é o que importa
        }
    }
}
