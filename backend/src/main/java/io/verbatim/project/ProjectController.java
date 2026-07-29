package io.verbatim.project;

import io.verbatim.project.ProjectModels.CreateProjectRequest;
import io.verbatim.project.ProjectModels.ProjectView;
import io.verbatim.project.ProjectModels.RuleSetView;
import io.verbatim.project.ProjectModels.UpdateRulesRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    List<ProjectView> list() {
        return projects.list();
    }

    @GetMapping("/{projectId}")
    ProjectView get(@PathVariable UUID projectId) {
        return projects.get(projectId);
    }

    @PostMapping
    ResponseEntity<ProjectView> create(@Valid @RequestBody CreateProjectRequest request) {
        ProjectView project = projects.create(request);
        return ResponseEntity.created(URI.create("/api/projects/" + project.id())).body(project);
    }

    @GetMapping("/{projectId}/rules")
    RuleSetView getRules(@PathVariable UUID projectId) {
        return projects.getRules(projectId);
    }

    @PutMapping("/{projectId}/rules")
    RuleSetView updateRules(
        @PathVariable UUID projectId,
        @Valid @RequestBody UpdateRulesRequest request
    ) {
        return projects.updateRules(projectId, request);
    }
}
