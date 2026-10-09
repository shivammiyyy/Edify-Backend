package com.example.ragingestor.integration;

import com.example.ragingestor.domain.ChunkVectorRepository;
import com.example.ragingestor.rag.RetrievalFilters;
import com.example.ragingestor.security.Role;
import io.lettuce.core.RedisClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class InfrastructureIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Test
    void persistsVectorsCachesValuesAndDeliversKafkaEvents() throws Exception {
        verifyPostgresRetrievalAuthorization();

        RedisClient redis = RedisClient.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        try (var connection = redis.connect()) {
            String key = "edify:integration:" + UUID.randomUUID();
            connection.sync().set(key, "cached");
            assertEquals("cached", connection.sync().get(key));
        } finally {
            redis.shutdown();
        }

        String topic = "edify.integration." + UUID.randomUUID();
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }

        Properties consumerConfig = new Properties();
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "edify-integration-" + UUID.randomUUID());
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Properties producerConfig = new Properties();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerConfig)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            producer.send(new ProducerRecord<>(topic, "document-1", "uploaded")).get();

            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            boolean received = false;
            while (!received && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if ("uploaded".equals(record.value())) {
                        received = true;
                        break;
                    }
                }
            }
            assertTrue(received, "Kafka should deliver the published event");
        }
    }

    private void verifyPostgresRetrievalAuthorization() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ChunkVectorRepository repository = new ChunkVectorRepository(jdbc);

        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        jdbc.update("insert into app_user (id, email, role) values (?, ?, 'READ')",
                ownerId, ownerId + "@integration.test");
        jdbc.update("insert into folder (id, name, created_by, min_role_required) values (?, ?, ?, 'CUSTODIAN')",
                folderId, "Restricted", ownerId);
        jdbc.update("""
                insert into document_meta
                    (id, filename, uploaded_by, folder_id, min_role_required, status)
                values (?, 'restricted.txt', ?, ?, 'READ', 'READY')
                """, documentId, ownerId, folderId);
        jdbc.update("""
                insert into document_chunk
                    (id, document_id, chunk_index, content, min_role_required, created_by)
                values (?, ?, 0, 'confidential operations procedure', 'READ', ?)
                """, chunkId, documentId, ownerId);
        float[] embedding = new float[1024];
        embedding[0] = 1f;
        repository.upsertEmbedding(chunkId, embedding);

        assertTrue(repository.vectorTopK(embedding, Role.READ, RetrievalFilters.empty(), 6).isEmpty());
        assertTrue(repository.keywordTopK("confidential operations", Role.READ,
                RetrievalFilters.empty(), 6).isEmpty());
        assertEquals(1, repository.vectorTopK(embedding, Role.CUSTODIAN, RetrievalFilters.empty(), 6).size());
        assertEquals(1, repository.keywordTopK("confidential operations", Role.CUSTODIAN,
                RetrievalFilters.empty(), 6).size());
    }
}
