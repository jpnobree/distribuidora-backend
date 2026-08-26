package com.distribuidora.backend.service;

import com.distribuidora.backend.dto.ContactRequest;
import com.distribuidora.backend.exception.ResourceNotFoundException;
import com.distribuidora.backend.model.ContactMessage;
import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.ContactMessageRepository;
import com.distribuidora.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactService {

    private final ContactMessageRepository contactMessageRepository;
    private final UserRepository userRepository;

    public ContactService(ContactMessageRepository contactMessageRepository, UserRepository userRepository) {
        this.contactMessageRepository = contactMessageRepository;
        this.userRepository = userRepository;
    }

    public ContactMessage create(String requesterUsername, ContactRequest request) {
        User requester = userRepository.findByUsername(requesterUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado: " + requesterUsername));

        ContactMessage message = new ContactMessage();
        message.setRequester(requester);
        message.setProductSlug(request.getProductSlug());
        message.setMessage(request.getMessage());
        message.setPhone(request.getPhone());

        return contactMessageRepository.save(message);
    }

    public List<ContactMessage> findAll() {
        return contactMessageRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<ContactMessage> findMine(String username) {
        User requester = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado: " + username));
        return contactMessageRepository.findByRequesterIdOrderByCreatedAtDesc(requester.getId());
    }
}
