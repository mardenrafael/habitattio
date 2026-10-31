package br.com.marden.habitattio.adapters.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import br.com.marden.habitattio.adapters.inbound.security.RespostasSeguranca;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SegurancaConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        RespostasSeguranca respostas = new RespostasSeguranca(resolver);
        http
                .csrf(csrf -> csrf.spa())
                .sessionManagement(sessao -> sessao.sessionAuthenticationStrategy(trocaIdDaSessaoAoEntrar()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/proprietarios").permitAll()
                        .requestMatchers(HttpMethod.POST, "/sessao").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/sessao")
                        .loginProcessingUrl("/sessao")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .successHandler(respostas)
                        .failureHandler(respostas))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.DELETE, "/sessao"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(respostas)
                        .authenticationEntryPoint(respostas));
        return http.build();
    }

    /**
     * Proteção contra fixação de sessão: se já existe sessão no login, o id é
     * sempre trocado — mesmo quando o cliente não enviou o id (cookie) dela,
     * caso em que a estratégia padrão do Spring Security não age.
     */
    private static SessionAuthenticationStrategy trocaIdDaSessaoAoEntrar() {
        return (authentication, request, response) -> {
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
        };
    }
}
