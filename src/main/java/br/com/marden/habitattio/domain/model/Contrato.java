package br.com.marden.habitattio.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Contrato {
    
    private Long id;
    private Long imovelId;
    private Long inquilinoId;
    private BigDecimal valorAluguel;
    private int diaVencimento;
    private LocalDate dataInicio;
    private boolean ativo;
    
    public Contrato(Long id, Long imovelId, Long inquilinoId, BigDecimal valorAluguel, int diaVencimento,
            LocalDate dataInicio) {

        if (diaVencimento < 1 || diaVencimento > 31) {
            throw new IllegalArgumentException("O dia de vencimento deve estar entre 1 e 31.");
        }

        this.id = id;
        this.imovelId = imovelId;
        this.inquilinoId = inquilinoId;
        this.valorAluguel = valorAluguel;
        this.diaVencimento = diaVencimento;
        this.dataInicio = dataInicio;
        this.ativo = true;
    }

    public void encerrar() {
        this.ativo = false;
    }

    public Long getId() {
        return id;
    }

    public Long getImovelId() {
        return imovelId;
    }

    public Long getInquilinoId() {
        return inquilinoId;
    }

    public BigDecimal getValorAluguel() {
        return valorAluguel;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public boolean isAtivo() {
        return ativo;
    }

}
