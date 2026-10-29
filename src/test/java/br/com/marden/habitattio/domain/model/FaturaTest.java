package br.com.marden.habitattio.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import br.com.marden.habitattio.domain.model.Fatura.StatusFatura;

class FaturaTest {

    private Fatura novaFatura(LocalDate dataVencimento) {
        return new Fatura(1L, 10L, "2026-01", new BigDecimal("1500.00"), dataVencimento);
    }

    @Test
    void deveNascerComoPendente() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));

        assertEquals(StatusFatura.PENDENTE, fatura.getStatus());
    }

    @Test
    void deveMarcarComoPagaQuandoValorPagoForIgualAoValorDaFatura() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));

        fatura.pagar(new BigDecimal("1500.00"), LocalDate.of(2026, 1, 5));

        assertEquals(StatusFatura.PAGA, fatura.getStatus());
    }

    @Test
    void deveRejeitarPagamentoComValorZero() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));

        assertThrows(IllegalArgumentException.class,
                () -> fatura.pagar(BigDecimal.ZERO, LocalDate.of(2026, 1, 5)));
    }

    @Test
    void deveRejeitarPagamentoParcial() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class,
                () -> fatura.pagar(new BigDecimal("1000.00"), LocalDate.of(2026, 1, 5)));

        assertEquals("Pagamento parcial não é permitido. O valor pago deve ser igual ao valor da fatura.",
                excecao.getMessage());
        assertEquals(StatusFatura.PENDENTE, fatura.getStatus());
    }

    @Test
    void deveRejeitarPagamentoDeFaturaJaPaga() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));
        fatura.pagar(new BigDecimal("1500.00"), LocalDate.of(2026, 1, 5));

        assertThrows(IllegalStateException.class,
                () -> fatura.pagar(new BigDecimal("1500.00"), LocalDate.of(2026, 1, 6)));
    }

    @Test
    void deveRegistrarValorPagoEDataDePagamentoAoPagar() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));
        LocalDate dataPagamento = LocalDate.of(2026, 1, 5);

        fatura.pagar(new BigDecimal("1500.00"), dataPagamento);

        assertEquals(new BigDecimal("1500.00"), fatura.getValorPago());
        assertEquals(dataPagamento, fatura.getDataPagamento());
    }

    @Test
    void deveMarcarComoAtrasadaQuandoVencimentoPassouENaoFoiPaga() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));

        fatura.verificarAtraso(LocalDate.of(2026, 1, 11));

        assertEquals(StatusFatura.ATRASADA, fatura.getStatus());
    }

    @Test
    void naoDeveMarcarComoAtrasadaNoProprioDiaDoVencimento() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));

        fatura.verificarAtraso(LocalDate.of(2026, 1, 10));

        assertEquals(StatusFatura.PENDENTE, fatura.getStatus());
    }

    @Test
    void naoDeveMarcarComoAtrasadaAntesDoVencimento() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));

        fatura.verificarAtraso(LocalDate.of(2026, 1, 9));

        assertEquals(StatusFatura.PENDENTE, fatura.getStatus());
    }

    @Test
    void naoDeveVoltarAPendenteAoVerificarAtrasoDeFaturaJaPaga() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));
        fatura.pagar(new BigDecimal("1500.00"), LocalDate.of(2026, 1, 5));

        fatura.verificarAtraso(LocalDate.of(2026, 2, 1));

        assertEquals(StatusFatura.PAGA, fatura.getStatus());
    }

    @Test
    void devePermitirPagarFaturaQueEstavaAtrasada() {
        Fatura fatura = novaFatura(LocalDate.of(2026, 1, 10));
        fatura.verificarAtraso(LocalDate.of(2026, 1, 20));

        fatura.pagar(new BigDecimal("1500.00"), LocalDate.of(2026, 1, 21));

        assertEquals(StatusFatura.PAGA, fatura.getStatus());
    }
}
