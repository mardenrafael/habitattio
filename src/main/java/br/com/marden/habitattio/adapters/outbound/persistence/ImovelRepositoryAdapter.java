package br.com.marden.habitattio.adapters.outbound.persistence;

import br.com.marden.habitattio.adapters.outbound.persistence.entity.ImovelJpaEntity;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

public class ImovelRepositoryAdapter implements ImovelRepositoryPort{

    private final SpringDataImovelRepository repository;

    public ImovelRepositoryAdapter(SpringDataImovelRepository repository) {
        this.repository = repository;
    }

    @Override
    public Imovel salvar(Imovel imovel) {
        ImovelJpaEntity entity = new ImovelJpaEntity(
            imovel.getId(),
            imovel.getEndereco(),
            imovel.getValorSugerido(),
            imovel.isAtivo()
        );

        ImovelJpaEntity salvo = repository.save(entity);

        return new Imovel(
            salvo.getId(),
            salvo.getEndereco(),
            salvo.getValorSugerido(),
            salvo.isAtivo()
        );
    }
}
