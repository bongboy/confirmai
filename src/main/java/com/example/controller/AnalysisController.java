package com.example.controller;

import com.example.dto.CodeImplementation;
import com.example.dto.FeedbackResponse;
import com.example.dto.Requirement;
import com.example.service.CodeAnalysisService;
import com.example.service.EmbeddingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnalysisController {

    @Inject
    EmbeddingService embeddingService;

    @Inject
    CodeAnalysisService codeAnalysisService;

    @ConfigProperty(name = "app.max-code-size")
    int maxCodeSize;

    @ConfigProperty(name = "app.supported-languages")
    String supportedLanguages;

    @POST
    @Path("/requirements")
    public Response storeRequirement(Requirement requirement) {
        try {
            if (requirement.getContent() == null || requirement.getContent().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Requirement content cannot be empty\"}")
                        .build();
            }

            if (requirement.getMetadata() == null ||
                    (!requirement.getMetadata().equals("Req") && !requirement.getMetadata().equals("Def"))) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Metadata must be either 'Req' or 'Def'\"}")
                        .build();
            }

            String id = embeddingService.storeRequirement(requirement);
            return Response.ok().entity("{\"id\": \"" + id + "\"}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to store requirement: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    @POST
    @Path("/implementations")
    public Response analyzeImplementation(CodeImplementation implementation) {
        try {
            // Validate code size
            if (implementation.getCode() == null || implementation.getCode().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Code cannot be empty\"}")
                        .build();
            }

            if (implementation.getCode().length() > maxCodeSize) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Code exceeds maximum size limit of " + maxCodeSize + " characters\"}")
                        .build();
            }

            // Validate language
            if (implementation.getLanguage() == null || implementation.getLanguage().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Language must be specified\"}")
                        .build();
            }

            String language = implementation.getLanguage().toLowerCase();
            if (!supportedLanguages.contains(language)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Unsupported language: " + implementation.getLanguage() +
                                ". Supported languages: " + supportedLanguages + "\"}")
                        .build();
            }

            // Extract or find requirement ID
            String requirementId = implementation.getRequirementId();
            if (requirementId == null) {
                requirementId = codeAnalysisService.extractRequirementId(implementation);
            }

            if (requirementId == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"Could not identify requirement for this implementation. " +
                                "Please provide a requirementId or include a UC-{id} comment in your code.\"}")
                        .build();
            }

            // Get requirement
            var requirement = embeddingService.findRequirementById(requirementId);
            if (requirement == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Requirement not found: " + requirementId + "\"}")
                        .build();
            }

            // Perform AST analysis
            codeAnalysisService.performAstAnalysis(implementation);

            // Analyze code alignment
            FeedbackResponse feedback = codeAnalysisService.analyzeCode(implementation, requirement);

            return Response.ok(feedback).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Analysis failed: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("/health")
    public Response healthCheck() {
        return Response.ok().entity("{\"status\": \"OK\", \"timestamp\": \"" + System.currentTimeMillis() + "\"}").build();
    }

    @GET
    @Path("/languages")
    public Response getSupportedLanguages() {
        return Response.ok().entity("{\"supportedLanguages\": \"" + supportedLanguages + "\"}").build();
    }
}