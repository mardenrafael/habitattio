package br.com.marden.habitattio.application.usecase;

import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.inbound.InativarImovelCommand;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

public class InativarImovelUseCase {
    private final ImovelRepositoryPort imovelRepositoryPort;

    public InativarImovelUseCase(ImovelRepositoryPort imovelRepositoryPort) {
        this.imovelRepositoryPort = imovelRepositoryPort;
    }

    public Imovel executar(InativarImovelCommand command) {
        throw new UnsupportedOperationException();
    }
}
