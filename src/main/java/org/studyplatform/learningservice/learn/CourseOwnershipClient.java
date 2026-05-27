package org.studyplatform.learningservice.learn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.studyplatform.learningservice.CodeRunner.config.CourseServiceProperties;

@Component
public class CourseOwnershipClient {

    private static final Logger log = LoggerFactory.getLogger(CourseOwnershipClient.class);

    private final RestClient restClient;
    private final CourseServiceProperties properties;

    public CourseOwnershipClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    public boolean isCourseOwner(Long courseId, Long userId) {
        long startedAt = System.nanoTime();
        log.info("CourseService ownership request courseId={} userId={}", courseId, userId);
        try {
            CourseOwnershipResponse response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/v1/internal/courses/{courseId}/ownership")
                            .queryParam("userId", userId)
                            .build(courseId))
                    .header(properties.getInternalApiKeyHeader(), properties.getInternalApiKey())
                    .retrieve()
                    .body(CourseOwnershipResponse.class);
            boolean owner = response != null && Boolean.TRUE.equals(response.owner());
            log.info(
                    "CourseService ownership response courseId={} userId={} owner={} durationMs={}",
                    courseId,
                    userId,
                    owner,
                    elapsedMs(startedAt)
            );
            return owner;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn(
                    "CourseService ownership not found courseId={} userId={} durationMs={}",
                    courseId,
                    userId,
                    elapsedMs(startedAt)
            );
            return false;
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "CourseService ownership error courseId={} userId={} status={} durationMs={}",
                    courseId,
                    userId,
                    ex.getStatusCode().value(),
                    elapsedMs(startedAt)
            );
            return false;
        } catch (RestClientException ex) {
            log.warn(
                    "CourseService ownership request failed courseId={} userId={} durationMs={}",
                    courseId,
                    userId,
                    elapsedMs(startedAt)
            );
            return false;
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
