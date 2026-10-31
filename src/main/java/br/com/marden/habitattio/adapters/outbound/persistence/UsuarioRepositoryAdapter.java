package br.com.marden.habitattio.adapters.outbound.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;

import br.com.marden.habitattio.adapters.outbound.persistence.entity.UsuarioJpaEntity;
import br.com.marden.habitattio.domain.exception.EmailJaCadastradoException;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.outbound.UsuarioRepositoryPort;

public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {

    private static final String CONSTRAINT_EMAIL = "uq_usuarios_email";

    private final SpringDataUsuarioRepository repository;

    public UsuarioRepositoryAdapter(SpringDataUsuarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        UsuarioJpaEntity entity = new UsuarioJpaEntity(
            usuario.getId(),
            usuario.getNome(),
            usuario.getEmail(),
            usuario.getSenhaHash()
        );

        try {
            return paraDominio(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            String mensagem = e.getMostSpecificCause().getMessage();
            if (mensagem != null && mensagem.toLowerCase().contains(CONSTRAINT_EMAIL)) {
                throw new EmailJaCadastradoException();
            }
            throw e;
        }
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String emailNormalizado) {
        return repository.findByEmail(emailNormalizado).map(this::paraDominio);
    }

    @Override
    public boolean existePorEmail(String emailNormalizado) {
        return repository.existsByEmail(emailNormalizado);
    }

    private Usuario paraDominio(UsuarioJpaEntity entity) {
        return new Usuario(entity.getId(), entity.getNome(), entity.getEmail(), entity.getSenhaHash());
    }
}
