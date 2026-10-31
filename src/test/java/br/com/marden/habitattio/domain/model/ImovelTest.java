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
        Imovel imovel = new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", new BigDecimal("1500.00"));

        assertEquals("Rua das Flores, 123", imovel.getEndereco());
        assertEquals(new BigDecimal("1500.00"), imovel.getValorSugerido());
        assertTrue(imovel.isAtivo());
    }

    @Test
    void deveRejeitarEnderecoNulo() {
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, 1L, "Apto 101", null, new BigDecimal("1500.00")));

        assertEquals("O endereço do imóvel não pode ser vazio.", excecao.getMessage());
    }

    @Test
    void deveRejeitarEnderecoEmBranco() {
        assertThrows(IllegalArgumentException.class, () -> new Imovel(1L, 1L, "Apto 101", "   ", new BigDecimal("1500.00")));
    }

    @Test
    void deveRejeitarValorSugeridoNulo() {
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", null));

        assertEquals("O valor sugerido deve ser maior que zero.", excecao.getMessage());
    }

    @Test
    void deveRejeitarValorSugeridoZero() {
        assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", BigDecimal.ZERO));
    }

    @Test
    void deveRejeitarValorSugeridoNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", new BigDecimal("-10.00")));
    }

    @Test
    void deveInativarImovelAtivo() {
        Imovel imovel = new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", new BigDecimal("1500.00"));

        imovel.inativar();

        assertFalse(imovel.isAtivo());
    }

    @Test
    void deveRejeitarEnderecoComMaisDe255Caracteres() {
        String endereco = "a".repeat(256);

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, 1L, "Apto 101", endereco, new BigDecimal("1500.00")));

        assertEquals("O endereço do imóvel deve ter no máximo 255 caracteres.", excecao.getMessage());
    }

    @Test
    void deveAceitarEnderecoCom255Caracteres() {
        String endereco = "a".repeat(255);

        Imovel imovel = new Imovel(1L, 1L, "Apto 101", endereco, new BigDecimal("1500.00"));

        assertEquals(endereco, imovel.getEndereco());
    }

    @Test
    void deveContarCaracteresENaoUnidadesUtf16NoLimiteDoEndereco() {
        String endereco = "a".repeat(254) + "🏠";

        Imovel imovel = new Imovel(1L, 1L, "Apto 101", endereco, new BigDecimal("1500.00"));

        assertEquals(256, endereco.length());
        assertEquals(endereco, imovel.getEndereco());
    }

    @Test
    void deveCriarImovelInativoQuandoInformado() {
        Imovel imovel = new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", new BigDecimal("1500.00"), false);

        assertEquals(1L, imovel.getId());
        assertEquals("Rua das Flores, 123", imovel.getEndereco());
        assertEquals(new BigDecimal("1500.00"), imovel.getValorSugerido());
        assertFalse(imovel.isAtivo());
    }

    @Test
    void deveCriarImovelAtivoQuandoInformado() {
        Imovel imovel = new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", new BigDecimal("1500.00"), true);

        assertTrue(imovel.isAtivo());
    }

    @Test
    void deveRejeitarEnderecoVazioNoConstrutorCompleto() {
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, 1L, "Apto 101", "   ", new BigDecimal("1500.00"), false));

        assertEquals("O endereço do imóvel não pode ser vazio.", excecao.getMessage());
    }

    @Test
    void deveRejeitarValorSugeridoInvalidoNoConstrutorCompleto() {
        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> new Imovel(1L, 1L, "Apto 101", "Rua das Flores, 123", BigDecimal.ZERO, false));

        assertEquals("O valor sugerido deve ser maior que zero.", excecao.getMessage());
    }
}
