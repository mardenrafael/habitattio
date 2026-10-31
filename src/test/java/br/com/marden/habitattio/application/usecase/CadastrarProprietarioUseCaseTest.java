package br.com.marden.habitattio.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.marden.habitattio.domain.exception.EmailJaCadastradoException;
import br.com.marden.habitattio.domain.model.Usuario;
import br.com.marden.habitattio.fakes.CodificadorSenhaFake;
import br.com.marden.habitattio.fakes.UsuarioRepositoryFake;
import br.com.marden.habitattio.ports.inbound.CadastrarProprietarioCommand;

class CadastrarProprietarioUseCaseTest {

    private UsuarioRepositoryFake repositorio;
    private CodificadorSenhaFake codificador;
    private CadastrarProprietarioUseCase useCase;

    @BeforeEach
    void preparar() {
        repositorio = new UsuarioRepositoryFake();
        codificador = new CodificadorSenhaFake();
        useCase = new CadastrarProprietarioUseCase(repositorio, codificador);
    }

    @Test
    void deveCadastrarProprietarioComEmailNormalizadoESenhaCodificada() {
        CadastrarProprietarioCommand command =
                new CadastrarProprietarioCommand("Ana Souza", " Ana@Exemplo.COM ", "senha-segura-1");

        Usuario usuario = useCase.executar(command);

        assertNotNull(usuario.getId());
        assertEquals("Ana Souza", usuario.getNome());
        assertEquals("ana@exemplo.com", usuario.getEmail());
        assertNotEquals("senha-segura-1", usuario.getSenhaHash());
        assertEquals(1, repositorio.quantidade());
    }

    @Test
    void deveRejeitarEmailJaCadastradoIgnorandoCaixaEEspacos() {
        useCase.executar(new CadastrarProprietarioCommand("Ana Souza", "ana@exemplo.com", "senha-segura-1"));
        int codificacoesAntes = codificador.chamadasCodificar();
        CadastrarProprietarioCommand repetido =
                new CadastrarProprietarioCommand("Outra Ana", " ANA@Exemplo.com ", "outra-senha-1");

        assertThrows(EmailJaCadastradoException.class, () -> useCase.executar(repetido));

        assertEquals(1, repositorio.quantidade());
        assertEquals(codificacoesAntes, codificador.chamadasCodificar());
    }

    @Test
    void deveRejeitarSenhaCurtaAntesDeConsultarORepositorio() {
        useCase.executar(new CadastrarProprietarioCommand("Ana Souza", "ana@exemplo.com", "senha-segura-1"));
        CadastrarProprietarioCommand comSenhaCurta =
                new CadastrarProprietarioCommand("Outra Ana", "ana@exemplo.com", "curta");

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> useCase.executar(comSenhaCurta));

        assertEquals("A senha deve ter no mínimo 8 caracteres.", erro.getMessage());
        assertEquals(1, repositorio.quantidade());
    }

    @Test
    void deveRejeitarNomeEmBranco() {
        CadastrarProprietarioCommand command =
                new CadastrarProprietarioCommand("   ", "ana@exemplo.com", "senha-segura-1");

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> useCase.executar(command));

        assertEquals("O nome do usuário não pode ser vazio.", erro.getMessage());
        assertEquals(0, repositorio.quantidade());
    }

    @Test
    void deveRejeitarEmailInvalido() {
        CadastrarProprietarioCommand command =
                new CadastrarProprietarioCommand("Ana Souza", "nao-e-um-email", "senha-segura-1");

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> useCase.executar(command));

        assertEquals("O e-mail do usuário não é válido.", erro.getMessage());
        assertEquals(0, repositorio.quantidade());
    }

    @Test
    void devePropagarEmailJaCadastradoQuandoORepositorioDetectaCorrida() {
        repositorio.simularCorridaNoProximoSalvar();
        CadastrarProprietarioCommand command =
                new CadastrarProprietarioCommand("Ana Souza", "ana@exemplo.com", "senha-segura-1");

        assertThrows(EmailJaCadastradoException.class, () -> useCase.executar(command));

        assertEquals(0, repositorio.quantidade());
    }

    @Test
    void naoDeveExporASenhaNoToStringDoCommand() {
        CadastrarProprietarioCommand command =
                new CadastrarProprietarioCommand("Ana Souza", "ana@exemplo.com", "senha-segura-1");

        String texto = command.toString();

        assertFalse(texto.contains("senha-segura-1"));
    }
}
