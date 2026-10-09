package com.example.ragingestor.rag;

import com.example.ragingestor.security.AppPrincipal;
import com.example.ragingestor.security.CorrelationIdFilter;
import com.example.ragingestor.security.RedisRateLimitService;
import com.example.ragingestor.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/conversations")
@PreAuthorize("hasRole('READ')")
@Tag(name = "Conversations", description = "Per-user conversation history and streaming RAG responses")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private final ConversationService conversationService;
    private final RagOrchestrator ragOrchestrator;
    private final RedisRateLimitService rateLimitService;
    private final Executor sseTaskExecutor;

    public ConversationController(ConversationService conversationService, RagOrchestrator ragOrchestrator,
                                  RedisRateLimitService rateLimitService,
                                  @Qualifier("sseTaskExecutor") Executor sseTaskExecutor) {
        this.conversationService = conversationService;
        this.ragOrchestrator = ragOrchestrator;
        this.rateLimitService = rateLimitService;
        this.sseTaskExecutor = sseTaskExecutor;
    }

    public record MessageRequest(@NotBlank @Size(max = 4000) String question,
                                @Size(max = 120) String department,
                                @Size(max = 80) String documentType,
                                @Size(max = 10) List<@Pattern(regexp = "[A-Za-z0-9 _-]{1,64}") String> tags,
                                UUID ownerId,
                                UUID retryMessageId) {}

    @PostMapping
    @Operation(summary = "Create a conversation")
    @SecurityRequirement(name = "csrfToken")
    public ConversationService.ConversationSummary create(@AuthenticationPrincipal AppPrincipal principal) {
        return conversationService.create(principal.getUserId());
    }

    @GetMapping
    @Operation(summary = "List the current user's conversations")
    public List<ConversationService.ConversationSummary> list(@AuthenticationPrincipal AppPrincipal principal) {
        return conversationService.list(principal.getUserId());
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get a conversation and its messages")
    public ConversationService.ConversationDetail get(@PathVariable UUID conversationId,
                                                        @AuthenticationPrincipal AppPrincipal principal) {
        return conversationService.get(conversationId, principal.getUserId());
    }

    @PostMapping(path = "/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream an answer, citations, and progress events using server-sent events")
    @SecurityRequirement(name = "csrfToken")
    public SseEmitter sendMessage(@PathVariable UUID conversationId, @Valid @RequestBody MessageRequest request,
                                  @AuthenticationPrincipal AppPrincipal principal) {
        rateLimitService.checkConversationMessage(principal.getUserId());
        UUID userMessageId = request.retryMessageId() == null
                ? conversationService.addUserMessage(conversationId, principal.getUserId(), request.question().trim())
                : conversationService.requireRetryableUserMessage(conversationId, principal.getUserId(),
                        request.retryMessageId());
        RetrievalFilters filters = new RetrievalFilters(request.department(), request.documentType(),
                request.tags(), request.ownerId());
        SseEmitter emitter = new SseEmitter(180_000L);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        sseTaskExecutor.execute(() -> streamResponse(emitter, conversationId, userMessageId, principal.getUserId(),
                principal.getRole(), request.question().trim(), filters, correlationId));
        return emitter;
    }

    private void streamResponse(SseEmitter emitter, UUID conversationId, UUID userMessageId, UUID userId,
                                Role role, String question, RetrievalFilters filters, String correlationId) {
        if (correlationId != null) MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        try {
            send(emitter, "status", new StatusEvent("searching", userMessageId.toString()));
            List<RagOrchestrator.ConversationTurn> history = conversationService.recentHistory(
                    conversationId, userId, userMessageId, 8);
            RagOrchestrator.RagAnswer answer = ragOrchestrator.streamAnswer(question, role, filters, history,
                    status -> send(emitter, "status", new StatusEvent(status, null)),
                    token -> send(emitter, "token", new TokenEvent(token)),
                    source -> send(emitter, "source", source));
            conversationService.addAssistantMessage(conversationId, userId, answer);
            send(emitter, "completion", answer);
            emitter.complete();
        } catch (RuntimeException failure) {
            log.error("Conversation response failed for conversation {}", conversationId, failure);
            try {
                send(emitter, "error", new ErrorEvent("Response generation failed."));
            } catch (SseDeliveryException disconnected) {
                log.debug("SSE client disconnected for conversation {}", conversationId);
            }
            emitter.complete();
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            throw new SseDeliveryException(e);
        }
    }

    public record StatusEvent(String status, String userMessageId) {}
    public record TokenEvent(String token) {}
    public record ErrorEvent(String message) {}

    private static final class SseDeliveryException extends RuntimeException {
        private SseDeliveryException(IOException cause) {
            super(cause);
        }
    }
}
