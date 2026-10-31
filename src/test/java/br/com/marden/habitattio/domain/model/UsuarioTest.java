package br.com.marden.habitattio.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void deveNormalizarEmailRemovendoEspacosEMaiusculas() {
        Usuario usuario = new Usuario(1L, "Ana", " Ana@Exemplo.COM ", "hash-abc123");

        assertEquals("ana@exemplo.com", usuario.getEmail());
    }

    @Test
    void deveRejeitarNomeComMaisDeCemCaracteres() {
        String nomeLongo = "a".repeat(101);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Usuario(1L, nomeLongo, "maria@exemplo.com", "hash-abc123"));

        assertEquals("O nome do usuário deve ter no máximo 100 caracteres.", erro.getMessage());
    }

    @Test
    void deveAceitarNomeComCemCaracteresAcentuados() {
        String nome = "é".repeat(100);

        Usuario usuario = new Usuario(1L, nome, "maria@exemplo.com", "hash-abc123");

        assertEquals(nome, usuario.getNome());
    }

    @Test
    void deveRejeitarEmailComMaisDe254Caracteres() {
        String emailLongo = "a".repeat(243) + "@exemplo.com";

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Usuario(1L, "Maria Silva", emailLongo, "hash-abc123"));

        assertEquals("O e-mail do usuário deve ter no máximo 254 caracteres.", erro.getMessage());
    }

    @Test
    void normalizarEmailDeveDevolverNuloParaNulo() {
        assertNull(Usuario.normalizarEmail(null));
    }
}
