package br.com.marden.habitattio.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.marden.habitattio.domain.exception.CredenciaisInvalidasException;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.fakes.CodificadorSenhaFake;
import br.com.marden.habitattio.fakes.UsuarioRepositoryFake;
import br.com.marden.habitattio.ports.inbound.AutenticarProprietarioCommand;
import br.com.marden.habitattio.ports.inbound.CadastrarProprietarioCommand;

class AutenticarProprietarioUseCaseTest {

    private static final String MENSAGEM = "E-mail ou senha inválidos.";

    private UsuarioRepositoryFake repositorio;
    private CodificadorSenhaFake codificador;
    private AutenticarProprietarioUseCase useCase;

    @BeforeEach
    void preparar() {
        repositorio = new UsuarioRepositoryFake();
        codificador = new CodificadorSenhaFake();
        new CadastrarProprietarioUseCase(repositorio, codificador)
                .executar(new CadastrarProprietarioCommand("Ana Souza", "ana@exemplo.com", "senha-segura-1"));
        useCase = new AutenticarProprietarioUseCase(repositorio, codificador);
    }

    @Test
    void deveAutenticarComEmailESenhaCorretos() {
        AutenticarProprietarioCommand command =
                new AutenticarProprietarioCommand("ana@exemplo.com", "senha-segura-1");

        Usuario usuario = useCase.executar(command);

        assertEquals("ana@exemplo.com", usuario.getEmail());
        assertEquals("Ana Souza", usuario.getNome());
    }

    @Test
    void deveAutenticarComEmailEmOutraCaixaEComEspacos() {
        AutenticarProprietarioCommand command =
                new AutenticarProprietarioCommand("  ANA@Exemplo.COM ", "senha-segura-1");

        Usuario usuario = useCase.executar(command);

        assertEquals("ana@exemplo.com", usuario.getEmail());
    }

    @Test
    void deveRejeitarSenhaErrada() {
        AutenticarProprietarioCommand command =
                new AutenticarProprietarioCommand("ana@exemplo.com", "senha-errada-9");

        CredenciaisInvalidasException erro =
                assertThrows(CredenciaisInvalidasException.class, () -> useCase.executar(command));

        assertEquals(MENSAGEM, erro.getMessage());
    }

    @Test
    void deveRejeitarEmailInexistenteComAMesmaExcecaoEMensagem() {
        AutenticarProprietarioCommand command =
                new AutenticarProprietarioCommand("ninguem@exemplo.com", "senha-segura-1");

        CredenciaisInvalidasException erro =
                assertThrows(CredenciaisInvalidasException.class, () -> useCase.executar(command));

        assertEquals(MENSAGEM, erro.getMessage());
    }

    @Test
    void deveConferirSenhaMesmoQuandoEmailNaoExiste() {
        AutenticarProprietarioCommand command =
                new AutenticarProprietarioCommand("ninguem@exemplo.com", "senha-qualquer-1");
        int conferenciasAntes = codificador.chamadasConfere();

        assertThrows(CredenciaisInvalidasException.class, () -> useCase.executar(command));

        assertEquals(1, codificador.chamadasConfere() - conferenciasAntes);
    }

    @Test
    void naoDeveExporASenhaNoToStringDoCommand() {
        AutenticarProprietarioCommand command =
                new AutenticarProprietarioCommand("ana@exemplo.com", "senha-segura-1");

        String texto = command.toString();

        assertFalse(texto.contains("senha-segura-1"));
    }
}
