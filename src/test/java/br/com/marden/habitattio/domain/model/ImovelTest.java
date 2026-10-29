package br.com.marden.habitattio.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ImovelTest {

    @Test
    void deveCriarImovelAtivoComDadosValidos() {
        Imovel imovel = new Imovel(1L, "Rua das Flores, 123", new BigDecimal("1500.00"));

        assertEquals("Rua das Flores, 123", imovel.getEndereco());
        assertEquals(new BigDecimal("1500.00"), imovel.getValorSugerido());
        assertTrue(imovel.isAtivo());
    }

    @Test
    void deveRejeitarEnderecoNulo() {
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, null, new BigDecimal("1500.00")));

        assertEquals("O endereço do imóvel não pode ser vazio.", excecao.getMessage());
    }

    @Test
    void deveRejeitarEnderecoEmBranco() {
        assertThrows(IllegalArgumentException.class, () -> new Imovel(1L, "   ", new BigDecimal("1500.00")));
    }

    @Test
    void deveRejeitarValorSugeridoNulo() {
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, "Rua das Flores, 123", null));

        assertEquals("O valor sugerido deve ser maior que zero.", excecao.getMessage());
    }

    @Test
    void deveRejeitarValorSugeridoZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, "Rua das Flores, 123", BigDecimal.ZERO));
    }

    @Test
    void deveRejeitarValorSugeridoNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, "Rua das Flores, 123", new BigDecimal("-10.00")));
    }

    @Test
    void deveInativarImovelAtivo() {
        Imovel imovel = new Imovel(1L, "Rua das Flores, 123", new BigDecimal("1500.00"));

        imovel.inativar();

        assertFalse(imovel.isAtivo());
    }
}
