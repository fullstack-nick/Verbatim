package io.verbatim.workflow;

import io.verbatim.workflow.WorkflowService.JobClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkflowWorker {

    private static final Logger log = LoggerFactory.getLogger(WorkflowWorker.class);
    private final WorkflowService workflows;

    public WorkflowWorker(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @Scheduled(fixedDelayString = "${verbatim.workflow.poll-delay:1000}")
    public void work() {
        JobClaim claim = workflows.claimNext();
        if (claim == null) {
            return;
        }
        try {
            workflows.process(claim);
        } catch (RuntimeException failure) {
            log.error("Document workflow {} failed", claim.id(), failure);
            workflows.fail(claim, failure);
        }
    }
}
