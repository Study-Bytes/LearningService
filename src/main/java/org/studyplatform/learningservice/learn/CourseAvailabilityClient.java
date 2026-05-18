package org.studyplatform.learningservice.learn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.studyplatform.learningservice.CodeRunner.config.CourseServiceProperties;
import org.studyplatform.learningservice.common.exception.NotFoundException;

@Component
public class CourseAvailabilityClient {

    private static final Logger log = LoggerFactory.getLogger(CourseAvailabilityClient.class);

    private final RestClient restClient;
    private final CourseServiceProperties properties;

    public CourseAvailabilityClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    public CourseAvailabilityResponse getAvailability(Long courseId) {
        long startedAt = System.nanoTime();
        log.info("CourseService availability request courseId={}", courseId);
        try {
            CourseAvailabilityResponse response = restClient.get()
                    .uri("/api/v1/internal/courses/{courseId}/availability", courseId)
                    .header(properties.getInternalApiKeyHeader(), properties.getInternalApiKey())
                    .retrieve()
                    .body(CourseAvailabilityResponse.class);
            log.info(
                    "CourseService availability response courseId={} available={} durationMs={}",
                    courseId,
                    response == null ? null : response.availableForEnrollment(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("CourseService availability not found courseId={} durationMs={}", courseId, elapsedMs(startedAt));
            throw new NotFoundException("Course not found: " + courseId);
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "CourseService availability error courseId={} status={} durationMs={}",
                    courseId,
                    ex.getStatusCode().value(),
                    elapsedMs(startedAt)
            );
            throw new IllegalStateException("CourseService returned " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("CourseService availability request failed courseId={} durationMs={}", courseId, elapsedMs(startedAt));
            throw new IllegalStateException("CourseService availability request failed");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
