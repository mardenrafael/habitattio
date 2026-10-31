package br.com.marden.habitattio.domain.exception;

public class IdentificadorImovelDuplicadoException extends RuntimeException {

    public IdentificadorImovelDuplicadoException() {
        super();
    }

    public IdentificadorImovelDuplicadoException(String identificador) {
        super("Você já tem um imóvel com o identificador '" + identificador + "'.");
    }
}
