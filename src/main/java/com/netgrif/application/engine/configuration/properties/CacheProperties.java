package com.netgrif.application.engine.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Data
@Configuration
@ConfigurationProperties(prefix = "nae.cache")
public class CacheProperties {

    private String petriNetById = "petriNetById";

    private String petriNetByIdentifier = "petriNetByIdentifier";

    private String petriNetNewest = "petriNetNewest";

    private String petriNetCache = "petriNetCache";

    private String defaultAdminAuthoritiesCache = "defaultAdminAuthoritiesCache";

    private String defaultUserAuthoritiesCache = "defaultUserAuthoritiesCache";

    private String defaultAnonymousAuthoritiesCache = "defaultAnonymousAuthoritiesCache";

    private List<String> additional = new ArrayList<>();

    public Set<String> getAllCaches() {
        Set<String> caches = new LinkedHashSet<>(Arrays.asList(petriNetById, petriNetByIdentifier, petriNetNewest, petriNetCache,
                defaultAdminAuthoritiesCache, defaultUserAuthoritiesCache, defaultAnonymousAuthoritiesCache));
        caches.addAll(additional);
        return caches;
    }
}
