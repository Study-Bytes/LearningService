package org.studyplatform.learningservice.learn;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ConflictException;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LearningEnrollmentService {

    private final CourseAvailabilityClient courseAvailabilityClient;
    private final CourseStructureClient courseStructureClient;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final TaskProgressRepository taskProgressRepository;

    public LearningEnrollmentService(
            CourseAvailabilityClient courseAvailabilityClient,
            CourseStructureClient courseStructureClient,
            CourseEnrollmentRepository courseEnrollmentRepository,
            TaskProgressRepository taskProgressRepository
    ) {
        this.courseAvailabilityClient = courseAvailabilityClient;
        this.courseStructureClient = courseStructureClient;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.taskProgressRepository = taskProgressRepository;
    }

    @Transactional
    public EnrollCourseResponse enroll(Long userId, Long courseId) {
        CourseAvailabilityResponse availability = courseAvailabilityClient.getAvailability(courseId);
        if (availability == null || !Boolean.TRUE.equals(availability.availableForEnrollment())) {
            throw new ForbiddenException("Course is not available for enrollment");
        }

        if (courseEnrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new ConflictException("User is already enrolled in course");
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUserId(userId);
        enrollment.setCourseId(courseId);
        enrollment.setStatus(ProgressStatus.NOT_STARTED);
        enrollment.setProgressPercent(BigDecimal.ZERO);
        enrollment.setCompletedTasksCount(0);
        enrollment.setTotalTasksCount(0);
        enrollment.setTotalScore(0);

        return EnrollCourseResponse.fromEntity(courseEnrollmentRepository.save(enrollment));
    }

    @Transactional(readOnly = true)
    public List<MyCourseEnrollmentResponse> getMyCourses(Long userId) {
        return courseEnrollmentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(MyCourseEnrollmentResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public LearningCourseStateResponse getCourseState(Long userId, Long courseId) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        CourseStructureResponse course = courseStructureClient.getCourse(courseId);
        List<Long> orderedItemIds = orderedItemIds(course);
        Map<Long, TaskProgress> progressByTaskId = taskProgressRepository.findByUserIdAndCourseId(userId, courseId)
                .stream()
                .collect(Collectors.toMap(
                        TaskProgress::getTaskId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        List<LearningCourseItemStateResponse> itemStates = orderedItemIds.stream()
                .map(itemId -> toItemState(itemId, progressByTaskId.get(itemId)))
                .toList();

        return new LearningCourseStateResponse(
                enrollment.getCourseId(),
                enrollment.getProgressPercent().intValue(),
                enrollment.getStatus(),
                resolveNextItemId(orderedItemIds, progressByTaskId),
                itemStates
        );
    }

    private List<Long> orderedItemIds(CourseStructureResponse course) {
        if (course == null || course.modules() == null || course.modules().isEmpty()) {
            return List.of();
        }

        List<CourseStructureResponse.Module> modules = new ArrayList<>(course.modules());
        modules.sort(Comparator.comparingInt(module -> orZero(module.orderIndex())));

        List<Long> itemIds = new ArrayList<>();
        for (CourseStructureResponse.Module module : modules) {
            if (module.items() == null || module.items().isEmpty()) {
                continue;
            }
            List<CourseStructureResponse.Item> items = new ArrayList<>(module.items());
            items.sort(Comparator.comparingInt(item -> orZero(item.orderIndex())));
            for (CourseStructureResponse.Item item : items) {
                if (item.id() != null) {
                    itemIds.add(item.id());
                }
            }
        }
        return itemIds;
    }

    private LearningCourseItemStateResponse toItemState(Long itemId, TaskProgress progress) {
        if (progress == null) {
            return new LearningCourseItemStateResponse(itemId, false, false);
        }
        return new LearningCourseItemStateResponse(
                itemId,
                isCompleted(progress),
                progress.getStatus() == ProgressStatus.LOCKED
        );
    }

    private Long resolveNextItemId(List<Long> orderedItemIds, Map<Long, TaskProgress> progressByTaskId) {
        for (Long itemId : orderedItemIds) {
            TaskProgress progress = progressByTaskId.get(itemId);
            if (progress == null || !isCompleted(progress)) {
                return itemId;
            }
        }
        return null;
    }

    private boolean isCompleted(TaskProgress progress) {
        return Boolean.TRUE.equals(progress.getIsCompleted()) || progress.getStatus() == ProgressStatus.COMPLETED;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
