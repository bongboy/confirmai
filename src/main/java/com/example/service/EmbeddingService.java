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
import io.quarkus.redis.datasource.ScanArgs;
import io.quarkus.redis.datasource.keys.KeyScanCursor;
import io.vertx.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;

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

    private static final Logger LOG = Logger.getLogger(EmbeddingService.class);

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

                // Store in Redis vector index
                embeddingStore.add(embedding, segment);

                // Also store in Redis hash
                String chunkKey = "requirements:" + id + ":chunk:" + i;
                redisClient.hset(List.of(chunkKey, "id", id));
                redisClient.hset(List.of(chunkKey, "chunkIndex", String.valueOf(i)));
                redisClient.hset(List.of(chunkKey, "content", segment.text()));
                redisClient.hset(List.of(chunkKey, "metadata", requirement.getMetadata()));
            }

            // Store requirement metadata (id, total chunks, global metadata)
            String metaKey = "requirements:" + id + ":meta";
            redisClient.hset(List.of(metaKey, "id", id));
            redisClient.hset(List.of(metaKey, "chunkCount", String.valueOf(chunks.size())));
            redisClient.hset(List.of(metaKey, "metadata", requirement.getMetadata()));

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
            // Read meta key to get chunk count
            String metaKey = "requirements:" + id + ":meta";
            Response metaResp = redisClient.hgetall(metaKey);
            Map<String, String> metaFields = toMap(metaResp);

            if (metaFields.isEmpty()) {
                return null; // no requirement found
            }

            int chunkCount = Integer.parseInt(metaFields.getOrDefault("chunkCount", "0"));
            String metadata = metaFields.get("metadata");

            StringBuilder contentBuilder = new StringBuilder();

            for (int i = 0; i < chunkCount; i++) {
                String key = "requirements:" + id + ":chunk:" + i;
                Response chunkResp = redisClient.hgetall(key);
                Map<String, String> fields = toMap(chunkResp);

                if (fields.isEmpty()) continue;

                String chunkContent = fields.get("content");
                if (chunkContent != null) {
                    contentBuilder.append(chunkContent).append(" ");
                }
            }

            Requirement requirement = new Requirement();
            requirement.setId(id);
            requirement.setContent(contentBuilder.toString().trim());
            requirement.setMetadata(metadata);

            return requirement;

        } catch (Exception e) {
            LOG.error("Error fetching requirement by id: " + id, e);
            return null;
        }
    }

    private String stripQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private Map<String, String> toMap(Response response) {
        Map<String, String> map = new HashMap<>();

        if (response == null) {
            return map;
        }

        try {
            // Iterate children if available
            for (Response child : response) {
                if (child.size() == 2) {
                    // Likely a key/value pair
                    String key = child.get(0).toString();
                    Response valResp = child.get(1);

                    // If value is a nested multi, recursively convert
                    String value;
                    if (valResp.size() > 0) {
                        value = toMap(valResp).toString(); // nested map as string
                    } else {
                        value = valResp.toString();
                    }

                    map.put(key, value);
                }
            }

            // If still empty and no children, treat as single value
            if (map.isEmpty() && response.size() == 0) {
                map.put("value", response.toString());
            }

        } catch (UnsupportedOperationException e) {
            // For single-value responses that cannot be iterated
            map.put("value", response.toString());
        }

        return map;
    }
}
