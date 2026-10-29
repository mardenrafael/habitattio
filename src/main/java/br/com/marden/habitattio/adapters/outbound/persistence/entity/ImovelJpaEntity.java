package br.com.marden.habitattio.adapters.outbound.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "imoveis")
public class ImovelJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String endereco;
    
    @Column(nullable = false)
    private BigDecimal valorSugerido;
    
    @Column(nullable = false)
    private boolean ativo;

    public ImovelJpaEntity() {}

    public ImovelJpaEntity(Long id, String endereco, BigDecimal valorSugerido, boolean ativo) {
        this.id = id;
        this.endereco = endereco;
        this.valorSugerido = valorSugerido;
        this.ativo = ativo;
    }

    public Long getId() { return id; }
    public String getEndereco() { return endereco; }
    public BigDecimal getValorSugerido() { return valorSugerido; }
    public boolean isAtivo() { return ativo; }
}