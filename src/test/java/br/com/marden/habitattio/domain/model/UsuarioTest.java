package br.com.marden.habitattio.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void deveCriarUsuarioComDadosValidos() {
        Usuario usuario = new Usuario(1L, "Maria Silva", "maria@exemplo.com", "hash-abc123");

        assertEquals("Maria Silva", usuario.getNome());
        assertEquals("maria@exemplo.com", usuario.getEmail());
        assertEquals("hash-abc123", usuario.getSenhaHash());
    }

    @Test
    void deveRejeitarNomeEmBranco() {
        assertThrows(IllegalArgumentException.class,
                () -> new Usuario(1L, "  ", "maria@exemplo.com", "hash-abc123"));
    }

    @Test
    void deveRejeitarNomeNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Usuario(1L, null, "maria@exemplo.com", "hash-abc123"));
    }

    @Test
    void deveRejeitarEmailEmBranco() {
        assertThrows(IllegalArgumentException.class,
                () -> new Usuario(1L, "Maria Silva", "  ", "hash-abc123"));
    }

    @Test
    void deveRejeitarEmailSemFormatoValido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Usuario(1L, "Maria Silva", "nao-e-um-email", "hash-abc123"));
    }

    @Test
    void deveRejeitarSenhaHashEmBranco() {
        assertThrows(IllegalArgumentException.class,
                () -> new Usuario(1L, "Maria Silva", "maria@exemplo.com", "  "));
    }
}
