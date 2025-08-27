package com.example.dto;

public class RequirementChunk {
    private String requirementId;
    private String chunkText;
    private int chunkIndex;

    public RequirementChunk(String requirementId, String chunkText, int chunkIndex) {
        this.requirementId = requirementId;
        this.chunkText = chunkText;
        this.chunkIndex = chunkIndex;
    }

    public String getRequirementId() {
        return requirementId;
    }

    public String getChunkText() {
        return chunkText;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }
}
