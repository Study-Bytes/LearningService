package org.studyplatform.learningservice.learn;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ConflictException;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.common.exception.NotFoundException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository.CourseLeaderboardRow;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LearningEnrollmentService {

    private final CourseAvailabilityClient courseAvailabilityClient;
    private final CourseOwnershipClient courseOwnershipClient;
    private final CourseStructureClient courseStructureClient;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final TaskProgressRepository taskProgressRepository;

    public LearningEnrollmentService(
            CourseAvailabilityClient courseAvailabilityClient,
            CourseOwnershipClient courseOwnershipClient,
            CourseStructureClient courseStructureClient,
            CourseEnrollmentRepository courseEnrollmentRepository,
            TaskProgressRepository taskProgressRepository
    ) {
        this.courseAvailabilityClient = courseAvailabilityClient;
        this.courseOwnershipClient = courseOwnershipClient;
        this.courseStructureClient = courseStructureClient;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.taskProgressRepository = taskProgressRepository;
    }

    @Transactional
    public EnrollCourseResponse enroll(Long userId, Long courseId, String nickname) {
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
        enrollment.setNickname(resolveNickname(userId, nickname));
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

    @Transactional
    public CourseLeaderboardResponse getCourseLeaderboard(Long userId, Long courseId, String nickname) {
        CourseEnrollment currentEnrollment = courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElse(null);

        if (currentEnrollment != null && hasText(nickname) && !nickname.equals(currentEnrollment.getNickname())) {
            currentEnrollment.setNickname(resolveNickname(userId, nickname));
            courseEnrollmentRepository.saveAndFlush(currentEnrollment);
        }

        List<CourseLeaderboardEntryResponse> top = courseEnrollmentRepository.findTopLeaderboardRows(courseId)
                .stream()
                .map(this::toLeaderboardEntry)
                .toList();

        if (currentEnrollment == null) {
            if (!courseOwnershipClient.isCourseOwner(courseId, userId)) {
                throw new ForbiddenException("User is not enrolled in course");
            }
            return new CourseLeaderboardResponse(courseId, LeaderboardViewerRole.TEACHER, top, null);
        }

        CourseLeaderboardEntryResponse currentUser = courseEnrollmentRepository
                .findLeaderboardRowForUser(courseId, userId)
                .map(this::toLeaderboardEntry)
                .orElseThrow(() -> new IllegalStateException("Current user is missing from course leaderboard"));

        return new CourseLeaderboardResponse(courseId, LeaderboardViewerRole.LEARNER, top, currentUser);
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

    @Transactional
    public LearningItemStateResponse getItemState(Long userId, Long courseId, Long itemId) {
        courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        CourseStructureResponse course = courseStructureClient.getCourse(courseId);
        List<OrderedCourseItem> orderedItems = orderedCourseItems(course);
        int currentIndex = findItemIndex(orderedItems, itemId);
        if (currentIndex < 0) {
            throw new NotFoundException("Course item not found in course");
        }

        OrderedCourseItem currentItem = orderedItems.get(currentIndex);
        TaskProgress progress = taskProgressRepository.findByUserIdAndCourseIdAndTaskId(userId, courseId, itemId)
                .map(existing -> touchTaskProgress(existing, currentItem.moduleId()))
                .orElseGet(() -> createTaskProgress(userId, courseId, currentItem.moduleId(), itemId));

        return new LearningItemStateResponse(
                courseId,
                itemId,
                toItemProgress(progress),
                new LearningItemNavigationResponse(
                        currentIndex > 0 ? orderedItems.get(currentIndex - 1).itemId() : null,
                        currentIndex + 1 < orderedItems.size() ? orderedItems.get(currentIndex + 1).itemId() : null
                )
        );
    }

    private List<Long> orderedItemIds(CourseStructureResponse course) {
        return orderedCourseItems(course)
                .stream()
                .map(OrderedCourseItem::itemId)
                .toList();
    }

    private List<OrderedCourseItem> orderedCourseItems(CourseStructureResponse course) {
        if (course == null || course.modules() == null || course.modules().isEmpty()) {
            return List.of();
        }

        List<CourseStructureResponse.Module> modules = new ArrayList<>(course.modules());
        modules.sort(Comparator.comparingInt(module -> orZero(module.orderIndex())));

        List<OrderedCourseItem> itemIds = new ArrayList<>();
        for (CourseStructureResponse.Module module : modules) {
            if (module.items() == null || module.items().isEmpty()) {
                continue;
            }
            List<CourseStructureResponse.Item> items = new ArrayList<>(module.items());
            items.sort(Comparator.comparingInt(item -> orZero(item.orderIndex())));
            for (CourseStructureResponse.Item item : items) {
                if (item.id() != null && module.id() != null) {
                    itemIds.add(new OrderedCourseItem(item.id(), module.id()));
                }
            }
        }
        return itemIds;
    }

    private int findItemIndex(List<OrderedCourseItem> orderedItems, Long itemId) {
        for (int i = 0; i < orderedItems.size(); i++) {
            if (orderedItems.get(i).itemId().equals(itemId)) {
                return i;
            }
        }
        return -1;
    }

    private TaskProgress createTaskProgress(Long userId, Long courseId, Long moduleId, Long itemId) {
        LocalDateTime now = LocalDateTime.now();
        TaskProgress progress = new TaskProgress();
        progress.setUserId(userId);
        progress.setCourseId(courseId);
        progress.setModuleId(moduleId);
        progress.setTaskId(itemId);
        progress.setStatus(ProgressStatus.NOT_STARTED);
        progress.setAttemptsCount(0);
        progress.setBestScore(0);
        progress.setLastScore(0);
        progress.setIsCompleted(false);
        progress.setFirstOpenedAt(now);
        progress.setLastActivityAt(now);
        return taskProgressRepository.save(progress);
    }

    private TaskProgress touchTaskProgress(TaskProgress progress, Long moduleId) {
        LocalDateTime now = LocalDateTime.now();
        if (progress.getFirstOpenedAt() == null) {
            progress.setFirstOpenedAt(now);
        }
        if (moduleId != null && !moduleId.equals(progress.getModuleId())) {
            progress.setModuleId(moduleId);
        }
        progress.setLastActivityAt(now);
        return progress;
    }

    private LearningItemProgressResponse toItemProgress(TaskProgress progress) {
        int attemptsCount = orZero(progress.getAttemptsCount());
        return new LearningItemProgressResponse(
                progress.getStatus(),
                attemptsCount,
                attemptsCount == 0 ? null : progress.getLastScore()
        );
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

    private CourseLeaderboardEntryResponse toLeaderboardEntry(CourseLeaderboardRow row) {
        return new CourseLeaderboardEntryResponse(
                row.getUserId(),
                row.getRankPlace() == null ? null : Math.toIntExact(row.getRankPlace()),
                row.getNickname(),
                row.getProgressPercent() == null ? BigDecimal.ZERO : row.getProgressPercent()
        );
    }

    private String resolveNickname(Long userId, String nickname) {
        if (hasText(nickname)) {
            String trimmed = nickname.trim();
            return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
        }
        return "User " + userId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record OrderedCourseItem(Long itemId, Long moduleId) {
    }
}
