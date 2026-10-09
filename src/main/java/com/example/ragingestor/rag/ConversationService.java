package com.example.ragingestor.rag;

import com.example.ragingestor.domain.Conversation;
import com.example.ragingestor.domain.ConversationMessage;
import com.example.ragingestor.domain.ConversationMessageRepository;
import com.example.ragingestor.domain.ConversationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private static final TypeReference<List<RagOrchestrator.SourceCitation>> SOURCES_TYPE = new TypeReference<>() {};
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationRepository conversations, ConversationMessageRepository messages,
                               ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.messages = messages;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConversationSummary create(UUID userId) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation = conversations.save(conversation);
        return summary(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(UUID userId) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ConversationService::summary).toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(UUID conversationId, UUID userId) {
        Conversation conversation = ownedConversation(conversationId, userId);
        return new ConversationDetail(summary(conversation), messages.findByConversationIdOrderByCreatedAtAsc(
                conversationId).stream().map(this::messageView).toList());
    }

    @Transactional
    public UUID addUserMessage(UUID conversationId, UUID userId, String content) {
        Conversation conversation = ownedConversation(conversationId, userId);
        if ("New conversation".equals(conversation.getTitle())) {
            conversation.setTitle(content.length() > 80 ? content.substring(0, 77) + "..." : content);
        }
        conversation.setUpdatedAt(Instant.now());
        conversations.save(conversation);
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setRole("USER");
        message.setContent(content);
        return messages.save(message).getId();
    }

    @Transactional(readOnly = true)
    public UUID requireRetryableUserMessage(UUID conversationId, UUID userId, UUID messageId) {
        ownedConversation(conversationId, userId);
        ConversationMessage message = messages.findByIdAndConversationId(messageId, conversationId)
                .filter(item -> item.getRole().equals("USER"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        List<ConversationMessage> history = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (history.isEmpty() || !history.get(history.size() - 1).getId().equals(message.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only the latest unanswered message can be retried");
        }
        return message.getId();
    }

    @Transactional(readOnly = true)
    public List<RagOrchestrator.ConversationTurn> recentHistory(UUID conversationId, UUID userId,
                                                                 UUID excludedMessageId, int maxMessages) {
        ownedConversation(conversationId, userId);
        List<ConversationMessage> history = messages.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .filter(message -> !message.getId().equals(excludedMessageId)).toList();
        int fromIndex = Math.max(0, history.size() - maxMessages);
        return history.subList(fromIndex, history.size()).stream()
                .map(message -> new RagOrchestrator.ConversationTurn(message.getRole(), message.getContent()))
                .toList();
    }

    @Transactional
    public void addAssistantMessage(UUID conversationId, UUID userId, RagOrchestrator.RagAnswer answer) {
        Conversation conversation = ownedConversation(conversationId, userId);
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setRole("ASSISTANT");
        message.setContent(answer.answer());
        try {
            message.setSourcesJson(objectMapper.writeValueAsString(answer.sources()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize conversation citations", e);
        }
        messages.save(message);
        conversation.setUpdatedAt(Instant.now());
        conversations.save(conversation);
    }

    private Conversation ownedConversation(UUID conversationId, UUID userId) {
        return conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private MessageView messageView(ConversationMessage message) {
        try {
            return new MessageView(message.getId(), message.getRole(), message.getContent(),
                    message.getCreatedAt(), objectMapper.readValue(message.getSourcesJson(), SOURCES_TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read conversation citations", e);
        }
    }

    private static ConversationSummary summary(Conversation conversation) {
        return new ConversationSummary(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    public record ConversationSummary(UUID id, String title, Instant createdAt, Instant updatedAt) {}
    public record MessageView(UUID id, String role, String content, Instant createdAt,
                              List<RagOrchestrator.SourceCitation> sources) {}
    public record ConversationDetail(ConversationSummary conversation, List<MessageView> messages) {}
}
