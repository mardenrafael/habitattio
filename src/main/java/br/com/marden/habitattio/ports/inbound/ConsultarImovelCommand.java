package br.com.marden.habitattio.ports.inbound;

public record ConsultarImovelCommand(Long usuarioId, Long imovelId) {}
