package com.example.service;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Inject
    RedisDataSource redisDataSource;

    @ConfigProperty(name = "ollama.base.url")
    String ollamaBaseUrl;

    @ConfigProperty(name = "ollama.embedding.model")
    String embeddingModelName;

    void onStart(@Observes StartupEvent ev) {
        LOG.info("=== Vector Search API Starting ===");

        // Check Redis connection
        try {
            String result = String.valueOf(redisDataSource.execute("PING"));
            if ("PONG".equals(result)) {
                LOG.info("✅ Redis connection successful");
            } else {
                LOG.warn("⚠️ Redis connection issue - expected PONG, got: " + result);
            }
        } catch (Exception e) {
            LOG.error("❌ Redis connection failed: " + e.getMessage());
        }

        // Log Ollama configuration
        LOG.info("📡 Ollama Base URL: " + ollamaBaseUrl);
        LOG.info("🧠 Embedding Model: " + embeddingModelName);

        LOG.info("🚀 Vector Search API ready!");
        LOG.info("📖 Available endpoints:");
        LOG.info("   - Health: GET /api/v1/documents/health");
        LOG.info("   - Add Document: POST /api/v1/documents");
        LOG.info("   - Search: POST /api/v1/documents/search");
        LOG.info("   - Get All: GET /api/v1/documents");
        LOG.info("   - Delete: DELETE /api/v1/documents/{id}");
    }
}