package br.com.marden.habitattio.ports.inbound;

public record AutenticarProprietarioCommand(String email, String senha) {
}
