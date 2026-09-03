package com.distribuidora.backend.service;

import com.distribuidora.backend.dto.ContactRequest;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.ContactMessage;
import com.distribuidora.backend.model.Role;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.ContactMessageRepository;
import com.distribuidora.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactMessageRepository contactMessageRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ContactService contactService;

    @Test
    void create_deveSalvarMensagemAtreladaAoUsuario_quandoUsuarioExiste() {
        User cliente = new User("cliente", "hash", null, Role.USER);
        cliente.setId(42L);

        ContactRequest request = new ContactRequest();
        request.setProductSlug("picanha-premium-98562");
        request.setMessage("Qual o preco para 20kg?");
        request.setPhone("(85) 99999-9999");

        when(userRepository.findByUsername("cliente")).thenReturn(Optional.of(cliente));
        when(contactMessageRepository.save(any(ContactMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ContactMessage salvo = contactService.create("cliente", request);

        assertEquals(cliente, salvo.getRequester());
        assertEquals("picanha-premium-98562", salvo.getProductSlug());
        assertEquals("Qual o preco para 20kg?", salvo.getMessage());
        assertEquals("(85) 99999-9999", salvo.getPhone());
    }

    @Test
    void create_deveLancarResourceNotFoundException_quandoUsuarioNaoExiste() {
        ContactRequest request = new ContactRequest();
        request.setMessage("Mensagem qualquer");

        when(userRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> contactService.create("fantasma", request));

        verify(contactMessageRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void findMine_deveListarSomenteMensagensDoUsuarioLogado() {
        User cliente = new User("cliente", "hash", null, Role.USER);
        cliente.setId(7L);

        ContactMessage mensagem = new ContactMessage();
        mensagem.setRequester(cliente);
        mensagem.setMessage("Tem entrega pra zona leste?");

        when(userRepository.findByUsername("cliente")).thenReturn(Optional.of(cliente));
        when(contactMessageRepository.findByRequesterIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(mensagem));

        List<ContactMessage> resultado = contactService.findMine("cliente");

        assertEquals(1, resultado.size());
        assertEquals("Tem entrega pra zona leste?", resultado.get(0).getMessage());

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(contactMessageRepository).findByRequesterIdOrderByCreatedAtDesc(idCaptor.capture());
        assertEquals(7L, idCaptor.getValue());
    }
}
