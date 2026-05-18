package org.studyplatform.learningservice.CodeRunner.client.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.studyplatform.learningservice.CodeRunner.config.CodeExecutorServiceProperties;

@Component
public class CodeExecutorClient {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutorClient.class);

    private final RestClient restClient;
    private final CodeExecutorServiceProperties properties;

    public CodeExecutorClient(RestClient.Builder builder, CodeExecutorServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    public ExecutionResponse executeBatch(ExecutionCreateRequest request) {
        long startedAt = System.nanoTime();
        log.info(
                "CodeExecutor batch request language={} tests={} codeLength={}",
                request.language(),
                request.tests() == null ? 0 : request.tests().size(),
                request.code() == null ? 0 : request.code().length()
        );
        try {
            ExecutionResponse response = restClient.post()
                    .uri("/executions/batch")
                    .header("Authorization", bearerToken())
                    .body(request)
                    .retrieve()
                    .body(ExecutionResponse.class);
            log.info(
                    "CodeExecutor batch response executorRequestId={} status={} tests={} durationMs={}",
                    response == null ? null : response.id(),
                    response == null ? null : response.status(),
                    response == null || response.tests() == null ? 0 : response.tests().size(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (RestClientException ex) {
            log.warn("CodeExecutor batch request failed durationMs={}", elapsedMs(startedAt));
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    public ExecutionResponse createSession(ExecutionSessionCreateRequest request) {
        long startedAt = System.nanoTime();
        log.info(
                "CodeExecutor session create request language={} codeLength={}",
                request.language(),
                request.code() == null ? 0 : request.code().length()
        );
        try {
            ExecutionResponse response = restClient.post()
                    .uri("/executions")
                    .header("Authorization", bearerToken())
                    .body(request)
                    .retrieve()
                    .body(ExecutionResponse.class);
            log.info(
                    "CodeExecutor session create response executorRequestId={} status={} durationMs={}",
                    response == null ? null : response.id(),
                    response == null ? null : response.status(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (RestClientException ex) {
            log.warn("CodeExecutor session create request failed durationMs={}", elapsedMs(startedAt));
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    public ExecutionResponse.TestExecutionResult runTest(String sessionId, ExecutionTestRunRequest request) {
        long startedAt = System.nanoTime();
        log.debug("CodeExecutor test run request sessionId={} testId={}", sessionId, request.id());
        try {
            ExecutionResponse.TestExecutionResult response = restClient.post()
                    .uri("/executions/{id}/tests", sessionId)
                    .header("Authorization", bearerToken())
                    .body(request)
                    .retrieve()
                    .body(ExecutionResponse.TestExecutionResult.class);
            log.debug(
                    "CodeExecutor test run response sessionId={} testId={} outcome={} durationMs={}",
                    sessionId,
                    request.id(),
                    response == null ? null : response.outcome(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (RestClientException ex) {
            log.warn("CodeExecutor test run request failed sessionId={} testId={} durationMs={}", sessionId, request.id(), elapsedMs(startedAt));
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    public void cancelSession(String sessionId) {
        long startedAt = System.nanoTime();
        log.debug("CodeExecutor session cancel request sessionId={}", sessionId);
        try {
            restClient.post()
                    .uri("/executions/{id}/cancel", sessionId)
                    .header("Authorization", bearerToken())
                    .retrieve()
                    .toBodilessEntity();
            log.debug("CodeExecutor session cancel response sessionId={} durationMs={}", sessionId, elapsedMs(startedAt));
        } catch (RestClientException ex) {
            log.warn("CodeExecutor session cancel request failed sessionId={} durationMs={}", sessionId, elapsedMs(startedAt));
            throw new IllegalStateException("CodeExecutorService request failed");
        }
    }

    private String bearerToken() {
        return "Bearer " + properties.getAuthToken();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
