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
public class CourseQuizEvaluationClient implements QuizEvaluationPackageProvider {

    private static final Logger log = LoggerFactory.getLogger(CourseQuizEvaluationClient.class);

    private final RestClient restClient;
    private final CourseServiceProperties properties;

    public CourseQuizEvaluationClient(RestClient.Builder builder, CourseServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    @Override
    public QuizEvaluationPackage getQuizEvaluationPackage(Long itemId, String authorizationHeader) {
        long startedAt = System.nanoTime();
        log.info("CourseService quiz evaluation package request itemId={}", itemId);
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri("/api/v1/internal/course-items/{itemId}/quiz-evaluation-package", itemId)
                    .header(properties.getInternalApiKeyHeader(), properties.getInternalApiKey());

            if (StringUtils.hasText(authorizationHeader)) {
                request = request.header("Authorization", authorizationHeader);
            }

            QuizEvaluationPackage response = request.retrieve()
                    .body(QuizEvaluationPackage.class);
            log.info(
                    "CourseService quiz evaluation package response itemId={} courseId={} moduleId={} options={} durationMs={}",
                    itemId,
                    response == null ? null : response.courseId(),
                    response == null ? null : response.moduleId(),
                    response == null || response.options() == null ? 0 : response.options().size(),
                    elapsedMs(startedAt)
            );
            return response;
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("CourseService quiz evaluation package not found itemId={} durationMs={}", itemId, elapsedMs(startedAt));
            throw new NotFoundException("Course item not found: " + itemId);
        } catch (HttpClientErrorException ex) {
            log.warn(
                    "CourseService quiz evaluation package error itemId={} status={} durationMs={}",
                    itemId,
                    ex.getStatusCode().value(),
                    elapsedMs(startedAt)
            );
            throw new IllegalStateException("CourseService returned " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            log.warn("CourseService quiz evaluation package request failed itemId={} durationMs={}", itemId, elapsedMs(startedAt));
            throw new IllegalStateException("CourseService quiz evaluation package request failed");
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
