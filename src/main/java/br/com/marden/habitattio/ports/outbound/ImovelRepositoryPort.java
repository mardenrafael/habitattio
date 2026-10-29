package br.com.marden.habitattio.ports.outbound;

import br.com.marden.habitattio.domain.model.Imovel;

public interface ImovelRepositoryPort {
    Imovel salvar(Imovel imovel);
}
