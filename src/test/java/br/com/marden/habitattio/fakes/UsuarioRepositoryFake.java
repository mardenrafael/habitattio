package br.com.marden.habitattio.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.com.marden.habitattio.domain.exception.EmailJaCadastradoException;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.ports.outbound.UsuarioRepositoryPort;

public class UsuarioRepositoryFake implements UsuarioRepositoryPort {

    private final Map<Long, Usuario> usuarios = new HashMap<>();
    private long proximoId = 1L;
    private boolean corridaNoProximoSalvar = false;

    public void simularCorridaNoProximoSalvar() {
        this.corridaNoProximoSalvar = true;
    }

    public void limpar() {
        usuarios.clear();
        proximoId = 1L;
        corridaNoProximoSalvar = false;
    }

    public int quantidade() {
        return usuarios.size();
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        if (corridaNoProximoSalvar) {
            corridaNoProximoSalvar = false;
            throw new EmailJaCadastradoException();
        }
        Long id = usuario.getId() != null ? usuario.getId() : proximoId++;
        Usuario salvo = new Usuario(id, usuario.getNome(), usuario.getEmail(), usuario.getSenhaHash());
        usuarios.put(id, salvo);
        return salvo;
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String emailNormalizado) {
        return usuarios.values().stream()
                .filter(u -> u.getEmail().equals(emailNormalizado))
                .findFirst();
    }

    @Override
    public boolean existePorEmail(String emailNormalizado) {
        return buscarPorEmail(emailNormalizado).isPresent();
    }
}
