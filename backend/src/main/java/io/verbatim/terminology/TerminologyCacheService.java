package io.verbatim.terminology;

import io.verbatim.project.ProjectService;
import io.verbatim.terminology.TerminologyModels.TermView;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class TerminologyCacheService {

    private static final Logger log = LoggerFactory.getLogger(TerminologyCacheService.class);

    private final TerminologyService terminology;
    private final ProjectService projects;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TerminologyCacheService(
        TerminologyService terminology,
        ProjectService projects,
        StringRedisTemplate redis,
        ObjectMapper objectMapper
    ) {
        this.terminology = terminology;
        this.projects = projects;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public List<TermView> active(UUID projectId, String targetLocale) {
        int version = projects.get(projectId).ruleSetVersion();
        String key = "term-base:%s:%s:v%d".formatted(projectId, targetLocale, version);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Arrays.asList(objectMapper.readValue(cached, TermView[].class));
            }
        } catch (RuntimeException failure) {
            log.warn("Terminology cache read failed; PostgreSQL remains authoritative", failure);
        }

        List<TermView> terms = terminology.list(projectId);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(terms), Duration.ofHours(24));
        } catch (RuntimeException failure) {
            log.warn("Terminology cache write failed; continuing without cache", failure);
        }
        return terms;
    }
}
