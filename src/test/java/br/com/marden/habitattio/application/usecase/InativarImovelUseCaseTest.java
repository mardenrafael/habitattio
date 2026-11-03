package br.com.marden.habitattio.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.marden.habitattio.domain.exception.ImovelNaoEncontradoException;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.fakes.ImovelRepositoryFake;
import br.com.marden.habitattio.ports.inbound.InativarImovelCommand;

class InativarImovelUseCaseTest {

    private ImovelRepositoryFake repositorio;
    private InativarImovelUseCase useCase;

    @BeforeEach
    void preparar() {
        repositorio = new ImovelRepositoryFake();
        useCase = new InativarImovelUseCase(repositorio);
    }

    @Test
    void deveInativarImovelDoProprio() {
        Imovel salvo = repositorio.salvar(new Imovel(null, 7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00")));

        Imovel inativado = useCase.executar(new InativarImovelCommand(7L, salvo.getId()));

        assertFalse(inativado.isAtivo());
        assertFalse(repositorio.buscarDoProprietario(salvo.getId(), 7L).orElseThrow().isAtivo());
    }

    @Test
    void deveSerIdempotenteAoInativarImovelJaInativo() {
        Imovel salvo = repositorio.salvar(new Imovel(null, 7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00")));
        InativarImovelCommand command = new InativarImovelCommand(7L, salvo.getId());
        useCase.executar(command);

        Imovel inativado = useCase.executar(command);

        assertFalse(inativado.isAtivo());
    }

    @Test
    void deveLancarNaoEncontradoEManterAtivoImovelDeOutroDono() {
        Imovel salvo = repositorio.salvar(new Imovel(null, 7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00")));
        InativarImovelCommand command = new InativarImovelCommand(8L, salvo.getId());

        ImovelNaoEncontradoException excecao = assertThrows(ImovelNaoEncontradoException.class,
                () -> useCase.executar(command));

        assertEquals("Imóvel não encontrado.", excecao.getMessage());
        assertTrue(repositorio.buscarDoProprietario(salvo.getId(), 7L).orElseThrow().isAtivo());
    }

    @Test
    void deveLancarNaoEncontradoParaIdInexistente() {
        InativarImovelCommand command = new InativarImovelCommand(7L, 999L);

        ImovelNaoEncontradoException excecao = assertThrows(ImovelNaoEncontradoException.class,
                () -> useCase.executar(command));

        assertEquals("Imóvel não encontrado.", excecao.getMessage());
    }
}
