package com.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CodeImplementation {
    private String code;
    private String language;
    private String requirementId;

    public CodeImplementation() {
    }

    public CodeImplementation(String code, String language, String requirementId) {
        this.code = code;
        this.language = language;
        this.requirementId = requirementId;
    }

    @JsonProperty("code")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonProperty("language")
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @JsonProperty("requirementId")
    public String getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(String requirementId) {
        this.requirementId = requirementId;
    }
}