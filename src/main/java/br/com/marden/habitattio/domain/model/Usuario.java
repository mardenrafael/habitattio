package br.com.marden.habitattio.domain.model;

public class Usuario {

    private Long id;
    private String nome;
    private String email;
    private String hash_senha;

    public Usuario(Long id, String nome, String email, String hash_senha) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.hash_senha = hash_senha;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getHash_senha() {
        return hash_senha;
    }
}
