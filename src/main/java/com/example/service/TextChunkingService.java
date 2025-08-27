package com.example.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class TextChunkingService {

    private final DocumentByParagraphSplitter splitter;

    public TextChunkingService(
            @ConfigProperty(name = "langchain4j.chunk.size", defaultValue = "500") int chunkSize,
            @ConfigProperty(name = "langchain4j.chunk.overlap", defaultValue = "50") int chunkOverlap
    ) {
        // Token-based chunking using OpenAI tokenizer
        OpenAiTokenizer tokenizer = new OpenAiTokenizer();

        this.splitter = new DocumentByParagraphSplitter(
                chunkSize,
                chunkOverlap,
                tokenizer
        );
    }

    public List<TextSegment> chunkText(String text) {
        Document doc = Document.from(text);
        return splitter.split(doc);
    }
}
