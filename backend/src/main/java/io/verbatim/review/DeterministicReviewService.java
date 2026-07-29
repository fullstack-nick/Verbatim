package io.verbatim.review;

import io.verbatim.review.ReviewModels.Finding;
import io.verbatim.terminology.TerminologyModels.TermView;
import io.verbatim.terminology.TerminologyModels.TranslationView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DeterministicReviewService {

    private static final Pattern PLACEHOLDER =
        Pattern.compile("%\\{[A-Za-z_][A-Za-z0-9_]*}|\\{[A-Za-z_][A-Za-z0-9_]*}|%[sd]");
    private static final Pattern HTML_TAG =
        Pattern.compile("</?(strong|em|a|br)\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    public List<Finding> review(
        UUID segmentId,
        int pageNumber,
        String source,
        String target,
        String targetLocale,
        List<TermView> terms
    ) {
        List<Finding> findings = new ArrayList<>();
        compareMultiset(segmentId, pageNumber, source, target, PLACEHOLDER, "PLACEHOLDER", findings);
        compareMultiset(segmentId, pageNumber, source, target, HTML_TAG, "HTML_TAG", findings);
        checkTerminology(segmentId, pageNumber, source, target, targetLocale, terms, findings);
        return findings;
    }

    private void compareMultiset(
        UUID segmentId,
        int pageNumber,
        String source,
        String target,
        Pattern pattern,
        String kind,
        List<Finding> findings
    ) {
        Map<String, Integer> sourceCounts = occurrences(source, pattern);
        Map<String, Integer> targetCounts = occurrences(target, pattern);
        for (Map.Entry<String, Integer> entry : sourceCounts.entrySet()) {
            int targetCount = targetCounts.getOrDefault(entry.getKey(), 0);
            if (targetCount < entry.getValue()) {
                findings.add(new Finding(
                    "PLACEHOLDER".equals(kind) ? "MISSING_PLACEHOLDER" : "MISSING_HTML_TAG",
                    "ERROR",
                    "Target text is missing %s %s.".formatted(kind.toLowerCase(Locale.ROOT), entry.getKey()),
                    pageNumber,
                    segmentId
                ));
            } else if (targetCount > entry.getValue()) {
                findings.add(new Finding(
                    "PLACEHOLDER".equals(kind) ? "DUPLICATE_PLACEHOLDER" : "EXTRA_HTML_TAG",
                    "ERROR",
                    "Target text duplicates %s %s.".formatted(kind.toLowerCase(Locale.ROOT), entry.getKey()),
                    pageNumber,
                    segmentId
                ));
            }
        }
        for (String value : targetCounts.keySet()) {
            if (!sourceCounts.containsKey(value)) {
                findings.add(new Finding(
                    "PLACEHOLDER".equals(kind) ? "EXTRA_PLACEHOLDER" : "EXTRA_HTML_TAG",
                    "ERROR",
                    "Target text contains unexpected %s %s.".formatted(kind.toLowerCase(Locale.ROOT), value),
                    pageNumber,
                    segmentId
                ));
            }
        }
    }

    private Map<String, Integer> occurrences(String text, Pattern pattern) {
        Map<String, Integer> result = new HashMap<>();
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = matcher.group().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            result.merge(value, 1, Integer::sum);
        }
        return result;
    }

    private void checkTerminology(
        UUID segmentId,
        int pageNumber,
        String source,
        String target,
        String targetLocale,
        List<TermView> terms,
        List<Finding> findings
    ) {
        for (TermView term : terms) {
            if (!matchesSource(source, term)) {
                continue;
            }
            if ("NEVER_TRANSLATE".equals(term.translationPreference())) {
                if (!contains(target, term.sourceTerm(), term.caseMode())) {
                    findings.add(new Finding(
                        "NEVER_TRANSLATE_TERM_CHANGED",
                        "ERROR",
                        "The term '%s' must remain unchanged.".formatted(term.sourceTerm()),
                        pageNumber,
                        segmentId
                    ));
                }
                continue;
            }

            List<TranslationView> candidates = term.translations().stream()
                .filter(item -> targetLocale.equalsIgnoreCase(item.locale()))
                .toList();
            TranslationView forbidden = candidates.stream()
                .filter(item -> "NOT_RECOMMENDED".equals(item.usage()) || "OBSOLETE".equals(item.usage()))
                .filter(item -> contains(target, item.text(), term.caseMode()))
                .findFirst()
                .orElse(null);
            TranslationView preferred = candidates.stream()
                .filter(item -> "PREFERRED".equals(item.usage()))
                .findFirst()
                .orElse(null);
            if (forbidden != null) {
                String severity = "OBSOLETE".equals(forbidden.usage()) ? "ERROR" : "WARNING";
                String replacement = preferred == null ? "" : " Prefer '%s'.".formatted(preferred.text());
                findings.add(new Finding(
                    "OBSOLETE".equals(forbidden.usage()) ? "OBSOLETE_TERM" : "NOT_RECOMMENDED_TERM",
                    severity,
                    "'%s' is %s.%s".formatted(
                        forbidden.text(),
                        forbidden.usage().toLowerCase(Locale.ROOT).replace('_', ' '),
                        replacement
                    ),
                    pageNumber,
                    segmentId
                ));
                continue;
            }
            boolean valid = candidates.stream()
                .filter(item -> "PREFERRED".equals(item.usage()) || "ADMITTED".equals(item.usage()))
                .anyMatch(item -> contains(target, item.text(), term.caseMode()));
            if (!valid && !candidates.isEmpty()) {
                findings.add(new Finding(
                    "MISSING_REQUIRED_TERM",
                    "ERROR",
                    "The source term '%s' has no approved translation in the target text."
                        .formatted(term.sourceTerm()),
                    pageNumber,
                    segmentId
                ));
            }
        }
    }

    private boolean matchesSource(String source, TermView term) {
        String haystack = normalize(source, term.caseMode());
        String needle = normalize(term.sourceTerm(), term.caseMode());
        return switch (term.matchingType()) {
            case "PREFIX" -> Pattern.compile("\\b" + Pattern.quote(needle)).matcher(haystack).find();
            case "FUZZY" -> haystack.contains(needle)
                || words(haystack).stream().anyMatch(word -> editDistance(word, needle) <= 1);
            default -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(needle)
                + "(?![\\p{L}\\p{N}])").matcher(haystack).find();
        };
    }

    private boolean contains(String text, String term, String caseMode) {
        return normalize(text, caseMode).contains(normalize(term, caseMode));
    }

    private String normalize(String text, String caseMode) {
        String value = text == null ? "" : text;
        return "SENSITIVE".equals(caseMode) ? value : value.toLowerCase(Locale.ROOT);
    }

    private List<String> words(String value) {
        return List.of(value.split("[^\\p{L}\\p{N}]+"));
    }

    private int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost
                );
            }
            previous = current;
        }
        return previous[right.length()];
    }
}
