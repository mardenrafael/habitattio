package br.com.marden.habitattio.adapters.outbound.seguranca;

import br.com.marden.habitattio.ports.outbound.CodificadorSenhaPort;

public class BCryptCodificadorSenhaAdapter implements CodificadorSenhaPort {

    private final int custo;

    public BCryptCodificadorSenhaAdapter(int custo) {
        this.custo = custo;
    }

    @Override
    public String codificar(String senha) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean confere(String senha, String hash) {
        throw new UnsupportedOperationException();
    }
}
