package br.com.marden.habitattio.adapters.inbound.rest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/sessao")
@Tag(name = "Sessão", description = "Login, logout e obtenção do token CSRF.")
public class SessaoController {

    private static final String SET_COOKIE_XSRF = "`XSRF-TOKEN=...; Path=/` (legível pelo cliente, sem `HttpOnly`).";

    @GetMapping
    @Operation(operationId = "consultarSessao", summary = "Quem está autenticado",
            description = """
                    Também é a forma de obter o cookie `XSRF-TOKEN` antes do cadastro e do login: mesmo
                    respondendo 401, a resposta já traz o cookie.""",
            security = @SecurityRequirement(name = "sessao"))
    @ApiResponse(responseCode = "200", description = "Sessão válida.",
            headers = @Header(name = "Set-Cookie", description = SET_COOKIE_XSRF, schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SessaoResponse.class),
                    examples = @ExampleObject(name = "sessao", value = """
                            {"id": 1, "nome": "Ana Souza", "email": "ana@exemplo.com"}""")))
    @ApiResponse(responseCode = "401", description = "Sem sessão. Traz o cookie `XSRF-TOKEN`.",
            headers = @Header(name = "Set-Cookie", description = SET_COOKIE_XSRF, schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/ProblemDetail"),
                    examples = @ExampleObject(name = "semSessao", value = """
                            {"title": "Unauthorized", "status": 401, "detail": "Autenticação necessária.", "instance": "/sessao"}""")))
    public SessaoResponse consultar(@AuthenticationPrincipal ProprietarioAutenticado proprietario) {
        return new SessaoResponse(proprietario.id(), proprietario.nome(), proprietario.email());
    }

    public record SessaoResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email") String email) {
    }
}
