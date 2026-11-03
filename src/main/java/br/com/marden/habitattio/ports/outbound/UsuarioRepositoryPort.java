package br.com.marden.habitattio.ports.outbound;

import java.util.Optional;

import br.com.marden.habitattio.domain.model.Usuario;

public interface UsuarioRepositoryPort {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorEmail(String emailNormalizado);

    boolean existePorEmail(String emailNormalizado);
}
