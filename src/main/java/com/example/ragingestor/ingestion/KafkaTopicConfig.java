package com.example.ragingestor.ingestion;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    public NewTopic documentUploadedTopic(@Value("${app.kafka.topics.uploaded}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic documentProcessedTopic(@Value("${app.kafka.topics.processed}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic documentFailedTopic(@Value("${app.kafka.topics.failed}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}
