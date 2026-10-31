package br.com.marden.habitattio.domain.model;

import java.nio.charset.StandardCharsets;

public class Senha {

    private static final int MINIMO_CARACTERES = 8;
    private static final int MAXIMO_BYTES = 72;

    private final String valor;

    public Senha(String valor) {
        if (valor == null || valor.isEmpty()) {
            throw new IllegalArgumentException("A senha não pode ser vazia.");
        }
        if (valor.codePointCount(0, valor.length()) < MINIMO_CARACTERES) {
            throw new IllegalArgumentException("A senha deve ter no mínimo 8 caracteres.");
        }
        if (valor.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            throw new IllegalArgumentException(
                    "A senha é longa demais: use no máximo 72 bytes (cerca de 72 letras sem acento).");
        }
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    @Override
    public String toString() {
        return "Senha[******]";
    }
}
