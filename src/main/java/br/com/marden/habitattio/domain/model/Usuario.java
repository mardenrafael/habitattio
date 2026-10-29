package br.com.marden.habitattio.domain.model;

public class Usuario {

    private static final String PADRAO_EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private Long id;
    private String nome;
    private String email;
    private String senhaHash;

    public Usuario(Long id, String nome, String email, String senhaHash) {
        validar(nome, email, senhaHash);

        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
    }

    private void validar(String nome, String email, String senhaHash) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do usuário não pode ser vazio.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("O e-mail do usuário não pode ser vazio.");
        }
        if (!email.matches(PADRAO_EMAIL)) {
            throw new IllegalArgumentException("O e-mail do usuário não é válido.");
        }
        if (senhaHash == null || senhaHash.isBlank()) {
            throw new IllegalArgumentException("O hash da senha não pode ser vazio.");
        }
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

    public String getSenhaHash() {
        return senhaHash;
    }
}
