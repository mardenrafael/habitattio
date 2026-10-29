package br.com.marden.habitattio.domain.model;

public class Inquilino {

    private Long id;
    private String nome;
    private String cpf;
    private String telefone;

    public Inquilino(Long id, String nome, String cpf, String telefone) {
        validar(nome, cpf);

        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
    }

    private void validar(String nome, String cpf) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do inquilino não pode ser vazio.");
        }
        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("O CPF do inquilino não pode ser vazio.");
        }
        if (!cpf.matches("\\d{11}")) {
            throw new IllegalArgumentException("O CPF do inquilino deve conter 11 dígitos.");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getCpf() {
        return cpf;
    }

    public String getTelefone() {
        return telefone;
    }

}
