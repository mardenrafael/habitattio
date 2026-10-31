package br.com.marden.habitattio.adapters.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;

import br.com.marden.habitattio.adapters.inbound.security.ProprietarioAuthenticationProvider;
import br.com.marden.habitattio.adapters.outbound.persistence.ImovelRepositoryAdapter;
import br.com.marden.habitattio.adapters.outbound.persistence.SpringDataImovelRepository;
import br.com.marden.habitattio.adapters.outbound.persistence.SpringDataUsuarioRepository;
import br.com.marden.habitattio.adapters.outbound.persistence.UsuarioRepositoryAdapter;
import br.com.marden.habitattio.adapters.outbound.seguranca.BCryptCodificadorSenhaAdapter;
import br.com.marden.habitattio.application.usecase.AutenticarProprietarioUseCase;
import br.com.marden.habitattio.application.usecase.CadastrarImovelUseCase;
import br.com.marden.habitattio.application.usecase.CadastrarProprietarioUseCase;
import br.com.marden.habitattio.application.usecase.ConsultarImovelUseCase;
import br.com.marden.habitattio.application.usecase.InativarImovelUseCase;
import br.com.marden.habitattio.ports.outbound.CodificadorSenhaPort;
import br.com.marden.habitattio.ports.outbound.ImovelRepositoryPort;
import br.com.marden.habitattio.ports.outbound.UsuarioRepositoryPort;

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

    @Bean
    public ConsultarImovelUseCase consultarImovelUseCase(ImovelRepositoryPort imovelRepositoryPort) {
        return new ConsultarImovelUseCase(imovelRepositoryPort);
    }

    @Bean
    public InativarImovelUseCase inativarImovelUseCase(ImovelRepositoryPort imovelRepositoryPort) {
        return new InativarImovelUseCase(imovelRepositoryPort);
    }

    @Bean
    public UsuarioRepositoryPort usuarioRepositoryPort(SpringDataUsuarioRepository repository) {
        return new UsuarioRepositoryAdapter(repository);
    }

    @Bean
    public CodificadorSenhaPort codificadorSenhaPort(@Value("${habitattio.seguranca.bcrypt-custo}") int custo) {
        return new BCryptCodificadorSenhaAdapter(custo);
    }

    @Bean
    public CadastrarProprietarioUseCase cadastrarProprietarioUseCase(UsuarioRepositoryPort usuarioRepositoryPort,
            CodificadorSenhaPort codificadorSenhaPort) {
        return new CadastrarProprietarioUseCase(usuarioRepositoryPort, codificadorSenhaPort);
    }

    @Bean
    public AutenticarProprietarioUseCase autenticarProprietarioUseCase(UsuarioRepositoryPort usuarioRepositoryPort,
            CodificadorSenhaPort codificadorSenhaPort) {
        return new AutenticarProprietarioUseCase(usuarioRepositoryPort, codificadorSenhaPort);
    }

    @Bean
    public AuthenticationProvider proprietarioAuthenticationProvider(
            AutenticarProprietarioUseCase autenticarProprietarioUseCase) {
        return new ProprietarioAuthenticationProvider(autenticarProprietarioUseCase);
    }
}
