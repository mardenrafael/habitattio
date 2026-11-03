package br.com.marden.habitattio.adapters.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Monta a descrição OpenAPI da API (specs/004-auto-docs-api, research R2/R3).
 *
 * <p>As operações dos controllers saem das anotações neles; aqui ficam o que é global (servidor,
 * esquemas de segurança, respostas e esquemas compartilhados). A exigência padrão é sessão E
 * CSRF; as operações que pedem só um dos dois, ou nenhum, sobrescrevem.
 */
@Configuration
public class DocumentacaoApiConfiguration {

    static final String PROBLEM_JSON = "application/problem+json";
    static final String PROBLEM_DETAIL = "#/components/schemas/ProblemDetail";

    @Bean
    OpenAPI descricaoApi() {
        return new OpenAPI()
                .info(new Info().title("Habitattio API").version("0.0.1"))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local (modo rápido, H2)")))
                .addSecurityItem(new SecurityRequirement().addList("sessao").addList("csrf"))
                .components(new Components()
                        .addSecuritySchemes("sessao", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Cookie de sessão emitido por `POST /sessao`."))
                        .addSecuritySchemes("csrf", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("Valor do cookie `XSRF-TOKEN` (obtido em `GET /sessao`). "
                                        + "Exigido em `POST`/`DELETE`."))
                        .addSchemas("ProblemDetail", problemDetail())
                        .addResponses("NaoAutenticado", erro("Sem sessão, sessão expirada ou encerrada.",
                                401, "Unauthorized", "Autenticação necessária."))
                        .addResponses("CsrfInvalido", erro("Token CSRF ausente ou inválido.",
                                403, "Forbidden", "Token CSRF ausente ou inválido."))
                        .addResponses("ImovelNaoEncontrado", erro(
                                "O imóvel não existe ou é de outro proprietário (indistinguíveis).",
                                404, "Not Found", "Imóvel não encontrado."))
                        .addResponses("IdInvalido", erro("`{id}` não numérico.",
                                400, "Bad Request", "Identificador de imóvel inválido.")));
    }

    @SuppressWarnings("rawtypes")
    private static Schema problemDetail() {
        return new ObjectSchema()
                .description("RFC 9457. O campo `type` é omitido (equivale a `about:blank`).")
                .required(List.of("status", "title", "detail"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema().description("Mensagem em português."))
                .addProperty("instance", new StringSchema());
    }

    static ApiResponse erro(String descricao, int status, String titulo, String detalhe) {
        Map<String, Object> exemplo = new LinkedHashMap<>();
        exemplo.put("title", titulo);
        exemplo.put("status", status);
        exemplo.put("detail", detalhe);
        return new ApiResponse()
                .description(descricao)
                .content(new Content().addMediaType(PROBLEM_JSON, new MediaType()
                        .schema(new Schema<>().$ref(PROBLEM_DETAIL))
                        .example(exemplo)));
    }
}
