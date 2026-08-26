package com.distribuidora.backend.controller;

import com.distribuidora.backend.dto.ContactRequest;
import com.distribuidora.backend.dto.ContactResponse;
import com.distribuidora.backend.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// "Falar com um vendedor": qualquer usuario logado (ADMIN ou USER) pode
// enviar uma mensagem sobre um produto. Somente ADMIN consegue listar as
// mensagens recebidas (ver SecurityConfig).
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody ContactRequest request) {
        ContactResponse response = ContactResponse.from(contactService.create(principal.getUsername(), request));
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping
    public List<ContactResponse> findAll() {
        return contactService.findAll().stream().map(ContactResponse::from).toList();
    }

    @GetMapping("/mine")
    public List<ContactResponse> findMine(@AuthenticationPrincipal UserDetails principal) {
        return contactService.findMine(principal.getUsername()).stream().map(ContactResponse::from).toList();
    }
}
