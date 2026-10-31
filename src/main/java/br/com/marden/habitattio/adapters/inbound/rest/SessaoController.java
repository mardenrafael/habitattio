package br.com.marden.habitattio.adapters.inbound.rest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAutenticado;

@RestController
@RequestMapping("/sessao")
public class SessaoController {

    @GetMapping
    public SessaoResponse consultar(@AuthenticationPrincipal ProprietarioAutenticado proprietario) {
        return new SessaoResponse(proprietario.id(), proprietario.nome(), proprietario.email());
    }

    public record SessaoResponse(Long id, String nome, String email) {
    }
}
