package com.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedbackResponse {
    private double alignmentScore;
    private List<String> requirementMiss;
    private List<String> edgeCases;
    private CodeFeedback codeFeedback;
    private List<String> warnings;

    public FeedbackResponse() {
    }

    public FeedbackResponse(double alignmentScore, List<String> requirementMiss,
                            List<String> edgeCases, CodeFeedback codeFeedback,
                            List<String> warnings) {
        this.alignmentScore = alignmentScore;
        this.requirementMiss = requirementMiss;
        this.edgeCases = edgeCases;
        this.codeFeedback = codeFeedback;
        this.warnings = warnings;
    }

    @JsonProperty("alignmentScore")
    public double getAlignmentScore() {
        return alignmentScore;
    }

    public void setAlignmentScore(double alignmentScore) {
        this.alignmentScore = alignmentScore;
    }

    @JsonProperty("requirementMiss")
    public List<String> getRequirementMiss() {
        return requirementMiss;
    }

    public void setRequirementMiss(List<String> requirementMiss) {
        this.requirementMiss = requirementMiss;
    }

    @JsonProperty("edgeCases")
    public List<String> getEdgeCases() {
        return edgeCases;
    }

    public void setEdgeCases(List<String> edgeCases) {
        this.edgeCases = edgeCases;
    }

    @JsonProperty("codeFeedback")
    public CodeFeedback getCodeFeedback() {
        return codeFeedback;
    }

    public void setCodeFeedback(CodeFeedback codeFeedback) {
        this.codeFeedback = codeFeedback;
    }

    @JsonProperty("warnings")
    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public static class CodeFeedback {
        private String quality;
        private List<String> optimizations;
        private String standards;

        public CodeFeedback() {
        }

        public CodeFeedback(String quality, List<String> optimizations, String standards) {
            this.quality = quality;
            this.optimizations = optimizations;
            this.standards = standards;
        }

        @JsonProperty("quality")
        public String getQuality() {
            return quality;
        }

        public void setQuality(String quality) {
            this.quality = quality;
        }

        @JsonProperty("optimizations")
        public List<String> getOptimizations() {
            return optimizations;
        }

        public void setOptimizations(List<String> optimizations) {
            this.optimizations = optimizations;
        }

        @JsonProperty("standards")
        public String getStandards() {
            return standards;
        }

        public void setStandards(String standards) {
            this.standards = standards;
        }
    }
}