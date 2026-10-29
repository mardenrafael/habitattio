package br.com.marden.habitattio.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InquilinoTest {

    @Test
    void deveCriarInquilinoComDadosValidos() {
        Inquilino inquilino = new Inquilino(1L, "João Souza", "12345678900", "11999998888");

        assertEquals("João Souza", inquilino.getNome());
        assertEquals("12345678900", inquilino.getCpf());
        assertEquals("11999998888", inquilino.getTelefone());
    }

    @Test
    void deveRejeitarNomeEmBranco() {
        assertThrows(IllegalArgumentException.class,
                () -> new Inquilino(1L, "  ", "12345678900", "11999998888"));
    }

    @Test
    void deveRejeitarNomeNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Inquilino(1L, null, "12345678900", "11999998888"));
    }

    @Test
    void deveRejeitarCpfEmBranco() {
        assertThrows(IllegalArgumentException.class,
                () -> new Inquilino(1L, "João Souza", "  ", "11999998888"));
    }

    @Test
    void deveRejeitarCpfComQuantidadeDeDigitosInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> new Inquilino(1L, "João Souza", "123", "11999998888"));
    }
}
