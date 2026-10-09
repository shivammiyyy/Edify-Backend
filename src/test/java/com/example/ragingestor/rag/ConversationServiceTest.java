package com.example.ragingestor.rag;

import com.example.ragingestor.domain.Conversation;
import com.example.ragingestor.domain.ConversationMessage;
import com.example.ragingestor.domain.ConversationMessageRepository;
import com.example.ragingestor.domain.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationMessageRepository messages = mock(ConversationMessageRepository.class);
    private final ConversationService service = new ConversationService(conversations, messages, new ObjectMapper());

    @Test
    void savesMessagesAndUpdatesTitleOnlyForTheOwner() {
        UUID conversationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserId(ownerId);
        when(conversations.findByIdAndUserId(conversationId, ownerId)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> {
            ConversationMessage message = invocation.getArgument(0);
            message.setId(UUID.randomUUID());
            return message;
        });

        UUID messageId = service.addUserMessage(conversationId, ownerId, "What is the policy?");

        assertEquals("What is the policy?", conversation.getTitle());
        verify(messages).save(org.mockito.ArgumentMatchers.argThat(message ->
                message.getRole().equals("USER") && message.getContent().equals("What is the policy?")));
        org.junit.jupiter.api.Assertions.assertNotNull(messageId);
    }

    @Test
    void hidesConversationsOwnedByOtherUsers() {
        UUID conversationId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        when(conversations.findByIdAndUserId(conversationId, differentUserId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.get(conversationId, differentUserId));
        verify(messages, never()).findByConversationIdOrderByCreatedAtAsc(any());
    }

    @Test
    void permitsRetryOnlyForTheLatestUnansweredOwnedUserMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        ConversationMessage userMessage = new ConversationMessage();
        userMessage.setId(messageId);
        userMessage.setConversationId(conversationId);
        userMessage.setRole("USER");
        when(conversations.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(messages.findByIdAndConversationId(messageId, conversationId)).thenReturn(Optional.of(userMessage));
        when(messages.findByConversationIdOrderByCreatedAtAsc(conversationId)).thenReturn(java.util.List.of(userMessage));

        assertEquals(messageId, service.requireRetryableUserMessage(conversationId, userId, messageId));

        ConversationMessage assistantMessage = new ConversationMessage();
        assistantMessage.setId(UUID.randomUUID());
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("ASSISTANT");
        when(messages.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(java.util.List.of(userMessage, assistantMessage));
        assertThrows(ResponseStatusException.class,
                () -> service.requireRetryableUserMessage(conversationId, userId, messageId));
    }
}
