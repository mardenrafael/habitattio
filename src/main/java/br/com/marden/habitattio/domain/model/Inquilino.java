package br.com.marden.habitattio.domain.model;

public class Inquilino {
    
    private Long id;
    private String nome;
    private String cpf;
    private String telefone;
    
    public Inquilino(Long id, String nome, String cpf, String telefone) {
        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
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
