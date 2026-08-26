package com.distribuidora.backend.repository;

import com.distribuidora.backend.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    List<ContactMessage> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);
}
