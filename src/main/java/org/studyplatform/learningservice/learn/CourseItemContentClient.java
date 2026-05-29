package org.studyplatform.learningservice.learn;

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
public class CourseItemContentClient {

    private static final Logger log = LoggerFactory.getLogger(CourseItemContentClient.class);

    private final RestClient restClient;
    private final CourseServiceProperties properties;

    public CourseItemContentClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    public CourseItemContentResponse getCourseItemContent(Long itemId, String authorizationHeader) {
        long startedAt = System.nanoTime();
        log.info("CourseService content request itemId={}", itemId);
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/api/v1/internal/course-items/{itemId}/content", itemId)
                    .header(properties.getInternalApiKeyHeader(), properties.getInternalApiKey());

            if (StringUtils.hasText(authorizationHeader)) {
                request = request.header("Authorization", authorizationHeader);
            }

            CourseItemContentResponse response = request.retrieve()
                    .body(CourseItemContentResponse.class);
            log.info(
                    "CourseService content response itemId={} courseId={} moduleId={} itemType={} durationMs={}",
                    itemId,
                    response == null ? null : response.courseId(),
                    response == null ? null : response.moduleId(),
                    response == null ? null : response.itemType(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("CourseService content not found itemId={} durationMs={}", itemId, elapsedMs(startedAt));
            throw new NotFoundException("Course item not found: " + itemId);
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "CourseService content error itemId={} status={} durationMs={}",
                    itemId,
                    ex.getStatusCode().value(),
                    elapsedMs(startedAt)
            );
            throw new IllegalStateException("CourseService returned " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("CourseService content request failed itemId={} durationMs={}", itemId, elapsedMs(startedAt));
            throw new IllegalStateException("CourseService content request failed");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
