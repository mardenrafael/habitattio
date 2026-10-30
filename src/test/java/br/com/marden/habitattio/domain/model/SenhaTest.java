package br.com.marden.habitattio.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SenhaTest {

    @Test
    void deveAceitarSenhaComOitoCaracteres() {
        Senha senha = new Senha("12345678");

        assertEquals("12345678", senha.getValor());
    }

    @Test
    void deveRejeitarSenhaNula() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Senha(null));

        assertEquals("A senha não pode ser vazia.", erro.getMessage());
    }

    @Test
    void deveRejeitarSenhaVazia() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Senha(""));

        assertEquals("A senha não pode ser vazia.", erro.getMessage());
    }

    @Test
    void deveRejeitarSenhaComMenosDeOitoCaracteres() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Senha("1234567"));

        assertEquals("A senha deve ter no mínimo 8 caracteres.", erro.getMessage());
    }

    @Test
    void deveContarCodePointsAoRejeitarSenhaCurta() {
        String seteEmojis = "😀".repeat(7);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Senha(seteEmojis));

        assertEquals("A senha deve ter no mínimo 8 caracteres.", erro.getMessage());
    }

    @Test
    void deveAceitarSenhaComSetentaEDoisBytes() {
        String senhaDe72Bytes = "a".repeat(72);

        Senha senha = new Senha(senhaDe72Bytes);

        assertEquals(senhaDe72Bytes, senha.getValor());
    }

    @Test
    void deveRejeitarSenhaComMaisDeSetentaEDoisBytesEmUtf8() {
        String senhaDe74Bytes = "é".repeat(37);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> new Senha(senhaDe74Bytes));

        assertEquals("A senha é longa demais: use no máximo 72 bytes (cerca de 72 letras sem acento).",
                erro.getMessage());
    }

    @Test
    void naoDeveExporOValorNoToString() {
        Senha senha = new Senha("segredo-123");

        String texto = senha.toString();

        assertFalse(texto.contains("segredo-123"));
    }
}
