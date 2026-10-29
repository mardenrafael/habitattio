package br.com.marden.habitattio.application.usecase;

import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.inbound.CadastrarImovelCommand;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

public class CadastrarImovelUseCase {
    private final ImovelRepositoryPort imovelRepositoryPort;

    public CadastrarImovelUseCase(ImovelRepositoryPort imovelRepositoryPort) {
        this.imovelRepositoryPort = imovelRepositoryPort;
    }

    public Imovel executar(CadastrarImovelCommand command) {
        Imovel imovel = new Imovel(null, command.endereco(), command.valorSugerido());

        return imovelRepositoryPort.salvar(imovel);
    }
}
