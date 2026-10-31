package br.com.marden.habitattio.adapters.inbound.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Delega o 403 ao HandlerExceptionResolver do MVC, para que a resposta
 * saia pelo TratamentoErrosHandler (ProblemDetail em português).
 */
public class RespostasSeguranca implements AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public RespostasSeguranca(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
