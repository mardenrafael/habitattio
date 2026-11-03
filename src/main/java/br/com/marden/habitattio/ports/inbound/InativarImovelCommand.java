package br.com.marden.habitattio.ports.inbound;

public record InativarImovelCommand(Long usuarioId, Long imovelId) {}
