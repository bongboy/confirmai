package com.example.service;

import com.example.dto.Requirement;
import dev.langchain4j.data.document.Metadata;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class EmbeddingService {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    RedisClient redisClient;

    @Inject
    TextChunkingService textChunkingService;

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
            // Ensure ID exists
            String id = requirement.getId() != null ? requirement.getId() : UUID.randomUUID().toString();

            // Split into chunks
            List<TextSegment> chunks = textChunkingService.chunkText(requirement.getContent());

            for (int i = 0; i < chunks.size(); i++) {

                // build metadata
                Map<String, String> metadataMap = new HashMap<>();
                metadataMap.put("requirementId", id);
                metadataMap.put("chunkIndex", String.valueOf(i));
                metadataMap.put("metadata", requirement.getMetadata());

                Metadata metadata = Metadata.from(metadataMap);

                TextSegment segment = TextSegment.from(
                        chunks.get(i).text(), metadata
                );

                // Embed each chunk
                Embedding embedding = embeddingModel.embed(segment.text()).content();

                // Store in Redis vector index (embedding + metadata travels together)
                embeddingStore.add(embedding, segment);

                // (Optional) also store in regular Redis for quick lookup
                String chunkKey = "requirements:" + id + ":chunk:" + i;
                redisClient.hset(List.of(chunkKey, "id", id));
                redisClient.hset(List.of(chunkKey, "chunkIndex", String.valueOf(i)));
                redisClient.hset(List.of(chunkKey, "content", segment.text()));
                redisClient.hset(List.of(chunkKey, "metadata", requirement.getMetadata()));
            }

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
            // Only fetch chunk 0 for high-level requirement text
            Response idResponse = redisClient.hget("requirements:" + id + ":0", "id");
            Response contentResponse = redisClient.hget("requirements:" + id + ":0", "content");
            Response metadataResponse = redisClient.hget("requirements:" + id + ":0", "metadata");

            if (idResponse == null || contentResponse == null || metadataResponse == null) {
                return null;
            }

            return new Requirement(
                    stripQuotes(idResponse.toString()),
                    stripQuotes(contentResponse.toString()),
                    stripQuotes(metadataResponse.toString())
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to find requirement by ID: " + e.getMessage(), e);
        }
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
