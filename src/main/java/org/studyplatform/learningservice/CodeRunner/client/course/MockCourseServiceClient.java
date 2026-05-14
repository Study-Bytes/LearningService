package org.studyplatform.learningservice.CodeRunner.client.course;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "coderunner.course-service",
        name = "mock-enabled",
        havingValue = "true"
)
public class MockCourseServiceClient implements CourseExecutionPackageProvider {

    @Override
    public CourseItemExecutionPackage getExecutionPackage(Long itemId) {
        return new CourseItemExecutionPackage(
                itemId,
                null,
                null,
                "CODING",
                "Mock task " + itemId,
                "python",
                "",
                new CourseItemExecutionPackage.ExecutionLimits(1500, 256, 256),
                new CourseItemExecutionPackage.ExecutionPolicy(true, true),
                new CourseItemExecutionPackage.EvaluationPolicy("EXACT", true, true),
                List.of(
                        new CourseItemExecutionPackage.ExecutionTest(
                                "open-1",
                                "OPEN",
                                "hello\n",
                                "hello\n",
                                1
                        ),
                        new CourseItemExecutionPackage.ExecutionTest(
                                "hidden-1",
                                "HIDDEN",
                                "world\n",
                                "world\n",
                                2
                        )
                )
        );
    }
}
