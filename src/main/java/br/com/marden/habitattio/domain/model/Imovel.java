package br.com.marden.habitattio.domain.model;

import java.math.BigDecimal;

public class Imovel {
    private Long id;
    private Long usuarioId;
    private String identificador;
    private String endereco;
    private BigDecimal valorSugerido;
    private boolean ativo;

    public Imovel(Long id, Long usuarioId, String identificador, String endereco, BigDecimal valorSugerido) {
        this(id, usuarioId, identificador, endereco, valorSugerido, true);
    }

    public Imovel(Long id, Long usuarioId, String identificador, String endereco, BigDecimal valorSugerido, boolean ativo) {
        if (usuarioId == null) {
            throw new IllegalArgumentException("O imóvel deve pertencer a um proprietário.");
        }
        String identificadorNormalizado = identificador == null ? null : identificador.strip();
        if (identificadorNormalizado == null || identificadorNormalizado.isEmpty()) {
            throw new IllegalArgumentException("O identificador do imóvel não pode ser vazio.");
        }
        if (identificadorNormalizado.codePointCount(0, identificadorNormalizado.length()) > 50) {
            throw new IllegalArgumentException("O identificador do imóvel deve ter no máximo 50 caracteres.");
        }
        validar(endereco, valorSugerido);

        this.id = id;
        this.usuarioId = usuarioId;
        this.identificador = identificadorNormalizado;
        this.endereco = endereco;
        this.valorSugerido = valorSugerido;
        this.ativo = ativo;
    }

    public boolean mesmoIdentificador(String outro) {
        return outro != null && identificador.strip().equalsIgnoreCase(outro.strip());
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getIdentificador() {
        return identificador;
    }

    private void validar(String endereco, BigDecimal valorSugerido) {
        if (endereco == null || endereco.isBlank()) {
            throw new IllegalArgumentException("O endereço do imóvel não pode ser vazio.");
        }
        if (endereco.codePointCount(0, endereco.length()) > 255) {
            throw new IllegalArgumentException("O endereço do imóvel deve ter no máximo 255 caracteres.");
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
