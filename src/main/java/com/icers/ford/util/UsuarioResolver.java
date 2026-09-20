package com.icers.ford.util;

import com.icers.ford.model.Usuario;
import com.icers.ford.repository.UsuarioRepository;
import org.springframework.stereotype.Component;

/**
 * Resolve o Usuario autenticado a partir do email do token JWT —
 * centraliza uma lógica que antes estava duplicada como método privado
 * em 3 controllers diferentes (SpecController, UsuarioController,
 * ChatController), no mesmo espírito do IpResolver.
 */
@Component
public class UsuarioResolver {

    private final UsuarioRepository usuarioRepository;

    public UsuarioResolver(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public Usuario resolverUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException(
                        "Usuário autenticado não encontrado no banco"
                ));
    }
}
