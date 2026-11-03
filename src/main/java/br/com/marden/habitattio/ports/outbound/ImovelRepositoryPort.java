package br.com.marden.habitattio.ports.outbound;

import java.util.Optional;

import br.com.marden.habitattio.domain.model.Imovel;

public interface ImovelRepositoryPort {
    Imovel salvar(Imovel imovel);

    boolean existeIdentificador(Long usuarioId, String identificador);

    Optional<Imovel> buscarDoProprietario(Long imovelId, Long usuarioId);
}
