package br.com.marden.habitattio.adapters.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.marden.habitattio.adapters.outbound.persistence.ImovelRepositoryAdapter;
import br.com.marden.habitattio.adapters.outbound.persistence.SpringDataImovelRepository;
import br.com.marden.habitattio.application.usecase.CadastrarImovelUseCase;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;

/**
 * Único ponto onde o Spring e o domínio se encontram: aqui os adapters e os
 * casos de uso — que não têm nenhuma anotação — são registrados como beans.
 */
@Configuration
public class BeanConfiguration {

    @Bean
    public ImovelRepositoryPort imovelRepositoryPort(SpringDataImovelRepository repository) {
        return new ImovelRepositoryAdapter(repository);
    }

    @Bean
    public CadastrarImovelUseCase cadastrarImovelUseCase(ImovelRepositoryPort imovelRepositoryPort) {
        return new CadastrarImovelUseCase(imovelRepositoryPort);
    }
}
