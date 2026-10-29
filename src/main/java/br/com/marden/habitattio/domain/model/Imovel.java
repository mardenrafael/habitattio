package br.com.marden.habitattio.domain.model;

import java.math.BigDecimal;

public class Imovel {
    private Long id;
    private String endereco;
    private BigDecimal valorSugerido;
    private boolean ativo;

    public Imovel(Long id, String endereco, BigDecimal valorSugerido) {
        validar(endereco, valorSugerido);

        this.id = id;
        this.endereco = endereco;
        this.valorSugerido = valorSugerido;
        this.ativo = true;
    }

    private void validar(String endereco, BigDecimal valorSugerido) {
        if (endereco == null || endereco.isBlank()) {
            throw new IllegalArgumentException("O endereço do imóvel não pode ser vazio.");
        }
        if (valorSugerido == null || valorSugerido.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor sugerido deve ser maior que zero.");
        }
    }

    public void inativar() {
        this.ativo = false;
    }

    
    public Long getId() {
        return id;
    }


    public String getEndereco() {
        return endereco;
    }


    public BigDecimal getValorSugerido() {
        return valorSugerido;
    }


    public boolean isAtivo() {
        return ativo;
    }


}
