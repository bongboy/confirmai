package com.example.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.HierarchicalDocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class TextChunkingService {

    private static final Logger LOG = Logger.getLogger(TextChunkingService.class);
    private final HierarchicalDocumentSplitter splitter;

    public TextChunkingService(
            @ConfigProperty(name = "langchain4j.chunk.size", defaultValue = "50") int chunkSize,
            @ConfigProperty(name = "langchain4j.chunk.overlap", defaultValue = "10") int chunkOverlap
    ) {
        OpenAiTokenizer tokenizer = new OpenAiTokenizer();
        this.splitter = new DocumentBySentenceSplitter(chunkSize, chunkOverlap, tokenizer);
    }

    /**
     * Splits the input text into chunks hierarchically, first by paragraphs then by sentences, respecting token limits.
     * @param text The input text to chunk.
     * @return A list of TextSegment objects, each representing a chunk.
     */
    public List<TextSegment> chunkText(String text) {
        Document doc = Document.from(text);
        List<TextSegment> segments = splitter.split(doc);
        LOG.debug("Number of chunks: " + segments.size());
        return segments;
    }
}
