package com.example.ragingestor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {
    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    Optional<ConversationMessage> findByIdAndConversationId(UUID id, UUID conversationId);
}
