package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Conteúdo mínimo da descrição gerada (contracts/descricao-api.md, D1–D5), no modo rápido e sem
 * Docker. A descrição é obtida autenticada para não depender da regra pública da US3; o
 * conteúdo é o mesmo que o anônimo recebe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DescricaoApiTest {

    private static final String NAO_AUTENTICADO = "#/components/responses/NaoAutenticado";
    private static final String CSRF_INVALIDO = "#/components/responses/CsrfInvalido";
    private static final String IMOVEL_NAO_ENCONTRADO = "#/components/responses/ImovelNaoEncontrado";
    private static final String ID_INVALIDO = "#/components/responses/IdInvalido";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveDescreverSoOServidorLocal() throws Exception {
        OpenAPI descricao = obterDescricao();

        assertNotNull(descricao.getServers(), "a descrição não declara servidores");
        assertEquals(1, descricao.getServers().size(), "esperava exatamente um servidor");
        assertEquals("http://localhost:8080", descricao.getServers().get(0).getUrl());
    }

    @Test
    void deveUsarOpenApi31() throws Exception {
        OpenAPI descricao = obterDescricao();

        assertTrue(descricao.getOpenapi().startsWith("3.1"), "versão era " + descricao.getOpenapi());
    }

    @Test
    void deveMarcarSegurancaDasOperacoesDeController() throws Exception {
        OpenAPI descricao = obterDescricao();

        Map<String, Set<String>> esperado = Map.of(
                "POST /proprietarios", Set.of("csrf"),
                "GET /sessao", Set.of("sessao"),
                "POST /imoveis", Set.of("sessao", "csrf"),
                "GET /imoveis/{id}", Set.of("sessao"),
                "POST /imoveis/{id}/inativacao", Set.of("sessao", "csrf"));

        esperado.forEach((chave, esquemas) ->
                assertEquals(esquemas, seguranca(descricao, operacao(descricao, chave)), chave));
    }

    @Test
    void deveDeclararAsRespostasDasOperacoesDeController() throws Exception {
        OpenAPI descricao = obterDescricao();

        Map<String, Set<String>> codigos = Map.of(
                "POST /proprietarios", Set.of("201", "400", "403", "409"),
                "GET /sessao", Set.of("200", "401"),
                "POST /imoveis", Set.of("201", "400", "401", "403", "409"),
                "GET /imoveis/{id}", Set.of("200", "400", "401", "404"),
                "POST /imoveis/{id}/inativacao", Set.of("200", "400", "401", "403", "404"));

        codigos.forEach((chave, esperados) -> {
            Map<String, ApiResponse> respostas = operacao(descricao, chave).getResponses();
            assertEquals(esperados, respostas.keySet(), chave);
            respostas.forEach((codigo, resposta) -> {
                if (codigo.equals("403")) {
                    assertEquals(CSRF_INVALIDO, resposta.get$ref(), chave + " 403");
                }
                if (codigo.equals("404")) {
                    assertEquals(IMOVEL_NAO_ENCONTRADO, resposta.get$ref(), chave + " 404");
                }
                if (codigo.equals("401") && chave.contains("/imoveis")) {
                    assertEquals(NAO_AUTENTICADO, resposta.get$ref(), chave + " 401");
                }
                if (codigo.equals("400") && chave.contains("{id}")) {
                    assertEquals(ID_INVALIDO, resposta.get$ref(), chave + " 400");
                }
                if (codigo.startsWith("4") && resposta.get$ref() == null) {
                    assertNotNull(resposta.getContent(), chave + " " + codigo + " sem conteúdo");
                    assertTrue(resposta.getContent().containsKey(PROBLEM_JSON),
                            chave + " " + codigo + " não usa " + PROBLEM_JSON);
                }
            });
        });
    }

    @Test
    void deveTerResumoTagEExemploNasOperacoesDeController() throws Exception {
        OpenAPI descricao = obterDescricao();

        List<String> operacoes = List.of("POST /proprietarios", "GET /sessao", "POST /imoveis",
                "GET /imoveis/{id}", "POST /imoveis/{id}/inativacao");
        Map<String, String> respostasComCorpo = Map.of(
                "POST /proprietarios", "201",
                "GET /sessao", "200",
                "POST /imoveis", "201",
                "POST /imoveis/{id}/inativacao", "200");

        for (String chave : operacoes) {
            Operation operacao = operacao(descricao, chave);
            assertTrue(operacao.getSummary() != null && !operacao.getSummary().isBlank(), chave + " sem summary");
            assertTrue(operacao.getTags() != null && !operacao.getTags().isEmpty(), chave + " sem tags");
        }
        for (String chave : List.of("POST /proprietarios", "POST /imoveis")) {
            assertTrue(temExemplo(operacao(descricao, chave).getRequestBody().getContent()),
                    chave + " sem exemplo no corpo de entrada");
        }
        respostasComCorpo.forEach((chave, codigo) ->
                assertTrue(temExemplo(operacao(descricao, chave).getResponses().get(codigo).getContent()),
                        chave + " " + codigo + " sem exemplo"));
    }

    @Test
    void naoDeveListarRotasInternas() throws Exception {
        OpenAPI descricao = obterDescricao();

        for (String caminho : descricao.getPaths().keySet()) {
            assertFalse(caminho.startsWith("/v3/api-docs"), caminho);
            assertFalse(caminho.startsWith("/swagger-ui"), caminho);
            assertFalse(caminho.startsWith("/error"), caminho);
            assertTrue(!caminho.startsWith("/actuator") || caminho.equals("/actuator/health"), caminho);
        }
    }

    @Test
    void naoDeveExporOProprietarioComoParametro() throws Exception {
        OpenAPI descricao = obterDescricao();

        descricao.getPaths().forEach((caminho, item) -> item.readOperationsMap().forEach((metodo, operacao) -> {
            List<Parameter> parametros = operacao.getParameters() == null ? List.of() : operacao.getParameters();
            parametros.forEach(parametro ->
                    assertFalse("proprietario".equals(parametro.getName()), metodo + " " + caminho));
        }));
    }

    @Test
    void deveGerarDescricaoEstavel() throws Exception {
        String primeira = obterYaml();

        String segunda = obterYaml();

        assertEquals(primeira, segunda);
    }

    @Test
    void deveExportarDescricaoParaDocsApi() throws Exception {
        String yaml = obterYaml();

        ExportadorDescricaoApi.verificar(Yaml.mapper().readValue(yaml, OpenAPI.class));
        ExportadorDescricaoApi.exportar(yaml, ExportadorDescricaoApi.DESTINO);

        assertEquals(yaml, Files.readString(ExportadorDescricaoApi.DESTINO, StandardCharsets.UTF_8));
    }

    private String obterYaml() throws Exception {
        return mockMvc.perform(get("/v3/api-docs.yaml").with(user("documentacao")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private OpenAPI obterDescricao() throws Exception {
        return Yaml.mapper().readValue(obterYaml(), OpenAPI.class);
    }

    static Operation operacao(OpenAPI descricao, String chave) {
        String[] partes = chave.split(" ", 2);
        PathItem item = descricao.getPaths() == null ? null : descricao.getPaths().get(partes[1]);
        assertNotNull(item, chave + " ausente da descrição");
        Operation operacao = item.readOperationsMap().get(PathItem.HttpMethod.valueOf(partes[0]));
        assertNotNull(operacao, chave + " ausente da descrição");
        return operacao;
    }

    /** Esquemas exigidos juntos (uma única exigência "E"); vazio = pública. */
    static Set<String> seguranca(OpenAPI descricao, Operation operacao) {
        List<SecurityRequirement> exigencias = operacao.getSecurity() != null
                ? operacao.getSecurity() : descricao.getSecurity();
        if (exigencias == null || exigencias.isEmpty()) {
            return Set.of();
        }
        assertEquals(1, exigencias.size(), "esperava uma única exigência combinada, veio " + exigencias);
        return new LinkedHashSet<>(exigencias.get(0).keySet());
    }

    private static boolean temExemplo(Map<String, MediaType> conteudo) {
        return conteudo != null && conteudo.values().stream().anyMatch(midia ->
                midia.getExample() != null || (midia.getExamples() != null && !midia.getExamples().isEmpty()));
    }
}
