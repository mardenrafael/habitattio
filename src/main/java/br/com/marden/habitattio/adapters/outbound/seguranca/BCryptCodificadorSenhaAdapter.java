package br.com.marden.habitattio.adapters.outbound.seguranca;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import br.com.marden.habitattio.ports.outbound.CodificadorSenhaPort;

public class BCryptCodificadorSenhaAdapter implements CodificadorSenhaPort {

    private static final int LIMITE_BYTES_BCRYPT = 72;

    private final BCryptPasswordEncoder encoder;

    public BCryptCodificadorSenhaAdapter(int custo) {
        this.encoder = new BCryptPasswordEncoder(custo);
    }

    @Override
    public String codificar(String senha) {
        return encoder.encode(senha);
    }

    @Override
    public boolean confere(String senha, String hash) {
        if (senha == null || hash == null) {
            return false;
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES_BCRYPT) {
            return false;
        }
        return encoder.matches(senha, hash);
    }
}
