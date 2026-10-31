package br.com.marden.habitattio.ports.outbound;

public interface CodificadorSenhaPort {

    String codificar(String senha);

    boolean confere(String senha, String hash);
}
