package br.com.marden.habitattio.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Fatura {

    public enum StatusFatura {
        PENDENTE, PAGA, ATRASADA
    }

    private Long id;
    private Long contratoId;
    private String mesReferencia;
    private BigDecimal valor;
    private LocalDate dataVencimento;
    private StatusFatura status;

    public Fatura(Long id, Long contratoId, String mesReferencia, BigDecimal valor, LocalDate dataVencimento) {
        this.id = id;
        this.contratoId = contratoId;
        this.mesReferencia = mesReferencia;
        this.valor = valor;
        this.dataVencimento = dataVencimento;
        this.status = StatusFatura.PENDENTE;
    }


    public void pagar(BigDecimal valorPago) {
        if (valorPago.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor do pagamento deve ser maior que zero.");
        }
        this.status = StatusFatura.PAGA;
    }


    public Long getId() {
        return id;
    }


    public Long getContratoId() {
        return contratoId;
    }


    public String getMesReferencia() {
        return mesReferencia;
    }


    public BigDecimal getValor() {
        return valor;
    }


    public LocalDate getDataVencimento() {
        return dataVencimento;
    }


    public StatusFatura getStatus() {
        return status;
    }
    
    
}
