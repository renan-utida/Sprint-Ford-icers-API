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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes - UsuarioService")
public class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario admin;
    private Usuario analyst;
    private UsuarioCreateRequest createRequestPadrao;
    private UsuarioUpdateRequest updateRequestPadrao;

    @BeforeEach
    public void setUp() {
        admin = Usuario.builder()
                .id(1L)
                .nome("Administrador SpecRadar")
                .email("admin@specradar.com")
                .senhaHash("$2a$12$hashAdmin")
                .role(Role.ADMIN)
                .ativo("S")
                .criadoEm(LocalDateTime.now())
                .build();

        analyst = Usuario.builder()
                .id(2L)
                .nome("Analista Ford")
                .email("analyst@specradar.com")
                .senhaHash("$2a$12$hashAnalyst")
                .role(Role.ANALYST)
                .ativo("S")
                .criadoEm(LocalDateTime.now())
                .build();

        createRequestPadrao = new UsuarioCreateRequest(
                "Novo Usuario", "novo@specradar.com", "Senha@123", "ANALYST");

        updateRequestPadrao = new UsuarioUpdateRequest(
                "Analista Ford Atualizado", "analyst@specradar.com", "ANALYST");
    }

    // listar

    @Test
    @DisplayName("Deve listar todos os usuários mapeados corretamente")
    public void testListar() {
        when(usuarioRepository.findAll()).thenReturn(List.of(admin, analyst));

        List<UsuarioResponse> resultado = usuarioService.listar();

        assertEquals(2, resultado.size());
        assertEquals("admin@specradar.com", resultado.get(0).email());
        assertEquals("analyst@specradar.com", resultado.get(1).email());
    }

    // buscarPorId

    @Test
    @DisplayName("Deve buscar usuário por ID existente com sucesso")
    public void testBuscarPorIdExistente() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        UsuarioResponse resultado = usuarioService.buscarPorId(1L);

        assertEquals(1L, resultado.id());
        assertEquals("admin@specradar.com", resultado.email());
        assertEquals("ADMIN", resultado.role());
    }

    @Test
    @DisplayName("Deve lançar UsuarioNaoEncontradoException ao buscar ID inexistente")
    public void testBuscarPorIdInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNaoEncontradoException.class,
                () -> usuarioService.buscarPorId(99L));
    }

    // criar

    @Test
    @DisplayName("Deve criar usuário com sucesso, com senha encodada")
    public void testCriarComSucesso() {
        when(usuarioRepository.existsByEmail("novo@specradar.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("$2a$12$hashNovo");
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenAnswer(invocation -> {
                    Usuario u = invocation.getArgument(0);
                    u.setId(3L);
                    u.setCriadoEm(LocalDateTime.now());
                    return u;
                });

        UsuarioResponse resultado = usuarioService.criar(createRequestPadrao);

        assertNotNull(resultado);
        assertEquals("novo@specradar.com", resultado.email());
        assertEquals("ANALYST", resultado.role());
        assertEquals("S", resultado.ativo());
        verify(passwordEncoder, times(1)).encode("Senha@123");
        verify(usuarioRepository, times(1)).saveAndFlush(any(Usuario.class));
    }

    @Test
    @DisplayName("Deve lançar EmailJaCadastradoException ao criar com email já existente")
    public void testCriarEmailDuplicadoDetectadoAntes() {
        when(usuarioRepository.existsByEmail("novo@specradar.com")).thenReturn(true);

        assertThrows(EmailJaCadastradoException.class,
                () -> usuarioService.criar(createRequestPadrao));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("REGRESSÃO — corrida de concorrência: DataIntegrityViolationException do saveAndFlush deve virar EmailJaCadastradoException (409), não propagar como 500")
    public void testCriarCorridaDeConcorrenciaEmailDuplicado() {
        // Duas requisições pro MESMO email passam no existsByEmail() (ambas
        // false) antes de qualquer uma commitar — a constraint única do
        // banco só rejeita a segunda no saveAndFlush().
        when(usuarioRepository.existsByEmail("novo@specradar.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("uk_sr_usuario_email violada"));

        assertThrows(EmailJaCadastradoException.class,
                () -> usuarioService.criar(createRequestPadrao));
    }

    @Test
    @DisplayName("Deve usar saveAndFlush, nunca save, ao criar (INSERT precisa ser síncrono)")
    public void testCriarUsaSaveAndFlushNuncaSave() {
        when(usuarioRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenReturn(admin);

        usuarioService.criar(createRequestPadrao);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // atualizar

    @Test
    @DisplayName("Deve atualizar usuário com sucesso mantendo o próprio email")
    public void testAtualizarMantendoProprioEmail() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(analyst));
        when(usuarioRepository.findByEmail("analyst@specradar.com")).thenReturn(Optional.of(analyst));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenReturn(analyst);

        UsuarioResponse resultado = usuarioService.atualizar(2L, updateRequestPadrao);

        assertNotNull(resultado);
        assertEquals("Analista Ford Atualizado", analyst.getNome());
        verify(usuarioRepository, times(1)).saveAndFlush(any(Usuario.class));
    }

    @Test
    @DisplayName("Deve lançar UsuarioNaoEncontradoException ao atualizar ID inexistente")
    public void testAtualizarIdInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNaoEncontradoException.class,
                () -> usuarioService.atualizar(99L, updateRequestPadrao));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Deve lançar EmailJaCadastradoException ao atualizar com email de OUTRO usuário")
    public void testAtualizarEmailDeOutroUsuario() {
        UsuarioUpdateRequest request = new UsuarioUpdateRequest(
                "Analista Ford", "admin@specradar.com", "ANALYST");

        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(analyst));
        // admin@specradar.com já pertence ao usuário id=1, que não é o id=2 sendo atualizado
        when(usuarioRepository.findByEmail("admin@specradar.com")).thenReturn(Optional.of(admin));

        assertThrows(EmailJaCadastradoException.class,
                () -> usuarioService.atualizar(2L, request));

        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("REGRESSÃO — corrida de concorrência: DataIntegrityViolationException do saveAndFlush ao atualizar também vira EmailJaCadastradoException")
    public void testAtualizarCorridaDeConcorrenciaEmailDuplicado() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(analyst));
        when(usuarioRepository.findByEmail("analyst@specradar.com")).thenReturn(Optional.of(analyst));
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("uk_sr_usuario_email violada"));

        assertThrows(EmailJaCadastradoException.class,
                () -> usuarioService.atualizar(2L, updateRequestPadrao));
    }

    // desativar

    @Test
    @DisplayName("Deve lançar AutoDesativacaoException ao tentar desativar a própria conta")
    public void testDesativarAPropriaConta() {
        assertThrows(AutoDesativacaoException.class,
                () -> usuarioService.desativar(1L, 1L));

        verify(usuarioRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Deve desativar usuário com sucesso (ativo vira N)")
    public void testDesativarComSucesso() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(analyst));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(analyst);

        usuarioService.desativar(2L, 1L);

        assertEquals("N", analyst.getAtivo());
        verify(usuarioRepository, times(1)).save(analyst);
    }

    @Test
    @DisplayName("Deve lançar UsuarioNaoEncontradoException ao desativar ID inexistente")
    public void testDesativarIdInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNaoEncontradoException.class,
                () -> usuarioService.desativar(99L, 1L));

        verify(usuarioRepository, never()).save(any());
    }

    // reativar

    @Test
    @DisplayName("Deve reativar usuário desativado com sucesso (ativo vira S)")
    public void testReativarComSucesso() {
        analyst.setAtivo("N");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(analyst));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(analyst);

        usuarioService.reativar(2L);

        assertEquals("S", analyst.getAtivo());
        verify(usuarioRepository, times(1)).save(analyst);
    }

    @Test
    @DisplayName("Deve lançar UsuarioNaoEncontradoException ao reativar ID inexistente")
    public void testReativarIdInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNaoEncontradoException.class,
                () -> usuarioService.reativar(99L));

        verify(usuarioRepository, never()).save(any());
    }

    // anonimizar

    @Test
    @DisplayName("Deve anonimizar usuário com sucesso (email placeholder + ativo N)")
    public void testAnonimizarComSucesso() {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(analyst));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(analyst);

        usuarioService.anonimizar(2L);

        assertEquals("anonimizado-2@deleted.local", analyst.getEmail());
        assertEquals("N", analyst.getAtivo());
        verify(usuarioRepository, times(1)).save(analyst);
    }

    @Test
    @DisplayName("Deve lançar UsuarioNaoEncontradoException ao anonimizar ID inexistente")
    public void testAnonimizarIdInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsuarioNaoEncontradoException.class,
                () -> usuarioService.anonimizar(99L));

        verify(usuarioRepository, never()).save(any());
    }
}
