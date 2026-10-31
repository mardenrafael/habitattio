package br.com.marden.habitattio.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import br.com.marden.habitattio.domain.exception.IdentificadorImovelDuplicadoException;
import br.com.marden.habitattio.domain.model.Imovel;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

public class ImovelRepositoryFake implements ImovelRepositoryPort {

    private final Map<Long, Imovel> imoveis = new HashMap<>();
    private long proximoId = 1L;
    private boolean corridaNoProximoSalvar = false;

    public void simularCorridaNoProximoSalvar() {
        this.corridaNoProximoSalvar = true;
    }

    public void limpar() {
        imoveis.clear();
        proximoId = 1L;
        corridaNoProximoSalvar = false;
    }

    public int quantidade() {
        return imoveis.size();
    }

    @Override
    public Imovel salvar(Imovel imovel) {
        if (corridaNoProximoSalvar) {
            corridaNoProximoSalvar = false;
            throw new IdentificadorImovelDuplicadoException(imovel.getIdentificador());
        }
        Long id = imovel.getId() != null ? imovel.getId() : proximoId++;
        Imovel salvo = new Imovel(id, imovel.getUsuarioId(), imovel.getIdentificador(),
                imovel.getEndereco(), imovel.getValorSugerido(), imovel.isAtivo());
        imoveis.put(id, salvo);
        return salvo;
    }

    @Override
    public boolean existeIdentificador(Long usuarioId, String identificador) {
        return imoveis.values().stream()
                .filter(i -> usuarioId.equals(i.getUsuarioId()))
                .anyMatch(i -> i.mesmoIdentificador(identificador));
    }

    @Override
    public Optional<Imovel> buscarDoProprietario(Long imovelId, Long usuarioId) {
        return Optional.ofNullable(imoveis.get(imovelId))
                .filter(i -> usuarioId.equals(i.getUsuarioId()));
    }
}
