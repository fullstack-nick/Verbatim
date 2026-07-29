package io.verbatim.workflow;

import io.verbatim.workflow.WorkflowModels.AddInstructionRequest;
import io.verbatim.workflow.WorkflowModels.InstructionView;
import io.verbatim.workflow.WorkflowModels.JobView;
import io.verbatim.workflow.WorkflowModels.RevisionView;
import io.verbatim.workflow.WorkflowModels.StartTranslationRequest;
import io.verbatim.workflow.WorkflowModels.StartTranslationResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/documents/{documentId}")
public class WorkflowController {

    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @PostMapping("/translations")
    ResponseEntity<StartTranslationResponse> start(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody(required = false) StartTranslationRequest request
    ) {
        StartTranslationResponse response = workflows.start(
            projectId,
            documentId,
            idempotencyKey,
            request == null ? new StartTranslationRequest(List.of()) : request
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/jobs/{jobId}")
    JobView job(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @PathVariable UUID jobId
    ) {
        return workflows.getJob(projectId, documentId, jobId);
    }

    @GetMapping("/revisions")
    List<RevisionView> revisions(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId
    ) {
        return workflows.revisions(projectId, documentId);
    }

    @GetMapping("/revisions/{revisionId}")
    RevisionView revision(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @PathVariable UUID revisionId
    ) {
        return workflows.revision(projectId, documentId, revisionId);
    }

    @GetMapping("/revisions/{revisionId}/download")
    ResponseEntity<Resource> download(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @PathVariable UUID revisionId
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"verbatim-translation.pdf\"")
            .cacheControl(CacheControl.noCache())
            .body(workflows.download(projectId, documentId, revisionId));
    }

    @PostMapping("/revisions/{revisionId}/approve")
    RevisionView approve(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @PathVariable UUID revisionId
    ) {
        return workflows.approve(projectId, documentId, revisionId);
    }

    @PostMapping("/instructions")
    InstructionView instruction(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @Valid @RequestBody AddInstructionRequest request
    ) {
        return workflows.addInstruction(projectId, documentId, request);
    }
}
