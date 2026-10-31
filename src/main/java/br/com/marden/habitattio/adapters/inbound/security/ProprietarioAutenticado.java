package br.com.marden.habitattio.adapters.inbound.security;

import java.io.Serializable;

public record ProprietarioAutenticado(Long id, String nome, String email) implements Serializable {
}
