package io.verbatim.workflow;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class WorkflowModels {

    private WorkflowModels() {
    }

    public record StartTranslationRequest(List<String> instructions) {
        public StartTranslationRequest {
            instructions = instructions == null ? List.of() : List.copyOf(instructions);
        }
    }

    public record StartTranslationResponse(UUID jobId, UUID revisionId, String state) {
    }

    public record AddInstructionRequest(@NotBlank String message, boolean promoteToProject) {
    }

    public record InstructionView(
        UUID id,
        String scope,
        String message,
        boolean promotedToProject,
        OffsetDateTime createdAt
    ) {
    }

    public record JobView(
        UUID id,
        UUID documentId,
        UUID revisionId,
        String state,
        String currentStage,
        int progressCurrent,
        int progressTotal,
        int attempts,
        String errorCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
    ) {
    }

    public record FindingView(
        UUID id,
        String code,
        String severity,
        String message,
        Integer pageNumber,
        UUID segmentId
    ) {
    }

    public record UsageView(
        long inputTokens,
        long cachedInputTokens,
        long outputTokens,
        long reasoningTokens,
        long durationMillis
    ) {
    }

    public record RevisionView(
        UUID id,
        int revisionNumber,
        String state,
        String downloadUrl,
        OffsetDateTime createdAt,
        OffsetDateTime approvedAt,
        List<FindingView> findings,
        UsageView usage
    ) {
    }
}
