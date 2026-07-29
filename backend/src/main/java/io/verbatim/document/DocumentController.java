package io.verbatim.document;

import io.verbatim.document.DocumentModels.DocumentDetail;
import io.verbatim.document.DocumentModels.DocumentView;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/documents")
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    List<DocumentView> list(@PathVariable UUID projectId) {
        return documents.list(projectId);
    }

    @GetMapping("/{documentId}")
    DocumentDetail get(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        return documents.get(projectId, documentId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DocumentDetail upload(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String sourceLocale,
        @RequestParam(required = false) String targetLocale,
        @RequestPart("file") MultipartFile file
    ) {
        return documents.upload(projectId, sourceLocale, targetLocale, file);
    }

    @GetMapping("/{documentId}/source")
    ResponseEntity<Resource> source(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .cacheControl(CacheControl.noCache())
            .body(documents.source(projectId, documentId));
    }

    @GetMapping("/{documentId}/pages/{pageNumber}/preview")
    ResponseEntity<Resource> pagePreview(
        @PathVariable UUID projectId,
        @PathVariable UUID documentId,
        @PathVariable int pageNumber
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.noCache())
            .body(documents.pagePreview(projectId, documentId, pageNumber));
    }
}
