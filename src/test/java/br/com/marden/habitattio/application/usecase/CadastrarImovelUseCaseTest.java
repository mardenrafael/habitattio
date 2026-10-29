package br.com.marden.habitattio.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.inbound.CadastrarImovelCommand;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

class CadastrarImovelUseCaseTest {

    /** Fake da porta de saída: substitui o banco por um mapa em memória. */
    private static class ImovelRepositoryFake implements ImovelRepositoryPort {
        private final Map<Long, Imovel> imoveis = new HashMap<>();
        private long proximoId = 1L;

        @Override
        public Imovel salvar(Imovel imovel) {
            Imovel salvo = new Imovel(proximoId++, imovel.getEndereco(), imovel.getValorSugerido(), imovel.isAtivo());
            imoveis.put(salvo.getId(), salvo);
            return salvo;
        }
    }

    @Test
    void deveCadastrarImovelERetornarComIdGerado() {
        ImovelRepositoryFake repositorioFake = new ImovelRepositoryFake();
        CadastrarImovelUseCase useCase = new CadastrarImovelUseCase(repositorioFake);
        CadastrarImovelCommand command = new CadastrarImovelCommand("Rua das Flores, 123", new BigDecimal("1500.00"));

        Imovel imovelCadastrado = useCase.executar(command);

        assertNotNull(imovelCadastrado.getId());
        assertEquals("Rua das Flores, 123", imovelCadastrado.getEndereco());
        assertEquals(new BigDecimal("1500.00"), imovelCadastrado.getValorSugerido());
    }

    @Test
    void deveRejeitarCadastroComValorSugeridoInvalido() {
        ImovelRepositoryFake repositorioFake = new ImovelRepositoryFake();
        CadastrarImovelUseCase useCase = new CadastrarImovelUseCase(repositorioFake);
        CadastrarImovelCommand command = new CadastrarImovelCommand("Rua das Flores, 123", BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> useCase.executar(command));
    }
}
