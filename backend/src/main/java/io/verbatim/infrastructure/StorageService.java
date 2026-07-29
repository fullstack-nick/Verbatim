package io.verbatim.infrastructure;

import io.verbatim.common.ApiException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StorageService {

    private final Path root;

    public StorageService(StorageProperties properties) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(root.resolve("documents"));
        Files.createDirectories(root.resolve("revisions"));
        Files.createDirectories(root.resolve("renders"));
    }

    public StoredFile storeSource(
        UUID projectId,
        UUID documentId,
        String filename,
        InputStream content
    ) {
        String safeName = sanitizeFilename(filename);
        Path relative = Path.of("documents", projectId.toString(), documentId.toString(), safeName);
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(content, digest)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(relative.toString().replace('\\', '/'), HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "STORAGE_WRITE_FAILED",
                "The PDF could not be stored."
            );
        }
    }

    public Path documentRenderDirectory(UUID documentId) {
        Path directory = resolve(Path.of("renders", documentId.toString(), "source"));
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "STORAGE_WRITE_FAILED",
                "The render directory could not be created."
            );
        }
    }

    public Path revisionPdf(UUID documentId, UUID revisionId) {
        Path target = resolve(Path.of(
            "revisions",
            documentId.toString(),
            revisionId.toString(),
            "translated.pdf"
        ));
        try {
            Files.createDirectories(target.getParent());
            return target;
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "STORAGE_WRITE_FAILED",
                "The revision directory could not be created."
            );
        }
    }

    public Path resolve(String relativePath) {
        return resolve(Path.of(relativePath));
    }

    public Resource resource(String relativePath) {
        try {
            Path path = resolve(relativePath);
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File not found.");
            }
            return resource;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "File not found.");
        }
    }

    public String relative(Path absolutePath) {
        return root.relativize(absolutePath.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private Path resolve(Path relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH", "The path is outside storage.");
        }
        return resolved;
    }

    private String sanitizeFilename(String filename) {
        String source = filename == null || filename.isBlank() ? "document.pdf" : filename;
        String cleaned = Path.of(source).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        if (!cleaned.toLowerCase().endsWith(".pdf")) {
            cleaned += ".pdf";
        }
        return cleaned;
    }

    public record StoredFile(String relativePath, String sha256) {
    }
}
