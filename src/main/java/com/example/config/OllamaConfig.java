package com.example.config;

import com.example.qualifier.CodingModel;
import com.example.qualifier.ReviewModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OllamaConfig {

    @ConfigProperty(name = "ollama.base.url")
    String baseUrl;

    @ConfigProperty(name = "ollama.embedding.model")
    String embeddingModelName;

    @ConfigProperty(name = "ollama.coding.model")
    String codingModelName;

    @ConfigProperty(name = "ollama.review.model")
    String reviewModelName;

    @Produces
    @ApplicationScoped
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .build();
    }

    @Produces
    @ApplicationScoped
    @CodingModel
    public ChatLanguageModel codingModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(codingModelName)
                .build();
    }

    @Produces
    @ApplicationScoped
    @ReviewModel
    public ChatLanguageModel reviewModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(reviewModelName)
                .build();
    }
}