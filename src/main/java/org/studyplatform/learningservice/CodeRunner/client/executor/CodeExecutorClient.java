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
                    .header("Authorization", bearerToken())
                    .body(request)
                    .retrieve()
                    .body(ExecutionResponse.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    public ExecutionResponse createSession(ExecutionSessionCreateRequest request) {
        try {
            return restClient.post()
                    .uri("/executions")
                    .header("Authorization", bearerToken())
                    .body(request)
                    .retrieve()
                    .body(ExecutionResponse.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    public ExecutionResponse.TestExecutionResult runTest(String sessionId, ExecutionTestRunRequest request) {
        try {
            return restClient.post()
                    .uri("/executions/{id}/tests", sessionId)
                    .header("Authorization", bearerToken())
                    .body(request)
                    .retrieve()
                    .body(ExecutionResponse.TestExecutionResult.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    public void cancelSession(String sessionId) {
        try {
            restClient.post()
                    .uri("/executions/{id}/cancel", sessionId)
                    .header("Authorization", bearerToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    private String bearerToken() {
        return "Bearer " + properties.getAuthToken();
    }
}
