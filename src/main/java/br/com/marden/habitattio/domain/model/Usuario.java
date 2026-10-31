package br.com.marden.habitattio.domain.model;

import java.util.Locale;

public class Usuario {

    private static final String PADRAO_EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private Long id;
    private String nome;
    private String email;
    private String senhaHash;

    public Usuario(Long id, String nome, String email, String senhaHash) {
        String emailNormalizado = normalizarEmail(email);
        validar(nome, emailNormalizado, senhaHash);

        this.id = id;
        this.nome = nome;
        this.email = emailNormalizado;
        this.senhaHash = senhaHash;
    }

    private void validar(String nome, String email, String senhaHash) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do usuário não pode ser vazio.");
        }
        if (nome.codePointCount(0, nome.length()) > 100) {
            throw new IllegalArgumentException("O nome do usuário deve ter no máximo 100 caracteres.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("O e-mail do usuário não pode ser vazio.");
        }
        if (email.codePointCount(0, email.length()) > 254) {
            throw new IllegalArgumentException("O e-mail do usuário deve ter no máximo 254 caracteres.");
        }
        if (!email.matches(PADRAO_EMAIL)) {
            throw new IllegalArgumentException("O e-mail do usuário não é válido.");
        }
        if (senhaHash == null || senhaHash.isBlank()) {
            throw new IllegalArgumentException("O hash da senha não pode ser vazio.");
        }
    }

    public static String normalizarEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.strip().toLowerCase(Locale.ROOT);
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
