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

@RestController
@RequestMapping("/imoveis")
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
    public ResponseEntity<ImovelResponse> cadastrar(@AuthenticationPrincipal ProprietarioAutenticado proprietario,
            @RequestBody CadastrarImovelRequest request) {
        CadastrarImovelCommand command = new CadastrarImovelCommand(
                proprietario.id(), request.identificador(), request.endereco(), request.valorSugerido());
        Imovel imovelSalvo = cadastrarImovelUseCase.executar(command);

        return ResponseEntity.created(URI.create("/imoveis/" + imovelSalvo.getId()))
                .body(paraResponse(imovelSalvo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImovelResponse> consultar(@AuthenticationPrincipal ProprietarioAutenticado proprietario,
            @PathVariable Long id) {
        Imovel imovel = consultarImovelUseCase.executar(new ConsultarImovelCommand(proprietario.id(), id));

        return ResponseEntity.ok(paraResponse(imovel));
    }

    @PostMapping("/{id}/inativacao")
    public ResponseEntity<ImovelResponse> inativar(@AuthenticationPrincipal ProprietarioAutenticado proprietario,
            @PathVariable Long id) {
        Imovel imovel = inativarImovelUseCase.executar(new InativarImovelCommand(proprietario.id(), id));

        return ResponseEntity.ok(paraResponse(imovel));
    }

    private static ImovelResponse paraResponse(Imovel imovel) {
        return new ImovelResponse(imovel.getId(), imovel.getIdentificador(), imovel.getEndereco(),
                imovel.getValorSugerido(), imovel.isAtivo());
    }

    public record CadastrarImovelRequest(String identificador, String endereco, BigDecimal valorSugerido) {}
    public record ImovelResponse(Long id, String identificador, String endereco, BigDecimal valorSugerido, boolean ativo) {}
}
