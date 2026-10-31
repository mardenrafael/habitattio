package br.com.marden.habitattio.application.usecase;

import java.util.Optional;

import br.com.marden.habitattio.domain.exception.CredenciaisInvalidasException;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.inbound.AutenticarProprietarioCommand;
import br.com.marden.habitattio.ports.outbound.CodificadorSenhaPort;
import br.com.marden.habitattio.ports.outbound.UsuarioRepositoryPort;

public class AutenticarProprietarioUseCase {

    private final UsuarioRepositoryPort usuarioRepositoryPort;
    private final CodificadorSenhaPort codificadorSenhaPort;
    private final String hashFicticio;

    public AutenticarProprietarioUseCase(UsuarioRepositoryPort usuarioRepositoryPort,
            CodificadorSenhaPort codificadorSenhaPort) {
        this.usuarioRepositoryPort = usuarioRepositoryPort;
        this.codificadorSenhaPort = codificadorSenhaPort;
        this.hashFicticio = codificadorSenhaPort.codificar("senha-ficticia-para-tempo-constante");
    }

    public Usuario executar(AutenticarProprietarioCommand command) {
        String email = Usuario.normalizarEmail(command.email());
        Optional<Usuario> encontrado = usuarioRepositoryPort.buscarPorEmail(email);

        if (encontrado.isEmpty()) {
            codificadorSenhaPort.confere(command.senha(), hashFicticio);
            throw new CredenciaisInvalidasException();
        }

        Usuario usuario = encontrado.get();
        if (!codificadorSenhaPort.confere(command.senha(), usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        return usuario;
    }
}
