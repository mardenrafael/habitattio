package br.com.marden.habitattio.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.marden.habitattio.domain.exception.ImovelNaoEncontradoException;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.fakes.ImovelRepositoryFake;
import br.com.marden.habitattio.ports.inbound.ConsultarImovelCommand;

class ConsultarImovelUseCaseTest {

    private ImovelRepositoryFake repositorio;
    private ConsultarImovelUseCase useCase;

    @BeforeEach
    void preparar() {
        repositorio = new ImovelRepositoryFake();
        useCase = new ConsultarImovelUseCase(repositorio);
    }

    @Test
    void deveRetornarImovelDoProprio() {
        Imovel salvo = repositorio.salvar(new Imovel(null, 7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00")));

        Imovel imovel = useCase.executar(new ConsultarImovelCommand(7L, salvo.getId()));

        assertEquals(salvo.getId(), imovel.getId());
        assertEquals("Apto 101", imovel.getIdentificador());
    }

    @Test
    void deveLancarNaoEncontradoParaImovelDeOutroDono() {
        Imovel salvo = repositorio.salvar(new Imovel(null, 7L, "Apto 101", "Rua A, 10", new BigDecimal("1500.00")));
        ConsultarImovelCommand command = new ConsultarImovelCommand(8L, salvo.getId());

        ImovelNaoEncontradoException excecao = assertThrows(ImovelNaoEncontradoException.class,
                () -> useCase.executar(command));

        assertEquals("Imóvel não encontrado.", excecao.getMessage());
    }

    @Test
    void deveLancarNaoEncontradoParaIdInexistente() {
        ConsultarImovelCommand command = new ConsultarImovelCommand(7L, 999L);

        ImovelNaoEncontradoException excecao = assertThrows(ImovelNaoEncontradoException.class,
                () -> useCase.executar(command));

        assertEquals("Imóvel não encontrado.", excecao.getMessage());
    }
}
