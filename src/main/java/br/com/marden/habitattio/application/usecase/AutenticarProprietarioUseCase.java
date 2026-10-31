package br.com.marden.habitattio.application.usecase;

import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.inbound.AutenticarProprietarioCommand;
import br.com.marden.habitattio.ports.outbound.CodificadorSenhaPort;
import br.com.marden.habitattio.ports.outbound.UsuarioRepositoryPort;

public class AutenticarProprietarioUseCase {

    private final UsuarioRepositoryPort usuarioRepositoryPort;
    private final CodificadorSenhaPort codificadorSenhaPort;

    public AutenticarProprietarioUseCase(UsuarioRepositoryPort usuarioRepositoryPort,
            CodificadorSenhaPort codificadorSenhaPort) {
        this.usuarioRepositoryPort = usuarioRepositoryPort;
        this.codificadorSenhaPort = codificadorSenhaPort;
    }

    public Usuario executar(AutenticarProprietarioCommand command) {
        throw new UnsupportedOperationException("Não implementado");
    }
}
