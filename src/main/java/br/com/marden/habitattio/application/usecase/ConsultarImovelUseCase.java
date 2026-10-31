package br.com.marden.habitattio.application.usecase;

import br.com.marden.habitattio.domain.exception.ImovelNaoEncontradoException;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.inbound.ConsultarImovelCommand;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

public class ConsultarImovelUseCase {
    private final ImovelRepositoryPort imovelRepositoryPort;

    public ConsultarImovelUseCase(ImovelRepositoryPort imovelRepositoryPort) {
        this.imovelRepositoryPort = imovelRepositoryPort;
    }

    public Imovel executar(ConsultarImovelCommand command) {
        return imovelRepositoryPort.buscarDoProprietario(command.imovelId(), command.usuarioId())
                .orElseThrow(ImovelNaoEncontradoException::new);
    }
}
