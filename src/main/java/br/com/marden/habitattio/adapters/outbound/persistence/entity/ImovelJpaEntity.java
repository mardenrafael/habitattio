package br.com.marden.habitattio.adapters.outbound.persistence.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "imoveis", uniqueConstraints = @UniqueConstraint(
    name = "uq_imoveis_usuario_identificador",
    columnNames = {"usuario_id", "identificador"}))
public class ImovelJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 50)
    private String identificador;

    @Column(nullable = false, length = 255)
    private String endereco;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal valorSugerido;

    @Column(nullable = false)
    private boolean ativo;

    public ImovelJpaEntity() {}

    public ImovelJpaEntity(Long id, Long usuarioId, String identificador, String endereco,
            BigDecimal valorSugerido, boolean ativo) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.identificador = identificador;
        this.endereco = endereco;
        this.valorSugerido = valorSugerido;
        this.ativo = ativo;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getIdentificador() { return identificador; }
    public String getEndereco() { return endereco; }
    public BigDecimal getValorSugerido() { return valorSugerido; }
    public boolean isAtivo() { return ativo; }
}
