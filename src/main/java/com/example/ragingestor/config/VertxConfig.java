package com.example.ragingestor.config;

import com.example.ragingestor.ingestion.IngestionVerticle;
import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Vert.x runs *inside* the Spring process (a Vertx instance is just another bean),
 * rather than as a second standalone HTTP server. Spring MVC + Spring Security stay
 * the single front door and the single place authorization is enforced; the
 * IngestionVerticle is only reached internally over the Vert.x event bus, from
 * already-authorized Spring controllers. This avoids re-implementing session/role
 * checks a second time on a separate Vert.x router.
 */
@Configuration
public class VertxConfig {

    private Vertx vertx;

    @Bean
    public Vertx vertx() {
        this.vertx = Vertx.vertx();
        registerCodecs(this.vertx);
        return this.vertx;
    }

    private void registerCodecs(Vertx vertx) {
        vertx.eventBus().registerDefaultCodec(CreateDocumentCommand.class,
                new JavaSerializationCodec<>(CreateDocumentCommand.class));
        vertx.eventBus().registerDefaultCodec(UpdateDocumentCommand.class,
                new JavaSerializationCodec<>(UpdateDocumentCommand.class));
        vertx.eventBus().registerDefaultCodec(DeleteDocumentCommand.class,
                new JavaSerializationCodec<>(DeleteDocumentCommand.class));
        vertx.eventBus().registerDefaultCodec(IngestionResult.class,
                new JavaSerializationCodec<>(IngestionResult.class));
    }

    @Bean
    public WebClient webClient(Vertx vertx) {
        return WebClient.create(vertx);
    }

    @EventListener(ContextRefreshedEvent.class)
    public void deployVerticles(ContextRefreshedEvent event) {
        IngestionVerticle verticle = event.getApplicationContext().getBean(IngestionVerticle.class);
        vertx.deployVerticle(verticle);
    }

    @PreDestroy
    public void shutdown() {
        if (vertx != null) {
            vertx.close();
        }
    }
}
