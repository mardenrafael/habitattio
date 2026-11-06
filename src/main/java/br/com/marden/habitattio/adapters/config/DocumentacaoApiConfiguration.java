package br.com.marden.habitattio.adapters.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Monta a descrição OpenAPI da API (specs/004-auto-docs-api, research R2/R3).
 *
 * <p>As operações dos controllers saem das anotações neles; aqui ficam o que é global (guia,
 * servidor, esquemas de segurança, respostas e esquemas compartilhados) e as operações que não
 * nascem de controllers: login e logout (filtros do Spring Security) e saúde (Actuator). A
 * exigência padrão é sessão E CSRF; as operações que pedem só um dos dois, ou nenhum, sobrescrevem.
 */
@Configuration
public class DocumentacaoApiConfiguration {

    static final String PROBLEM_JSON = "application/problem+json";
    static final String PROBLEM_DETAIL = "#/components/schemas/ProblemDetail";

    private static final String ACTUATOR_JSON = "application/vnd.spring-boot.actuator.v3+json";
    private static final String NAO_AUTENTICADO = "#/components/responses/NaoAutenticado";
    private static final String CSRF_INVALIDO = "#/components/responses/CsrfInvalido";
    private static final String TAG_SESSAO = "Sessão";
    private static final String TAG_SAUDE = "Saúde";

    private static final String GUIA = """
            Gestão de pagamentos de aluguéis. Este arquivo é **gerado** pela suíte de testes
            (`./mvnw test`) a partir do código: não edite à mão. Ele é a referência do contrato HTTP.

            ## Como testar num API client

            1. **Obter o token CSRF**: `GET /sessao` (responde 401 sem sessão, mas já devolve o cookie
               `XSRF-TOKEN`).
            2. Em todo `POST`/`DELETE`, envie o cabeçalho `X-XSRF-TOKEN` com o valor do cookie
               `XSRF-TOKEN` (e o próprio cookie). Sem ele: **403**.
            3. `POST /proprietarios` cadastra; `POST /sessao` (formulário) entra e devolve um novo
               `JSESSIONID` **e um novo `XSRF-TOKEN`** — atualize o cabeçalho depois do login.
            4. As rotas protegidas usam o cookie `JSESSIONID`. A sessão expira após 30 min de inatividade.

            O client precisa guardar e reenviar cookies (cookie jar ligado). Em `prod` o cookie de sessão
            é `Secure`: só trafega por HTTPS.

            Erros são `application/problem+json` (RFC 9457) com `detail` em português. Imóvel de outro
            proprietário responde como inexistente (404).
            """;

    @Bean
    OpenAPI descricaoApi() {
        return new OpenAPI()
                .info(new Info().title("Habitattio API").version("0.0.1").description(GUIA))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local (modo rápido, H2)")))
                .addSecurityItem(new SecurityRequirement().addList("sessao").addList("csrf"))
                .addTagsItem(new Tag().name(TAG_SAUDE).description("Endpoint operacional do Actuator."))
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

    @Bean
    OpenApiCustomizer operacoesForaDosControllers() {
        return descricao -> {
            // Aqui, e não no bean OpenAPI: o springdoc descarta esquemas sem referência antes dos customizers.
            descricao.getComponents().addSchemas("EntrarForm", entrarForm()).addSchemas("Saude", saude());
            descricao.getPaths().get("/sessao").post(entrar()).delete(sair());
            descricao.path("/actuator/health", new PathItem().get(verificarSaude()));
        };
    }

    private static Operation entrar() {
        Map<String, Object> exemplo = new LinkedHashMap<>();
        exemplo.put("email", "ana@exemplo.com");
        exemplo.put("senha", "segredo123");
        return new Operation()
                .addTagsItem(TAG_SESSAO)
                .operationId("entrar")
                .summary("Entrar (login)")
                .description("""
                        Formulário, não JSON. O e-mail aceita qualquer caixa e espaços nas pontas. Em caso de
                        sucesso, o id da sessão é trocado e um novo `XSRF-TOKEN` é emitido.""")
                .addSecurityItem(new SecurityRequirement().addList("csrf"))
                .requestBody(new RequestBody().required(true).content(new Content()
                        .addMediaType("application/x-www-form-urlencoded", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/EntrarForm"))
                                .example(exemplo))))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse()
                                .description("Autenticado. Novos cookies `JSESSIONID` e `XSRF-TOKEN`.")
                                .addHeaderObject("Set-Cookie", new Header()
                                        .description("`JSESSIONID=...; Path=/; HttpOnly; SameSite=Lax` e "
                                                + "`XSRF-TOKEN=...; Path=/`.")
                                        .schema(new StringSchema())))
                        .addApiResponse("401", erro("E-mail inexistente ou senha errada (indistinguíveis).",
                                401, "Unauthorized", "E-mail ou senha inválidos."))
                        .addApiResponse("403", new ApiResponse().$ref(CSRF_INVALIDO)));
    }

    private static Operation sair() {
        return new Operation()
                .addTagsItem(TAG_SESSAO)
                .operationId("sair")
                .summary("Sair (logout)")
                .description("Invalida a sessão; requisições seguintes com o mesmo cookie recebem 401.")
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("Sessão invalidada."))
                        .addApiResponse("401", new ApiResponse().$ref(NAO_AUTENTICADO))
                        .addApiResponse("403", new ApiResponse().$ref(CSRF_INVALIDO)));
    }

    private static Operation verificarSaude() {
        return new Operation()
                .addTagsItem(TAG_SAUDE)
                .operationId("verificarSaude")
                .summary("Saúde da aplicação")
                .description("Pública. Só o estado geral; o Redis não entra no cálculo.")
                .security(List.of())
                .responses(new ApiResponses()
                        .addApiResponse("200", estadoDeSaude("Aplicação e banco OK.", "UP"))
                        .addApiResponse("503", estadoDeSaude("Banco inacessível ou fora de serviço.", "DOWN")));
    }

    private static ApiResponse estadoDeSaude(String descricao, String status) {
        return new ApiResponse()
                .description(descricao)
                .content(new Content().addMediaType(ACTUATOR_JSON, new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/Saude"))
                        .example(Map.of("status", status))));
    }

    @SuppressWarnings("rawtypes")
    private static Schema entrarForm() {
        return new ObjectSchema()
                .required(List.of("email", "senha"))
                .addProperty("email", new StringSchema().format("email"))
                .addProperty("senha", new StringSchema().format("password"));
    }

    @SuppressWarnings("rawtypes")
    private static Schema saude() {
        return new ObjectSchema()
                .required(List.of("status"))
                .addProperty("status", new StringSchema()._enum(List.of("UP", "DOWN", "OUT_OF_SERVICE")));
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
