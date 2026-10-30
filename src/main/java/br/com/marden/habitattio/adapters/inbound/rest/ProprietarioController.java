package br.com.marden.habitattio.adapters.inbound.rest;

import org.springframework.web.bind.annotation.RestController;

import br.com.marden.habitattio.application.usecase.CadastrarProprietarioUseCase;

@RestController
public class ProprietarioController {

    private final CadastrarProprietarioUseCase cadastrarProprietarioUseCase;

    public ProprietarioController(CadastrarProprietarioUseCase cadastrarProprietarioUseCase) {
        this.cadastrarProprietarioUseCase = cadastrarProprietarioUseCase;
    }
}
