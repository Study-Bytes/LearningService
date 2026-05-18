package org.studyplatform.learningservice.learn;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.studyplatform.learningservice.CodeRunner.config.CourseServiceProperties;
import org.studyplatform.learningservice.common.exception.NotFoundException;

@Component
public class CourseStructureClient {

    private final RestClient restClient;

    public CourseStructureClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public CourseStructureResponse getCourse(Long courseId) {
        try {
            return restClient.get()
                    .uri("/api/v1/courses/{courseId}", courseId)
                    .retrieve()
                    .body(CourseStructureResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NotFoundException("Course not found: " + courseId);
        } catch (HttpClientErrorException ex) {
            throw new IllegalStateException("CourseService returned " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new IllegalStateException("CourseService course request failed");
        }
    }
}
