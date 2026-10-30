package br.com.marden.habitattio.adapters.outbound.persistence;

import java.util.Optional;

import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.outbound.UsuarioRepositoryPort;

public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {

    private final SpringDataUsuarioRepository repository;

    public UsuarioRepositoryAdapter(SpringDataUsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String emailNormalizado) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean existePorEmail(String emailNormalizado) {
        throw new UnsupportedOperationException();
    }
}
