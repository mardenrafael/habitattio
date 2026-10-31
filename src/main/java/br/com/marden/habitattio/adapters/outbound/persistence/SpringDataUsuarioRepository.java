package br.com.marden.habitattio.adapters.outbound.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.marden.habitattio.adapters.outbound.persistence.entity.UsuarioJpaEntity;

public interface SpringDataUsuarioRepository extends JpaRepository<UsuarioJpaEntity, Long> {

    Optional<UsuarioJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
