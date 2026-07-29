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
public class CodexVisionOcrClient {

    private final CodexProperties properties;
    private final ObjectMapper objectMapper;

    public CodexVisionOcrClient(CodexProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public OcrResult read(Path pageImage) {
        if (!properties.enabled()) {
            throw new IllegalStateException("Codex integration is disabled.");
        }
        Instant started = Instant.now();
        Process process = startProcess(pageImage);
        String prompt = """
            Read every printed text region in this high-quality scanned document page.
            Return regions in natural reading order. Coordinates x, y, width and height are
            normalized from 0 to 1000 relative to the full page, with x/y at the top-left.
            Keep placeholders byte-for-byte exact. Estimate fontSize in PDF points and bold.
            Do not translate. Exclude handwriting. If text is printed over photographs,
            diagrams, or textured backgrounds, omit that region because version one flags it
            for manual handling.
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
                throw new IllegalStateException("Codex OCR timed out.");
            }
            errors.join(Duration.ofSeconds(5));
            if (process.exitValue() != 0 || message == null) {
                throw new IllegalStateException("Codex OCR failed: " + abbreviate(error.toString()));
            }
            return new OcrResult(
                parse(message),
                usage,
                threadId,
                Duration.between(started, Instant.now()).toMillis()
            );
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new IllegalStateException("Codex OCR output could not be read.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Codex OCR was interrupted.", exception);
        }
    }

    private Process startProcess(Path image) {
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
        command.add(image.toAbsolutePath().normalize().toString());
        command.add("--output-schema");
        command.add(Path.of(properties.ocrSchema()).toAbsolutePath().normalize().toString());
        command.add("-");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(Path.of("..").toAbsolutePath().normalize().toFile());
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("The Codex CLI could not be started for OCR.", exception);
        }
    }

    private List<OcrRegion> parse(String message) {
        JsonNode root = objectMapper.readTree(message);
        List<OcrRegion> regions = new ArrayList<>();
        for (JsonNode item : root.path("regions")) {
            regions.add(new OcrRegion(
                item.path("text").asText(),
                item.path("x").asDouble(),
                item.path("y").asDouble(),
                item.path("width").asDouble(),
                item.path("height").asDouble(),
                item.path("fontSize").asDouble(),
                item.path("bold").asBoolean(false)
            ));
        }
        return regions;
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
            // Exit status and structured stdout remain authoritative.
        }
    }

    private String abbreviate(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= 600 ? clean : clean.substring(0, 600);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    public record OcrRegion(
        String text,
        double x,
        double y,
        double width,
        double height,
        double fontSize,
        boolean bold
    ) {
    }

    public record OcrResult(
        List<OcrRegion> regions,
        Usage usage,
        String providerThreadId,
        long durationMillis
    ) {
    }
}
