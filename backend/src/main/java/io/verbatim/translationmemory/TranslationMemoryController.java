package io.verbatim.translationmemory;

import io.verbatim.translationmemory.TranslationMemoryModels.CreateMemoryRequest;
import io.verbatim.translationmemory.TranslationMemoryModels.MemoryView;
import io.verbatim.translationmemory.TranslationMemoryModels.MemorySuggestion;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/translation-memory")
public class TranslationMemoryController {

    private final TranslationMemoryService translationMemory;

    public TranslationMemoryController(TranslationMemoryService translationMemory) {
        this.translationMemory = translationMemory;
    }

    @GetMapping
    List<MemoryView> list(@PathVariable UUID projectId) {
        return translationMemory.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MemoryView create(
        @PathVariable UUID projectId,
        @Valid @RequestBody CreateMemoryRequest request
    ) {
        return translationMemory.create(projectId, request);
    }

    @GetMapping("/search")
    List<MemorySuggestion> search(
        @PathVariable UUID projectId,
        @RequestParam String sourceLocale,
        @RequestParam String targetLocale,
        @RequestParam String query,
        @RequestParam(defaultValue = "3") int limit
    ) {
        return translationMemory.suggestions(
            projectId,
            sourceLocale,
            targetLocale,
            query,
            limit
        );
    }
}
