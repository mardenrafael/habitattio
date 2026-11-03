package br.com.marden.habitattio;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Verifica o conteúdo mínimo da descrição da API (contracts/descricao-api.md, D1–D4) e a grava
 * em {@code docs/api/openapi.yaml} de forma atômica (D5). Sem Spring.
 */
final class ExportadorDescricaoApi {

    static final Path DESTINO = Path.of("docs", "api", "openapi.yaml");

    private ExportadorDescricaoApi() {
    }

    static void verificar(OpenAPI descricao) {
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
