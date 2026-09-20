package com.icers.ford.service;

import com.icers.ford.dto.request.UsuarioCreateRequest;
import com.icers.ford.dto.request.UsuarioUpdateRequest;
import com.icers.ford.dto.response.UsuarioResponse;
import com.icers.ford.exception.AutoDesativacaoException;
import com.icers.ford.exception.EmailJaCadastradoException;
import com.icers.ford.exception.UsuarioNaoEncontradoException;
import com.icers.ford.model.Usuario;
import com.icers.ford.model.enums.Role;
import com.icers.ford.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));
        return toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse criar(UsuarioCreateRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException();
        }

        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .senhaHash(passwordEncoder.encode(request.senha()))
                .role(Role.valueOf(request.role()))
                .ativo("S")
                .build();

        Usuario salvo;
        try {
            // saveAndFlush, não save: com GenerationType.SEQUENCE o
            // save() só aloca o ID da sequence — o INSERT real fica
            // pendente pro flush/commit do Hibernate, que só acontece
            // DEPOIS deste método retornar (dentro do @Transactional).
            // Um save() simples nunca lançaria a exceção aqui dentro do
            // catch; ela apareceria tarde demais, em
            // JpaTransactionManager.doCommit(), fora do try/catch —
            // confirmado em teste de concorrência real (500 em vez de 409).
            salvo = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Corrida de concorrência: duas requisições pro MESMO email
            // passaram no existsByEmail() acima antes de qualquer uma
            // commitar — a constraint única do banco rejeita a segunda.
            // Relança como EmailJaCadastradoException (409), não um 500
            // genérico — é exatamente essa a situação real.
            throw new EmailJaCadastradoException();
        }

        log.info("[AUDITORIA] Usuário criado — id: {} | role: {}",
                salvo.getId(), salvo.getRole());

        return toResponse(salvo);
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioUpdateRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        // Só barra se o email pertencer a OUTRO usuário — o próprio
        // dono pode "atualizar" mantendo o mesmo email sem problema.
        usuarioRepository.findByEmail(request.email())
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new EmailJaCadastradoException();
                });

        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setRole(Role.valueOf(request.role()));

        Usuario salvo;
        try {
            // saveAndFlush — mesmo motivo do criar(): o UPDATE fica
            // pendente pro flush/commit se não forçarmos aqui dentro.
            salvo = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Mesma corrida de concorrência do criar() — duas
            // atualizações pro mesmo email novo passaram no
            // findByEmail() acima antes de qualquer uma commitar.
            throw new EmailJaCadastradoException();
        }

        log.info("[AUDITORIA] Usuário atualizado — id: {}", id);

        return toResponse(salvo);
    }

    /**
     * Desativação REVERSÍVEL — diferente de anonimizar(). Só muda
     * ativo='N' (bloqueia login), sem tocar no email. Pensada pra
     * suspensão temporária (ex: funcionário afastado), não descarte
     * de dado pessoal.
     */
    @Transactional
    public void desativar(Long id, Long idQuemPediu) {
        if (id.equals(idQuemPediu)) {
            throw new AutoDesativacaoException();
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        usuario.setAtivo("N");
        usuarioRepository.save(usuario);

        log.info("[AUDITORIA] Usuário desativado — id: {}", id);
    }

    @Transactional
    public void reativar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        usuario.setAtivo("S");
        usuarioRepository.save(usuario);

        log.info("[AUDITORIA] Usuário reativado — id: {}", id);
    }

    /**
     * Anonimização irreversível — diferente de soft delete (campo
     * ativo). Remove o email (único dado identificável do Usuario)
     * substituindo por um placeholder único e desativa a conta.
     * A linha em si permanece, preservando integridade referencial
     * com sr_fichas_tecnicas.criado_por, sr_historico_consultas, etc.
     */
    @Transactional
    public void anonimizar(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(id));

        usuario.setEmail("anonimizado-" + usuario.getId() + "@deleted.local");
        usuario.setAtivo("N");
        usuarioRepository.save(usuario);

        log.info("[AUDITORIA] Usuário anonimizado — id: {}", id);
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getRole().name(),
                usuario.getAtivo(),
                usuario.getCriadoEm(),
                usuario.getUltimoAcesso()
        );
    }
}