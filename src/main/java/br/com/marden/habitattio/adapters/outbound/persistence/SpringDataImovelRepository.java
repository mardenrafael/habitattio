package br.com.marden.habitattio.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.marden.habitattio.adapters.outbound.persistence.entity.ImovelJpaEntity;

public interface SpringDataImovelRepository extends JpaRepository<ImovelJpaEntity, Long> {}
