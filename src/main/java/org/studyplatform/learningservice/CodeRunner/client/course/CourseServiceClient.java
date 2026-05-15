package org.studyplatform.learningservice.CodeRunner.client.course;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.studyplatform.learningservice.CodeRunner.config.CourseServiceProperties;
import org.studyplatform.learningservice.common.exception.NotFoundException;

@Component
public class CourseServiceClient implements CourseExecutionPackageProvider {

    private final RestClient restClient;
    private final CourseServiceProperties properties;

    public CourseServiceClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public CourseItemExecutionPackage getExecutionPackage(Long itemId, String authorizationHeader) {
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/api/v1/internal/course-items/{itemId}/execution-package", itemId)
                    .header(properties.getInternalApiKeyHeader(), properties.getInternalApiKey());

            if (StringUtils.hasText(authorizationHeader)) {
                request = request.header("Authorization", authorizationHeader);
            }

            return request.retrieve()
                    .body(CourseItemExecutionPackage.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NotFoundException("Course item not found: " + itemId);
        } catch (HttpClientErrorException ex) {
            throw new IllegalStateException("CourseService returned " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new IllegalStateException("CourseService request failed");
        }
    }
}
