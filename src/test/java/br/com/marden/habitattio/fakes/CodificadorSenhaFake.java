package br.com.marden.habitattio.fakes;

import br.com.marden.habitattio.ports.outbound.CodificadorSenhaPort;

public class CodificadorSenhaFake implements CodificadorSenhaPort {

    private int codificacoes = 0;
    private int chamadasConfere = 0;

    @Override
    public String codificar(String senha) {
        codificacoes++;
        return "hash:" + senha + ":" + codificacoes;
    }

    @Override
    public boolean confere(String senha, String hash) {
        chamadasConfere++;
        if (senha == null || hash == null || !hash.startsWith("hash:")) {
            return false;
        }
        int fim = hash.lastIndexOf(':');
        return fim > 5 && hash.substring(5, fim).equals(senha);
    }

    public int chamadasConfere() {
        return chamadasConfere;
    }

    public int chamadasCodificar() {
        return codificacoes;
    }
}
