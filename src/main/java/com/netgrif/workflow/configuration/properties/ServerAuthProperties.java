package com.netgrif.workflow.configuration.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "server.auth")
public class ServerAuthProperties {

    private boolean openRegistration = true;

    private int tokenValidityPeriod = 3;

    private int minimalPasswordLength = 6;

    private boolean enableProfileEdit = true;

    private String[] noAuthorizationPatterns = new String[0];
}
