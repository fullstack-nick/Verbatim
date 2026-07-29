package io.verbatim.terminology;

import io.verbatim.terminology.TerminologyModels.CreateTermRequest;
import io.verbatim.terminology.TerminologyModels.TermView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/terms")
public class TerminologyController {

    private final TerminologyService terminology;

    public TerminologyController(TerminologyService terminology) {
        this.terminology = terminology;
    }

    @GetMapping
    List<TermView> list(@PathVariable UUID projectId) {
        return terminology.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TermView create(@PathVariable UUID projectId, @Valid @RequestBody CreateTermRequest request) {
        return terminology.create(projectId, request);
    }

    @DeleteMapping("/{termId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID projectId, @PathVariable UUID termId) {
        terminology.delete(projectId, termId);
    }
}
