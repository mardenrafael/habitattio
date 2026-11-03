package br.com.marden.habitattio.ports.inbound;

import java.math.BigDecimal;

public record CadastrarImovelCommand(
    Long usuarioId,
    String identificador,
    String endereco,
    BigDecimal valorSugerido
) {}
