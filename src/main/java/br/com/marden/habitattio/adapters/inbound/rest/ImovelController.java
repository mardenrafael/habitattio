package br.com.marden.habitattio.adapters.inbound.rest;

import java.math.BigDecimal;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAutenticado;
import br.com.marden.habitattio.application.usecase.CadastrarImovelUseCase;
import br.com.marden.habitattio.application.usecase.ConsultarImovelUseCase;
import br.com.marden.habitattio.application.usecase.InativarImovelUseCase;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.inbound.CadastrarImovelCommand;
import br.com.marden.habitattio.ports.inbound.ConsultarImovelCommand;
import br.com.marden.habitattio.ports.inbound.InativarImovelCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/imoveis")
@Tag(name = "Imóveis", description = "Imóveis do proprietário autenticado.")
public class ImovelController {

    private final CadastrarImovelUseCase cadastrarImovelUseCase;
    private final ConsultarImovelUseCase consultarImovelUseCase;
    private final InativarImovelUseCase inativarImovelUseCase;

    public ImovelController(CadastrarImovelUseCase cadastrarImovelUseCase,
            ConsultarImovelUseCase consultarImovelUseCase,
            InativarImovelUseCase inativarImovelUseCase) {
        this.cadastrarImovelUseCase = cadastrarImovelUseCase;
        this.consultarImovelUseCase = consultarImovelUseCase;
        this.inativarImovelUseCase = inativarImovelUseCase;
    }

    @PostMapping
    @Operation(operationId = "cadastrarImovel", summary = "Cadastrar imóvel",
            description = """
                    O imóvel pertence ao proprietário da sessão; qualquer campo informando o dono é ignorado.
                    O `identificador` é único por proprietário, sem distinguir maiúsculas de minúsculas.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CadastrarImovelRequest.class),
                            examples = @ExampleObject(name = "cadastro", value = """
                                    {"identificador": "Apto 101", "endereco": "Rua A, 10", "valorSugerido": 1500.00}"""))))
    @ApiResponse(responseCode = "201", description = "Imóvel criado.",
            headers = @Header(name = "Location", description = "`/imoveis/{id}` do imóvel criado.",
                    schema = @Schema(type = "string", example = "/imoveis/7")),
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ImovelResponse.class),
                    examples = @ExampleObject(name = "criado", value = """
                            {"id": 7, "identificador": "Apto 101", "endereco": "Rua A, 10", "valorSugerido": 1500.00, "ativo": true}""")))
    @ApiResponse(responseCode = "400", description = "Identificador, endereço ou valor inválidos, ou corpo malformado.",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/ProblemDetail"),
                    examples = @ExampleObject(name = "valorZero", value = """
                            {"title": "Bad Request", "status": 400, "detail": "O valor sugerido deve ser maior que zero.", "instance": "/imoveis"}""")))
    @ApiResponse(responseCode = "401", ref = "NaoAutenticado")
    @ApiResponse(responseCode = "403", ref = "CsrfInvalido")
    @ApiResponse(responseCode = "409",
            description = "O proprietário já tem um imóvel com esse identificador (a mensagem repete o valor enviado).",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/ProblemDetail"),
                    examples = @ExampleObject(name = "identificadorRepetido", value = """
                            {"title": "Conflict", "status": 409, "detail": "Você já tem um imóvel com o identificador 'Apto 101'.", "instance": "/imoveis"}""")))
    public ResponseEntity<ImovelResponse> cadastrar(@AuthenticationPrincipal ProprietarioAutenticado proprietario, @RequestBody CadastrarImovelRequest request) {
        CadastrarImovelCommand command = new CadastrarImovelCommand(
                proprietario.id(), request.identificador(), request.endereco(), request.valorSugerido());
        Imovel imovelSalvo = cadastrarImovelUseCase.executar(command);

        return ResponseEntity.created(URI.create("/imoveis/" + imovelSalvo.getId()))
                .body(paraResponse(imovelSalvo));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "consultarImovel", summary = "Consultar imóvel",
            security = @SecurityRequirement(name = "sessao"))
    @ApiResponse(responseCode = "200", description = "O imóvel é do proprietário da sessão.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImovelResponse.class)))
    @ApiResponse(responseCode = "400", ref = "IdInvalido")
    @ApiResponse(responseCode = "401", ref = "NaoAutenticado")
    @ApiResponse(responseCode = "404", ref = "ImovelNaoEncontrado")
    public ResponseEntity<ImovelResponse> consultar(@AuthenticationPrincipal ProprietarioAutenticado proprietario,
            @Parameter(example = "7") @PathVariable Long id) {
        Imovel imovel = consultarImovelUseCase.executar(new ConsultarImovelCommand(proprietario.id(), id));

        return ResponseEntity.ok(paraResponse(imovel));
    }

    @PostMapping("/{id}/inativacao")
    @Operation(operationId = "inativarImovel", summary = "Inativar imóvel",
            description = "Sem corpo. Idempotente — inativar um imóvel já inativo responde 200.")
    @ApiResponse(responseCode = "200", description = "Imóvel inativado.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ImovelResponse.class),
                    examples = @ExampleObject(name = "inativado", value = """
                            {"id": 7, "identificador": "Apto 101", "endereco": "Rua A, 10", "valorSugerido": 1500.0000, "ativo": false}""")))
    @ApiResponse(responseCode = "400", ref = "IdInvalido")
    @ApiResponse(responseCode = "401", ref = "NaoAutenticado")
    @ApiResponse(responseCode = "403", ref = "CsrfInvalido")
    @ApiResponse(responseCode = "404", ref = "ImovelNaoEncontrado")
    public ResponseEntity<ImovelResponse> inativar(@AuthenticationPrincipal ProprietarioAutenticado proprietario,
            @Parameter(example = "7") @PathVariable Long id) {
        Imovel imovel = inativarImovelUseCase.executar(new InativarImovelCommand(proprietario.id(), id));

        return ResponseEntity.ok(paraResponse(imovel));
    }

    private static ImovelResponse paraResponse(Imovel imovel) {
        return new ImovelResponse(imovel.getId(), imovel.getIdentificador(), imovel.getEndereco(),
                imovel.getValorSugerido(), imovel.isAtivo());
    }

    public record CadastrarImovelRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 50,
                    description = "Único por proprietário, sem distinguir maiúsculas de minúsculas.") String identificador,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 255) String endereco,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0",
                    description = "Valor monetário, estritamente positivo.") BigDecimal valorSugerido) {}
    public record ImovelResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String identificador,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String endereco,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = """
                    No cadastro (201) volta com a escala enviada; na consulta e na inativação, com 4 casas
                    decimais (por exemplo 1500.0000).""") BigDecimal valorSugerido,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativo) {}
}
