package br.com.marden.habitattio.ports.inbound;

import java.math.BigDecimal;

public record CadastrarImovelCommand(
    String endereco,
    BigDecimal valorSugerido
) {}
