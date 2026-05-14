package org.studyplatform.learningservice.CodeRunner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "coderunner.course-service")
public class CourseServiceProperties {

    private String baseUrl = "http://localhost:8082";
    private String internalApiKeyHeader = "X-Internal-API-Key";
    private String internalApiKey = "dev-learning-service-internal-key";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalApiKeyHeader() {
        return internalApiKeyHeader;
    }

    public void setInternalApiKeyHeader(String internalApiKeyHeader) {
        this.internalApiKeyHeader = internalApiKeyHeader;
    }

    public String getInternalApiKey() {
        return internalApiKey;
    }

    public void setInternalApiKey(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

}
