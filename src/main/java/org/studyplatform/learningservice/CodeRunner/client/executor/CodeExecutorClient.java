package org.studyplatform.learningservice.CodeRunner.client.executor;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.studyplatform.learningservice.CodeRunner.config.CodeExecutorServiceProperties;

@Component
public class CodeExecutorClient {

    private final RestClient restClient;
    private final CodeExecutorServiceProperties properties;

    public CodeExecutorClient(RestClient.Builder builder, CodeExecutorServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    public ExecutionResponse executeBatch(ExecutionCreateRequest request) {
        try {
            return restClient.post()
                    .uri("/executions/batch")
                    .header("Authorization", "Bearer " + properties.getAuthToken())
                    .body(request)
                    .retrieve()
                    .body(ExecutionResponse.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }
}
