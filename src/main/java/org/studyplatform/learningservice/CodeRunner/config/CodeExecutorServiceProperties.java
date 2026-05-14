package org.studyplatform.learningservice.CodeRunner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "coderunner.executor-service")
public class CodeExecutorServiceProperties {

    private String baseUrl = "http://localhost:8083";
    private String authToken = "dev-executor-service-token";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}
