package br.com.marden.habitattio.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ContratoTest {

    @Test
    void deveCriarContratoAtivoComDadosValidos() {
        Contrato contrato = new Contrato(1L, 10L, 20L, new BigDecimal("1500.00"), 5, LocalDate.of(2026, 1, 1));

        assertEquals(10L, contrato.getImovelId());
        assertEquals(20L, contrato.getInquilinoId());
        assertEquals(5, contrato.getDiaVencimento());
        assertTrue(contrato.isAtivo());
    }

    @Test
    void deveRejeitarDiaDeVencimentoMenorQueUm() {
        assertThrows(IllegalArgumentException.class,
                () -> new Contrato(1L, 10L, 20L, new BigDecimal("1500.00"), 0, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void deveRejeitarDiaDeVencimentoMaiorQueTrintaEUm() {
        assertThrows(IllegalArgumentException.class,
                () -> new Contrato(1L, 10L, 20L, new BigDecimal("1500.00"), 32, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void deveEncerrarContratoAtivo() {
        Contrato contrato = new Contrato(1L, 10L, 20L, new BigDecimal("1500.00"), 5, LocalDate.of(2026, 1, 1));

        contrato.encerrar();

        assertFalse(contrato.isAtivo());
    }
}
