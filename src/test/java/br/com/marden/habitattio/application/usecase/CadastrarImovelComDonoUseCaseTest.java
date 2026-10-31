package br.com.marden.habitattio.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.marden.habitattio.domain.exception.IdentificadorImovelDuplicadoException;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.fakes.ImovelRepositoryFake;
import br.com.marden.habitattio.ports.inbound.CadastrarImovelCommand;

class CadastrarImovelComDonoUseCaseTest {

    private ImovelRepositoryFake repositorio;
    private CadastrarImovelUseCase useCase;

    @BeforeEach
    void preparar() {
        repositorio = new ImovelRepositoryFake();
        useCase = new CadastrarImovelUseCase(repositorio);
    }

    @Test
    void deveCadastrarImovelParaODonoDoCommand() {
        CadastrarImovelCommand command = new CadastrarImovelCommand(7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00"));

        Imovel imovel = useCase.executar(command);

        assertNotNull(imovel.getId());
        assertEquals(7L, imovel.getUsuarioId());
        assertEquals("Apto 101", imovel.getIdentificador());
        assertTrue(imovel.isAtivo());
        assertEquals(1, repositorio.quantidade());
    }

    @Test
    void deveRejeitarIdentificadorRepetidoParaOMesmoDonoIgnorandoCaixa() {
        repositorio.salvar(new Imovel(null, 7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00")));
        CadastrarImovelCommand command = new CadastrarImovelCommand(7L, "apto 101", "Rua B, 20", new BigDecimal("1200.00"));

        IdentificadorImovelDuplicadoException excecao = assertThrows(IdentificadorImovelDuplicadoException.class,
                () -> useCase.executar(command));

        assertEquals("Você já tem um imóvel com o identificador 'apto 101'.", excecao.getMessage());
        assertEquals(1, repositorio.quantidade());
    }

    @Test
    void deveAceitarMesmoIdentificadorParaDonosDiferentes() {
        repositorio.salvar(new Imovel(null, 7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00")));
        CadastrarImovelCommand command = new CadastrarImovelCommand(8L, "Apto 101", "Rua B, 20", new BigDecimal("1200.00"));

        Imovel imovel = useCase.executar(command);

        assertEquals(8L, imovel.getUsuarioId());
        assertEquals(2, repositorio.quantidade());
    }

    @Test
    void devePropagarDuplicidadeQuandoORepositorioDetectaCorrida() {
        repositorio.simularCorridaNoProximoSalvar();
        CadastrarImovelCommand command = new CadastrarImovelCommand(7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00"));

        assertThrows(IdentificadorImovelDuplicadoException.class, () -> useCase.executar(command));
    }

    @Test
    void deveRejeitarValorSugeridoZero() {
        CadastrarImovelCommand command = new CadastrarImovelCommand(7L, "Apto 101", "Rua A, 10", BigDecimal.ZERO);

        IllegalArgumentException excecao = assertThrows(IllegalArgumentException.class, () -> useCase.executar(command));

        assertEquals("O valor sugerido deve ser maior que zero.", excecao.getMessage());
        assertEquals(0, repositorio.quantidade());
    }
}
