package com.icers.ford.security;

import com.icers.ford.model.Usuario;
import com.icers.ford.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Carrega o usuário pelo email para autenticação.
     * Chamado automaticamente pelo Spring Security no login
     * e pelo JwtAuthFilter em cada requisição autenticada.
     */
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        Usuario usuario = usuarioRepository
                .findByEmailAndAtivo(email, "S")
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado ou inativo"
                ));

        // Converte o Role do domínio para authority do Spring Security
        // ROLE_ é o prefixo exigido pelo Spring para hasRole()
        String authority = "ROLE_" + usuario.getRole().name();

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenhaHash())
                .authorities(List.of(new SimpleGrantedAuthority(authority)))
                .accountExpired(false)
                .accountLocked(!usuario.isAtivo())
                .credentialsExpired(false)
                .disabled(!usuario.isAtivo())
                .build();
    }
}