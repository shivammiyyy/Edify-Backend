package com.example.ragingestor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_message")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sources_json", nullable = false, columnDefinition = "text")
    private String sourcesJson = "[]";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
