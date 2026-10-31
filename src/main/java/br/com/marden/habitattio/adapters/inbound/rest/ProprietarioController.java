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

@RestController
@RequestMapping("/proprietarios")
public class ProprietarioController {

    private final CadastrarProprietarioUseCase cadastrarProprietarioUseCase;

    public ProprietarioController(CadastrarProprietarioUseCase cadastrarProprietarioUseCase) {
        this.cadastrarProprietarioUseCase = cadastrarProprietarioUseCase;
    }

    @PostMapping
    public ResponseEntity<ProprietarioResponse> cadastrar(@RequestBody CadastrarProprietarioRequest request) {
        CadastrarProprietarioCommand command = new CadastrarProprietarioCommand(
                request.nome(), request.email(), request.senha());
        Usuario salvo = cadastrarProprietarioUseCase.executar(command);

        ProprietarioResponse response = new ProprietarioResponse(salvo.getId(), salvo.getNome(), salvo.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record CadastrarProprietarioRequest(String nome, String email, String senha) {}
    public record ProprietarioResponse(Long id, String nome, String email) {}
}
