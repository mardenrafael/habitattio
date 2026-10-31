package br.com.marden.habitattio.ports.inbound;

public record CadastrarProprietarioCommand(String nome, String email, String senha) {

    @Override
    public String toString() {
        return "CadastrarProprietarioCommand[nome=" + nome + ", email=" + email + ", senha=******]";
    }
}
