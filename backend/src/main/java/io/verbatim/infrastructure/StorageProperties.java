package io.verbatim.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "verbatim.storage")
public record StorageProperties(String root) {
}
