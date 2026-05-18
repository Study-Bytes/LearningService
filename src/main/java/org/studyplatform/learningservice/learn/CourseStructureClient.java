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
public class CourseStructureClient {

    private static final Logger log = LoggerFactory.getLogger(CourseStructureClient.class);

    private final RestClient restClient;

    public CourseStructureClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public CourseStructureResponse getCourse(Long courseId) {
        long startedAt = System.nanoTime();
        log.info("CourseService public course request courseId={}", courseId);
        try {
            CourseStructureResponse response = restClient.get()
                    .uri("/api/v1/courses/{courseId}", courseId)
                    .retrieve()
                    .body(CourseStructureResponse.class);
            log.info(
                    "CourseService public course response courseId={} modules={} durationMs={}",
                    courseId,
                    response == null || response.modules() == null ? 0 : response.modules().size(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("CourseService public course not found courseId={} durationMs={}", courseId, elapsedMs(startedAt));
            throw new NotFoundException("Course not found: " + courseId);
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "CourseService public course error courseId={} status={} durationMs={}",
                    courseId,
                    ex.getStatusCode().value(),
                    elapsedMs(startedAt)
            );
            throw new IllegalStateException("CourseService returned " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("CourseService public course request failed courseId={} durationMs={}", courseId, elapsedMs(startedAt));
            throw new IllegalStateException("CourseService course request failed");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
