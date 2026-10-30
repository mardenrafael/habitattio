package br.com.marden.habitattio.application.usecase;

import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.inbound.CadastrarProprietarioCommand;
import br.com.marden.habitattio.ports.outbound.CodificadorSenhaPort;
import br.com.marden.habitattio.ports.outbound.UsuarioRepositoryPort;

public class CadastrarProprietarioUseCase {

    private final UsuarioRepositoryPort usuarioRepositoryPort;
    private final CodificadorSenhaPort codificadorSenhaPort;

    public CadastrarProprietarioUseCase(UsuarioRepositoryPort usuarioRepositoryPort,
            CodificadorSenhaPort codificadorSenhaPort) {
        this.usuarioRepositoryPort = usuarioRepositoryPort;
        this.codificadorSenhaPort = codificadorSenhaPort;
    }

    public Usuario executar(CadastrarProprietarioCommand command) {
        throw new UnsupportedOperationException();
    }
}
