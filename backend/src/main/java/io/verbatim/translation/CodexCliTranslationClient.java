package io.verbatim.translation;

import io.verbatim.translation.TranslationClient.SourceSegment;
import io.verbatim.translation.TranslationClient.TargetSegment;
import io.verbatim.translation.TranslationClient.TranslationContext;
import io.verbatim.translation.TranslationClient.TranslationResult;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class CodexCliTranslationClient {

    private final CodexProperties properties;
    private final ObjectMapper objectMapper;

    CodexCliTranslationClient(CodexProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    TranslationResult translate(TranslationContext context) {
        if (!properties.enabled()) {
            throw new IllegalStateException("Codex integration is disabled.");
        }
        Instant started = Instant.now();
        Process process = startProcess();
        String prompt = prompt(context);
        try {
            process.getOutputStream().write(prompt.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            StringBuilder error = new StringBuilder();
            Thread errorReader = Thread.ofVirtual().start(() -> readErrors(process, error));

            String finalMessage = null;
            String threadId = null;
            Usage usage = Usage.empty();
            try (BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = output.readLine()) != null) {
                    JsonNode event = objectMapper.readTree(line);
                    String type = event.path("type").asText("");
                    if ("thread.started".equals(type)) {
                        threadId = event.path("thread_id").asText(null);
                    }
                    if ("item.completed".equals(type)
                        && "agent_message".equals(event.path("item").path("type").asText())) {
                        finalMessage = event.path("item").path("text").asText();
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
                throw new IllegalStateException("Codex translation timed out.");
            }
            errorReader.join(Duration.ofSeconds(5));
            if (process.exitValue() != 0 || finalMessage == null) {
                throw new IllegalStateException(
                    "Codex translation failed: " + abbreviate(error.toString())
                );
            }
            return new TranslationResult(
                parseTranslations(finalMessage),
                usage,
                threadId,
                "CODEX_CLI",
                false,
                Duration.between(started, Instant.now()).toMillis()
            );
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new IllegalStateException("Codex output could not be read.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Codex translation was interrupted.", exception);
        }
    }

    private Process startProcess() {
        List<String> command = new ArrayList<>();
        String configured = properties.executable();
        String executable = configured.contains("/") || configured.contains("\\")
            ? Path.of(configured).toAbsolutePath().normalize().toString()
            : configured;
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
        command.add("--output-schema");
        command.add(Path.of(properties.translationSchema()).toAbsolutePath().normalize().toString());
        command.add("-");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(Path.of("..").toAbsolutePath().normalize().toFile());
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("The Codex CLI could not be started.", exception);
        }
    }

    private String prompt(TranslationContext context) {
        StringBuilder prompt = new StringBuilder("""
            Translate the supplied PDF text segments faithfully.

            Return only the structured result required by the output schema.
            Keep every placeholder byte-for-byte unchanged.
            Preserve names and NEVER_TRANSLATE terminology exactly.
            Use the terminology and project rules as authoritative.
            Do not add explanations to translated text.
            Translate each segment independently enough to preserve its layout, while keeping
            terminology and tone consistent across all segments.

            """);
        prompt.append("Source locale: ").append(context.sourceLocale()).append('\n');
        prompt.append("Target locale: ").append(context.targetLocale()).append('\n');
        prompt.append("Project rules: ")
            .append(objectMapper.writeValueAsString(context.projectRules())).append('\n');
        prompt.append("Terminology: ")
            .append(objectMapper.writeValueAsString(context.terminology())).append('\n');
        prompt.append("Approved translation memory examples: ")
            .append(objectMapper.writeValueAsString(context.translationMemory())).append('\n');
        prompt.append("Document instructions: ")
            .append(objectMapper.writeValueAsString(context.documentInstructions())).append('\n');
        prompt.append("Segments:\n");
        for (SourceSegment segment : context.segments()) {
            prompt.append("- ")
                .append(segment.id())
                .append(": ")
                .append(objectMapper.writeValueAsString(segment.text()))
                .append('\n');
        }
        return prompt.toString();
    }

    private List<TargetSegment> parseTranslations(String finalMessage) {
        JsonNode result = objectMapper.readTree(finalMessage);
        List<TargetSegment> translations = new ArrayList<>();
        for (JsonNode item : result.path("translations")) {
            translations.add(new TargetSegment(
                UUID.fromString(item.path("segmentId").asText()),
                item.path("targetText").asText()
            ));
        }
        return translations;
    }

    private void readErrors(Process process, StringBuilder target) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (target.length() < 8_000) {
                    target.append(line).append('\n');
                }
            }
        } catch (IOException ignored) {
            // The process result remains the authoritative failure signal.
        }
    }

    private String abbreviate(String value) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.length() <= 600 ? cleaned : cleaned.substring(0, 600);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }
}
