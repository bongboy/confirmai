package com.example.service;

import com.example.dto.Requirement;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EmbeddingService {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    RedisClient redisClient;

    @ConfigProperty(name = "redis.vector.index")
    String indexName;

    @ConfigProperty(name = "redis.vector.dimension")
    int dimension;

    @ConfigProperty(name = "quarkus.redis.hosts")
    String redisHost;

    private EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    void initEmbeddingStore() {
        try {
            // Extract host and port from the Redis URL
            String host = redisHost.replace("redis://", "").split(":")[0];
            int port = Integer.parseInt(redisHost.replace("redis://", "").split(":")[1]);

            this.embeddingStore = RedisEmbeddingStore.builder()
                    .host(host)
                    .port(port)
                    .indexName(indexName)
                    .dimension(dimension)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Redis embedding store: " + e.getMessage(), e);
        }
    }

    public String storeRequirement(Requirement requirement) {
        try {
            String text = "ID: " + requirement.getId() + " CONTENT: " + requirement.getContent() + " METADATA: " + requirement.getMetadata();
            Embedding embedding = embeddingModel.embed(text).content();
            TextSegment segment = TextSegment.from(text);

            String id = requirement.getId() != null ? requirement.getId() : UUID.randomUUID().toString();

            // Use the new add method signature
            embeddingStore.add(embedding, segment);

            // Store in regular Redis using hset with proper arguments
            redisClient.hset(List.of("requirements:" + id, "id", id));
            redisClient.hset(List.of("requirements:" + id, "content", requirement.getContent()));
            redisClient.hset(List.of("requirements:" + id, "metadata", requirement.getMetadata()));

            return id;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store requirement: " + e.getMessage(), e);
        }
    }

    public List<EmbeddingMatch<TextSegment>> findSimilarRequirements(String text, int maxResults) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(text).content();
            return embeddingStore.findRelevant(queryEmbedding, maxResults, 0.5);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find similar requirements: " + e.getMessage(), e);
        }
    }

    public Requirement findRequirementById(String id) {
        try {
            Response idResponse = redisClient.hget("requirements:" + id, "id");
            Response contentResponse = redisClient.hget("requirements:" + id, "content");
            Response metadataResponse = redisClient.hget("requirements:" + id, "metadata");

            if (idResponse == null || contentResponse == null || metadataResponse == null) {
                return null;
            }

            // Convert Response objects to strings
            String idStr = idResponse.toString();
            String contentStr = contentResponse.toString();
            String metadataStr = metadataResponse.toString();

            // Remove quotes if present
            if (idStr.startsWith("\"") && idStr.endsWith("\"")) {
                idStr = idStr.substring(1, idStr.length() - 1);
            }
            if (contentStr.startsWith("\"") && contentStr.endsWith("\"")) {
                contentStr = contentStr.substring(1, contentStr.length() - 1);
            }
            if (metadataStr.startsWith("\"") && metadataStr.endsWith("\"")) {
                metadataStr = metadataStr.substring(1, metadataStr.length() - 1);
            }

            return new Requirement(idStr, contentStr, metadataStr);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find requirement by ID: " + e.getMessage(), e);
        }
    }
}