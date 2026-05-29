package org.studyplatform.learningservice.CodeRunner.client.course;

public interface QuizEvaluationPackageProvider {

    QuizEvaluationPackage getQuizEvaluationPackage(Long itemId, String authorizationHeader);
}
