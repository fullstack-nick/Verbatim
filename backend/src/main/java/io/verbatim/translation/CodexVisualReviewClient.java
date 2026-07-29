package io.verbatim.translation;

import io.verbatim.translation.TranslationClient.Usage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CodexVisualReviewClient {

    private final CodexProperties properties;
    private final ObjectMapper objectMapper;

    public CodexVisualReviewClient(CodexProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public VisualReviewResult review(Path sourcePage, Path translatedPage) {
        if (!properties.enabled() || !properties.visualReviewEnabled()) {
            return new VisualReviewResult(true, List.of(), Usage.empty(), null, 0);
        }
        Instant started = Instant.now();
        Process process = startProcess(sourcePage, translatedPage);
        String prompt = """
            Compare these two renders of the same PDF page. The first image is the source and
            the second is the translated revision. Different words and normal target-language
            text expansion are expected. Review layout fidelity only.

            Flag clipped or overlapping text, visible remnants of source text, damaged
            backgrounds, broken tables, moved or changed non-text graphics, lost hierarchy,
            severe spacing drift, or unreadable target text. Do not flag a valid translation
            merely because its glyph shapes or line lengths differ. Return the required
            structured result and no prose.
            """;
        try {
            process.getOutputStream().write(prompt.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            StringBuilder error = new StringBuilder();
            Thread errors = Thread.ofVirtual().start(() -> drainErrors(process, error));
            String message = null;
            String threadId = null;
            Usage usage = Usage.empty();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode event = objectMapper.readTree(line);
                    String type = event.path("type").asText("");
                    if ("thread.started".equals(type)) {
                        threadId = event.path("thread_id").asText(null);
                    }
                    if ("item.completed".equals(type)
                        && "agent_message".equals(event.path("item").path("type").asText())) {
                        message = event.path("item").path("text").asText();
                    }
                    if ("turn.completed".equals(type)) {
                        JsonNode reported = event.path("usage");
                        usage = new Usage(
                            reported.path("input_tokens").asLong(0),
                            reported.path("cached_input_tokens").asLong(0),
                            reported.path("output_tokens").asLong(0),
                            reported.path("reasoning_output_tokens").asLong(0)
                        );
                    }
                }
            }
            boolean finished = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Codex visual review timed out.");
            }
            errors.join(Duration.ofSeconds(5));
            if (process.exitValue() != 0 || message == null) {
                throw new IllegalStateException(
                    "Codex visual review failed: " + abbreviate(error.toString())
                );
            }
            JsonNode output = objectMapper.readTree(message);
            List<VisualFinding> findings = new ArrayList<>();
            for (JsonNode item : output.path("findings")) {
                findings.add(new VisualFinding(
                    item.path("code").asText(),
                    item.path("severity").asText(),
                    item.path("message").asText()
                ));
            }
            return new VisualReviewResult(
                output.path("pass").asBoolean(findings.isEmpty()),
                findings,
                usage,
                threadId,
                Duration.between(started, Instant.now()).toMillis()
            );
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new IllegalStateException("Codex visual review output could not be read.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Codex visual review was interrupted.", exception);
        }
    }

    public int maxPages() {
        return Math.max(0, properties.visualReviewMaxPages());
    }

    private Process startProcess(Path source, Path translated) {
        String configured = properties.executable();
        String executable = configured.contains("/") || configured.contains("\\")
            ? Path.of(configured).toAbsolutePath().normalize().toString()
            : configured;
        List<String> command = new ArrayList<>();
        if (isWindows() && (executable.endsWith(".cmd") || executable.endsWith(".bat"))) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
        }
        command.add(executable);
        command.add("exec");
        command.add("--ephemeral");
        command.add("--sandbox");
        command.add("read-only");
        command.add("--json");
        command.add("--image");
        command.add(source.toAbsolutePath().normalize().toString());
        command.add("--image");
        command.add(translated.toAbsolutePath().normalize().toString());
        command.add("--output-schema");
        command.add(Path.of(properties.visualReviewSchema()).toAbsolutePath().normalize().toString());
        command.add("-");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(Path.of("..").toAbsolutePath().normalize().toFile());
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("The Codex CLI could not be started for visual review.", exception);
        }
    }

    private void drainErrors(Process process, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null && target.length() < 8_000) {
                target.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // Exit status and structured output remain authoritative.
        }
    }

    private String abbreviate(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= 600 ? clean : clean.substring(0, 600);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    public record VisualFinding(String code, String severity, String message) {
    }

    public record VisualReviewResult(
        boolean pass,
        List<VisualFinding> findings,
        Usage usage,
        String providerThreadId,
        long durationMillis
    ) {
    }
}
