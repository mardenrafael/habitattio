package br.com.marden.habitattio.application.usecase;

import br.com.marden.habitattio.domain.exception.EmailJaCadastradoException;
import br.com.marden.habitattio.domain.model.Senha;
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
        Senha senha = new Senha(command.senha());
        String emailNormalizado = Usuario.normalizarEmail(command.email());
        if (usuarioRepositoryPort.existePorEmail(emailNormalizado)) {
            throw new EmailJaCadastradoException();
        }
        String hash = codificadorSenhaPort.codificar(senha.getValor());
        Usuario usuario = new Usuario(null, command.nome(), emailNormalizado, hash);
        return usuarioRepositoryPort.salvar(usuario);
    }
}
