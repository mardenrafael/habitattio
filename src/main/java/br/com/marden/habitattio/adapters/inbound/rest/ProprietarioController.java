package br.com.marden.habitattio.adapters.inbound.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.marden.habitattio.application.usecase.CadastrarProprietarioUseCase;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.inbound.CadastrarProprietarioCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/proprietarios")
@Tag(name = "Proprietários", description = "Cadastro de quem é dono dos imóveis (públicas).")
public class ProprietarioController {

    private final CadastrarProprietarioUseCase cadastrarProprietarioUseCase;

    public ProprietarioController(CadastrarProprietarioUseCase cadastrarProprietarioUseCase) {
        this.cadastrarProprietarioUseCase = cadastrarProprietarioUseCase;
    }

    @PostMapping
    @Operation(operationId = "cadastrarProprietario", summary = "Cadastrar proprietário",
            description = """
                    Pública, mas exige CSRF. O e-mail é normalizado (minúsculas, sem espaços nas pontas).
                    O cadastro **não** autentica: entre em seguida por `POST /sessao`.""",
            security = @SecurityRequirement(name = "csrf"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CadastrarProprietarioRequest.class),
                            examples = @ExampleObject(name = "cadastro", value = """
                                    {"nome": "Ana Souza", "email": "ana@exemplo.com", "senha": "segredo123"}"""))))
    @ApiResponse(responseCode = "201", description = "Proprietário criado (sem `Location`).",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ProprietarioResponse.class),
                    examples = @ExampleObject(name = "criado", value = """
                            {"id": 1, "nome": "Ana Souza", "email": "ana@exemplo.com"}""")))
    @ApiResponse(responseCode = "400", description = "Nome, e-mail ou senha inválidos, ou corpo malformado.",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/ProblemDetail"),
                    examples = @ExampleObject(name = "senhaCurta", value = """
                            {"title": "Bad Request", "status": 400, "detail": "A senha deve ter no mínimo 8 caracteres.", "instance": "/proprietarios"}""")))
    @ApiResponse(responseCode = "403", ref = "CsrfInvalido")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado (comparado normalizado).",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(ref = "#/components/schemas/ProblemDetail"),
                    examples = @ExampleObject(name = "emailRepetido", value = """
                            {"title": "Conflict", "status": 409, "detail": "Já existe um proprietário com este e-mail.", "instance": "/proprietarios"}""")))
    public ResponseEntity<ProprietarioResponse> cadastrar(@RequestBody CadastrarProprietarioRequest request) {
        CadastrarProprietarioCommand command = new CadastrarProprietarioCommand(
                request.nome(), request.email(), request.senha());
        Usuario salvo = cadastrarProprietarioUseCase.executar(command);

        ProprietarioResponse response = new ProprietarioResponse(salvo.getId(), salvo.getNome(), salvo.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record CadastrarProprietarioRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100) String nome,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email", maxLength = 254,
                    description = "Normalizado para minúsculas e sem espaços nas pontas.") String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "password", minLength = 8,
                    description = "No mínimo 8 caracteres e no máximo 72 bytes em UTF-8.") String senha) {}
    public record ProprietarioResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nome,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email") String email) {}
}
