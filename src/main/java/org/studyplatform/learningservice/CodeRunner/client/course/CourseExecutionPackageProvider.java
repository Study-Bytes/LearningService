package org.studyplatform.learningservice.CodeRunner.client.course;

public interface CourseExecutionPackageProvider {

    CourseItemExecutionPackage getExecutionPackage(Long itemId);
}
