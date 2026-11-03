package br.com.marden.habitattio.adapters.outbound.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.marden.habitattio.adapters.outbound.persistence.entity.ImovelJpaEntity;

public interface SpringDataImovelRepository extends JpaRepository<ImovelJpaEntity, Long> {

    Optional<ImovelJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByUsuarioIdAndIdentificadorIgnoreCase(Long usuarioId, String identificador);
}
