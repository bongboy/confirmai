package com.example.service;

import com.example.dto.CodeImplementation;
import com.example.dto.FeedbackResponse;
import com.example.dto.Requirement;
import com.example.qualifier.CodingModel;
import com.example.qualifier.ReviewModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class CodeAnalysisService {

    private static final Logger LOG = Logger.getLogger(CodeAnalysisService.class);

    @Inject
    @CodingModel
    ChatLanguageModel codingModel;

    @Inject
    @ReviewModel
    ChatLanguageModel reviewModel;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.supported-languages")
    List<String> supportedLanguages;

    //private static final Pattern UC_ID_PATTERN = Pattern.compile("UC-(\\w+)");
    //private static final Pattern UC_ID_PATTERN = Pattern.compile("UC-([^:]+):");
    private static final Pattern UC_ID_PATTERN = Pattern.compile("UC-([A-Za-z0-9_-]+):?");
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    public String extractRequirementId(CodeImplementation implementation) {
        // First try to extract from comments
        String code = implementation.getCode();
        Matcher matcher = UC_ID_PATTERN.matcher(code);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // If not found in comments, try semantic search
        var matches = embeddingService.findSimilarRequirements(code, 3);
        if (!matches.isEmpty()) {
            // Extract ID from the first match (most relevant)
            String embeddedText = matches.get(0).embedded().text();
            // Parse the embedded text to extract ID
            return extractIdFromEmbeddedText(embeddedText);
        }

        return null;
    }

    private String extractIdFromEmbeddedText(String embeddedText) {
        // Simple extraction - in a real implementation, you'd want a more robust method
        if (embeddedText.contains("ID:")) {
            String[] parts = embeddedText.split("ID:");
            if (parts.length > 1) {
                return parts[1].split(" ")[0].trim();
            }
        }
        return null;
    }

    public FeedbackResponse analyzeCode(CodeImplementation implementation, Requirement requirement) {
        // Initial analysis with coding model
        String initialAnalysis = codingModel.generate("""
    Analyze this %s code for quality, optimizations, and standards:
    
    %s
    
    Provide a concise analysis focusing on code quality, potential optimizations, and adherence to coding standards.
    """.formatted(implementation.getLanguage(), implementation.getCode()));

        // Comprehensive review with review model
        String reviewPrompt = """
    Comprehensive code review task:
    
    REQUIREMENT: %s
    
    CODE:
    %s
    
    LANGUAGE: %s
    
    INITIAL ANALYSIS: %s
    
    Please provide a comprehensive review with the following structure:
    1. Alignment Score (0-10): A numerical score indicating how well the code fulfills the requirement.
    Also consider while scoring if requirement is Completely, Partially or not met.
    Penalize if requirement completely missed.
    2. Requirement Misses: List any aspects of the requirement that are not addressed
    Classify if the requirement is Completely, Partially or not met. Highlight all deviation.
    3. Edge Cases: List any edge cases that should be handled
    4. Code Feedback: 
       - Quality: Comments on code quality
       - Optimizations: Specific optimization suggestions
       - Standards: Adherence to coding standards
    5. Warnings: Any other warnings or concerns
    
    Please format your response as a JSON object with the following structure:
    {
      "alignmentScore": 8.5,
      "requirementMiss": ["item1", "item2"],
      "edgeCases": ["case1", "case2"],
      "codeFeedback": {
        "quality": "comments on quality",
        "optimizations": ["opt1", "opt2"],
        "standards": "comments on standards"
      },
      "warnings": ["warning1", "warning2"]
    }
    """.formatted(
                requirement.getContent(),
                implementation.getCode(),
                implementation.getLanguage(),
                initialAnalysis
        );

        String comprehensiveReview = reviewModel.generate(reviewPrompt);

        // Extract JSON from the response
        String jsonResponse = extractJsonFromResponse(comprehensiveReview);

        try {
            return objectMapper.readValue(jsonResponse, FeedbackResponse.class);
        } catch (JsonProcessingException e) {
            // Fallback to parsing if JSON parsing fails
            return parseReviewResponse(comprehensiveReview);
        }
    }

    private String extractJsonFromResponse(String response) {
        Matcher matcher = JSON_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group();
        }
        return response; // Return the whole response if no JSON found
    }

    private FeedbackResponse parseReviewResponse(String reviewText) {
        // Fallback parsing if JSON parsing fails
        FeedbackResponse response = new FeedbackResponse();
        FeedbackResponse.CodeFeedback codeFeedback = new FeedbackResponse.CodeFeedback();

        // Extract alignment score
        if (reviewText.contains("Alignment Score")) {
            String[] parts = reviewText.split("Alignment Score");
            if (parts.length > 1) {
                String scorePart = parts[1].split("\n")[0];
                scorePart = scorePart.replaceAll("[^0-9.]", "");
                try {
                    response.setAlignmentScore(Double.parseDouble(scorePart));
                } catch (NumberFormatException e) {
                    response.setAlignmentScore(5.0); // Default score
                }
            }
        }

        // Extract requirement misses
        if (reviewText.contains("Requirement Misses")) {
            String[] parts = reviewText.split("Requirement Misses");
            if (parts.length > 1) {
                String missesPart = parts[1].split("Edge Cases")[0];
                response.setRequirementMiss(extractListItems(missesPart));
            }
        }

        // Extract edge cases
        if (reviewText.contains("Edge Cases")) {
            String[] parts = reviewText.split("Edge Cases");
            if (parts.length > 1) {
                String casesPart = parts[1].split("Code Feedback")[0];
                response.setEdgeCases(extractListItems(casesPart));
            }
        }

        // Extract code feedback
        if (reviewText.contains("Code Feedback")) {
            String[] parts = reviewText.split("Code Feedback");
            if (parts.length > 1) {
                String feedbackPart = parts[1].split("Warnings")[0];

                // Extract quality
                if (feedbackPart.contains("Quality:")) {
                    String[] qualityParts = feedbackPart.split("Quality:");
                    if (qualityParts.length > 1) {
                        String quality = qualityParts[1].split("Optimizations:")[0].trim();
                        codeFeedback.setQuality(quality);
                    }
                }

                // Extract optimizations
                if (feedbackPart.contains("Optimizations:")) {
                    String[] optParts = feedbackPart.split("Optimizations:");
                    if (optParts.length > 1) {
                        String optimizations = optParts[1].split("Standards:")[0];
                        codeFeedback.setOptimizations(extractListItems(optimizations));
                    }
                }

                // Extract standards
                if (feedbackPart.contains("Standards:")) {
                    String[] stdParts = feedbackPart.split("Standards:");
                    if (stdParts.length > 1) {
                        String standards = stdParts[1].trim();
                        codeFeedback.setStandards(standards);
                    }
                }
            }
        }

        // Extract warnings
        if (reviewText.contains("Warnings")) {
            String[] parts = reviewText.split("Warnings");
            if (parts.length > 1) {
                String warningsPart = parts[1];
                response.setWarnings(extractListItems(warningsPart));
            }
        }

        response.setCodeFeedback(codeFeedback);
        return response;
    }

    private List<String> extractListItems(String text) {
        List<String> items = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("-") || line.startsWith("*")) {
                items.add(line.substring(1).trim());
            } else if (line.matches("\\d+\\.\\s+.*")) {
                items.add(line.replaceFirst("\\d+\\.\\s+", ""));
            } else if (!line.isEmpty() && !line.contains(":")) {
                items.add(line);
            }
        }
        return items;
    }

    public void performAstAnalysis(CodeImplementation implementation) {
        String language = implementation.getLanguage().toLowerCase();

        switch (language) {
            case "java":
                analyzeJavaAst(implementation.getCode());
                break;
            case "python":
                analyzePythonAst(implementation.getCode());
                break;
            case "javascript":
            case "typescript":
                analyzeJavaScriptAst(implementation.getCode());
                break;
            case "cpp":
            case "c":
                analyzeCppAst(implementation.getCode());
                break;
            case "go":
                analyzeGoAst(implementation.getCode());
                break;
            case "rust":
                analyzeRustAst(implementation.getCode());
                break;
            default:
                LOG.error("Unsupported language: " + language);
        }
    }

    private void analyzeJavaAst(String code) {
        try {
            ASTParser parser = ASTParser.newParser(AST.JLS21);
            parser.setSource(code.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);

            Map<String, String> options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
            parser.setCompilerOptions(options);

            CompilationUnit cu = (CompilationUnit) parser.createAST(null);

            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration node) {
                    LOG.info("Method: " + node.getName());
                    return super.visit(node);
                }

                @Override
                public boolean visit(VariableDeclarationFragment node) {
                    LOG.info("Variable: " + node.getName());
                    return super.visit(node);
                }
            });
        } catch (Exception e) {
            LOG.error("Java AST analysis failed: " + e.getMessage());
        }
    }

    private void analyzePythonAst(String code) {
        LOG.info("Python AST analysis: Basic structure analysis");
        // Count functions and classes as a simple analysis
        long functionCount = code.lines().filter(line -> line.trim().startsWith("def ")).count();
        long classCount = code.lines().filter(line -> line.trim().startsWith("class ")).count();
        LOG.info("Functions: " + functionCount + ", Classes: " + classCount);
    }

    private void analyzeJavaScriptAst(String code) {
        LOG.info("JavaScript AST analysis: Basic structure analysis");
        // Count functions and classes as a simple analysis
        long functionCount = code.lines().filter(line -> line.trim().startsWith("function ") || line.contains("=>")).count();
        long classCount = code.lines().filter(line -> line.trim().startsWith("class ")).count();
        LOG.info("Functions: " + functionCount + ", Classes: " + classCount);
    }

    private void analyzeCppAst(String code) {
        LOG.info("C++ AST analysis: Basic structure analysis");
        // Count functions and classes as a simple analysis
        long functionCount = code.lines().filter(line -> line.contains("(") && line.contains(")") && line.contains("{")).count();
        long classCount = code.lines().filter(line -> line.trim().startsWith("class ")).count();
        LOG.info("Functions: " + functionCount + ", Classes: " + classCount);
    }

    private void analyzeGoAst(String code) {
        LOG.info("Go AST analysis: Basic structure analysis");
        // Count functions and structs as a simple analysis
        long functionCount = code.lines().filter(line -> line.trim().startsWith("func ")).count();
        long structCount = code.lines().filter(line -> line.trim().startsWith("type ") && line.contains("struct")).count();
        LOG.info("Functions: " + functionCount + ", Structs: " + structCount);
    }

    private void analyzeRustAst(String code) {
        LOG.info("Rust AST analysis: Basic structure analysis");
        // Count functions and structs as a simple analysis
        long functionCount = code.lines().filter(line -> line.trim().startsWith("fn ")).count();
        long structCount = code.lines().filter(line -> line.trim().startsWith("struct ")).count();
        LOG.info("Functions: " + functionCount + ", Structs: " + structCount);
    }
}