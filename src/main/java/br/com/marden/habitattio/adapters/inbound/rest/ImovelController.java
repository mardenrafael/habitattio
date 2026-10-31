package br.com.marden.habitattio.adapters.inbound.rest;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.marden.habitattio.application.usecase.CadastrarImovelUseCase;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.inbound.CadastrarImovelCommand;

@RestController
@RequestMapping("/imoveis")
public class ImovelController {

    private final CadastrarImovelUseCase cadastrarImovelUseCase;

    public ImovelController(CadastrarImovelUseCase cadastrarImovelUseCase) {
        this.cadastrarImovelUseCase = cadastrarImovelUseCase;
    }

    @PostMapping
    public ResponseEntity<ImovelResponse> cadastrar(@RequestBody CadastrarImovelRequest request) {
        CadastrarImovelCommand command = new CadastrarImovelCommand(null, null, request.endereco(), request.valorSugerido());
        Imovel imovelSalvo = cadastrarImovelUseCase.executar(command);

        ImovelResponse response = new ImovelResponse(imovelSalvo.getId(), imovelSalvo.getEndereco(), imovelSalvo.getValorSugerido());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public record CadastrarImovelRequest(String endereco, BigDecimal valorSugerido) {}
    public record ImovelResponse(Long id, String endereco, BigDecimal valorSugerido) {}
}
