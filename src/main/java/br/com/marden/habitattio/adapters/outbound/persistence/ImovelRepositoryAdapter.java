package br.com.marden.habitattio.adapters.outbound.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;

import br.com.marden.habitattio.adapters.outbound.persistence.entity.ImovelJpaEntity;
import br.com.marden.habitattio.domain.exception.IdentificadorImovelDuplicadoException;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

public class ImovelRepositoryAdapter implements ImovelRepositoryPort {

    private static final String CONSTRAINT_IDENTIFICADOR = "uq_imoveis_usuario_identificador";

    private final SpringDataImovelRepository repository;

    public ImovelRepositoryAdapter(SpringDataImovelRepository repository) {
        this.repository = repository;
    }

    @Override
    public Imovel salvar(Imovel imovel) {
        ImovelJpaEntity entity = new ImovelJpaEntity(
            imovel.getId(),
            imovel.getUsuarioId(),
            imovel.getIdentificador(),
            imovel.getEndereco(),
            imovel.getValorSugerido(),
            imovel.isAtivo()
        );

        try {
            return paraDominio(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            String mensagem = e.getMostSpecificCause().getMessage();
            if (mensagem != null && mensagem.toLowerCase().contains(CONSTRAINT_IDENTIFICADOR)) {
                throw new IdentificadorImovelDuplicadoException(imovel.getIdentificador());
            }
            throw e;
        }
    }

    @Override
    public boolean existeIdentificador(Long usuarioId, String identificador) {
        return repository.existsByUsuarioIdAndIdentificadorIgnoreCase(usuarioId, identificador);
    }

    @Override
    public Optional<Imovel> buscarDoProprietario(Long imovelId, Long usuarioId) {
        return repository.findByIdAndUsuarioId(imovelId, usuarioId).map(this::paraDominio);
    }

    private Imovel paraDominio(ImovelJpaEntity entity) {
        return new Imovel(
            entity.getId(),
            entity.getUsuarioId(),
            entity.getIdentificador(),
            entity.getEndereco(),
            entity.getValorSugerido(),
            entity.isAtivo()
        );
    }
}
