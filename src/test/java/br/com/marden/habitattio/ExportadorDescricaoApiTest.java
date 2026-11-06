package br.com.marden.habitattio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;

/**
 * Descrição incompleta é recusada com mensagem clara e não é gravada (FR-008). Cada teste
 * estraga uma coisa numa cópia do arquivo gerado.
 */
class ExportadorDescricaoApiTest {

    @ParameterizedTest
    @ValueSource(strings = {"POST /proprietarios", "GET /sessao", "POST /sessao", "DELETE /sessao", "POST /imoveis",
            "GET /imoveis/{id}", "POST /imoveis/{id}/inativacao", "GET /actuator/health"})
    void deveRecusarDescricaoSemUmaOperacao(String chave) throws IOException {
        OpenAPI descricao = descricaoGerada();
        remover(descricao, chave);

        AssertionError erro = assertThrows(AssertionError.class, () -> ExportadorDescricaoApi.verificar(descricao));

        assertTrue(erro.getMessage().contains(chave), erro.getMessage());
    }

    @Test
    void deveRecusarOperacaoProtegidaDescritaComoPublica() throws IOException {
        OpenAPI descricao = descricaoGerada();
        DescricaoApiTest.operacao(descricao, "POST /imoveis").setSecurity(List.of());

        AssertionError erro = assertThrows(AssertionError.class, () -> ExportadorDescricaoApi.verificar(descricao));

        assertTrue(erro.getMessage().contains("POST /imoveis"), erro.getMessage());
    }

    @Test
    void deveRecusarOperacaoPublicaDescritaComoProtegida() throws IOException {
        OpenAPI descricao = descricaoGerada();
        DescricaoApiTest.operacao(descricao, "GET /actuator/health")
                .setSecurity(List.of(new SecurityRequirement().addList("sessao")));

        AssertionError erro = assertThrows(AssertionError.class, () -> ExportadorDescricaoApi.verificar(descricao));

        assertTrue(erro.getMessage().contains("GET /actuator/health"), erro.getMessage());
    }

    @Test
    void deveRecusarDescricaoSemOGuia() throws IOException {
        OpenAPI descricao = descricaoGerada();
        descricao.getInfo().setDescription(null);

        AssertionError erro = assertThrows(AssertionError.class, () -> ExportadorDescricaoApi.verificar(descricao));

        assertTrue(erro.getMessage().contains("guia"), erro.getMessage());
    }

    @Test
    void naoDeveGravarQuandoAVerificacaoFalha(@TempDir Path diretorio) throws IOException {
        Path destino = diretorio.resolve("openapi.yaml");
        Files.writeString(destino, "antigo", StandardCharsets.UTF_8);
        OpenAPI descricao = descricaoGerada();
        remover(descricao, "GET /sessao");

        assertThrows(AssertionError.class, () -> {
            ExportadorDescricaoApi.verificar(descricao);
            ExportadorDescricaoApi.exportar("incompleto", destino);
        });

        assertEquals("antigo", Files.readString(destino, StandardCharsets.UTF_8));
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            assertEquals(List.of(destino), arquivos.toList());
        }
    }

    private static OpenAPI descricaoGerada() throws IOException {
        return Yaml.mapper().readValue(Files.readString(ExportadorDescricaoApi.DESTINO, StandardCharsets.UTF_8),
                OpenAPI.class);
    }

    private static void remover(OpenAPI descricao, String chave) {
        String[] partes = chave.split(" ", 2);
        descricao.getPaths().get(partes[1]).operation(PathItem.HttpMethod.valueOf(partes[0]), null);
    }
}
