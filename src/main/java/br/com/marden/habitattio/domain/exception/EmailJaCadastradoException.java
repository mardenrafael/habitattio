package br.com.marden.habitattio.domain.exception;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("Já existe um proprietário com este e-mail.");
    }
}
