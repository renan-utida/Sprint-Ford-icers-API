package com.icers.ford.repository;

import com.icers.ford.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca usuário pelo email — usado no login
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Busca usuário ativo pelo email — usado na autenticação JWT
     */
    Optional<Usuario> findByEmailAndAtivo(String email, String ativo);

    /**
     * Verifica se já existe um usuário com esse email
     */
    boolean existsByEmail(String email);

    /**
     * Atualiza o timestamp de último acesso após login bem-sucedido
     */
    @Transactional
    @Modifying
    @Query("UPDATE Usuario u SET u.ultimoAcesso = :agora WHERE u.id = :id")
    void atualizarUltimoAcesso(@Param("id") Long id,
                               @Param("agora") LocalDateTime agora);
}