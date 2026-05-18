package org.studyplatform.learningservice.CodeRunner.client.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.studyplatform.learningservice.CodeRunner.config.CourseServiceProperties;
import org.studyplatform.learningservice.common.exception.NotFoundException;

@Component
public class CourseServiceClient implements CourseExecutionPackageProvider {

    private static final Logger log = LoggerFactory.getLogger(CourseServiceClient.class);

    private final RestClient restClient;
    private final CourseServiceProperties properties;

    public CourseServiceClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public CourseItemExecutionPackage getExecutionPackage(Long itemId, String authorizationHeader) {
        long startedAt = System.nanoTime();
        log.info("CourseService execution package request itemId={}", itemId);
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/api/v1/internal/course-items/{itemId}/execution-package", itemId)
                    .header(properties.getInternalApiKeyHeader(), properties.getInternalApiKey());

            if (StringUtils.hasText(authorizationHeader)) {
                request = request.header("Authorization", authorizationHeader);
            }

            CourseItemExecutionPackage response = request.retrieve()
                    .body(CourseItemExecutionPackage.class);
            log.info(
                    "CourseService execution package response itemId={} courseId={} moduleId={} itemType={} tests={} durationMs={}",
                    itemId,
                    response == null ? null : response.courseId(),
                    response == null ? null : response.moduleId(),
                    response == null ? null : response.itemType(),
                    response == null || response.tests() == null ? 0 : response.tests().size(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("CourseService execution package not found itemId={} durationMs={}", itemId, elapsedMs(startedAt));
            throw new NotFoundException("Course item not found: " + itemId);
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "CourseService execution package error itemId={} status={} durationMs={}",
                    itemId,
                    ex.getStatusCode().value(),
                    elapsedMs(startedAt)
            );
            throw new IllegalStateException("CourseService returned " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("CourseService execution package request failed itemId={} durationMs={}", itemId, elapsedMs(startedAt));
            throw new IllegalStateException("CourseService request failed");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
