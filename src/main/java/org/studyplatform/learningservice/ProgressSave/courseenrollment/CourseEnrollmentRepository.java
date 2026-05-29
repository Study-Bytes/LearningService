package org.studyplatform.learningservice.courseenrollment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    Optional<CourseEnrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CourseEnrollment e where e.userId = :userId and e.courseId = :courseId")
    Optional<CourseEnrollment> findByUserIdAndCourseIdForUpdate(
            @Param("userId") Long userId,
            @Param("courseId") Long courseId
    );

    List<CourseEnrollment> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(
            value = """
                    SELECT ranked.rank_place AS "rankPlace",
                           ranked.user_id AS "userId",
                           ranked.nickname AS nickname,
                           ranked.progress_percent AS "progressPercent"
                    FROM (
                        SELECT row_number() OVER (
                                   ORDER BY progress_percent DESC,
                                            completed_tasks_count DESC,
                                            total_score DESC,
                                            updated_at ASC,
                                            user_id ASC
                               ) AS rank_place,
                               user_id,
                               nickname,
                               progress_percent
                        FROM course_enrollments
                        WHERE course_id = :courseId
                    ) ranked
                    ORDER BY ranked.rank_place
                    LIMIT 10
                    """,
            nativeQuery = true
    )
    List<CourseLeaderboardRow> findTopLeaderboardRows(@Param("courseId") Long courseId);

    @Query(
            value = """
                    SELECT ranked.rank_place AS "rankPlace",
                           ranked.user_id AS "userId",
                           ranked.nickname AS nickname,
                           ranked.progress_percent AS "progressPercent"
                    FROM (
                        SELECT row_number() OVER (
                                   ORDER BY progress_percent DESC,
                                            completed_tasks_count DESC,
                                            total_score DESC,
                                            updated_at ASC,
                                            user_id ASC
                               ) AS rank_place,
                               user_id,
                               nickname,
                               progress_percent
                        FROM course_enrollments
                        WHERE course_id = :courseId
                    ) ranked
                    WHERE ranked.user_id = :userId
                    """,
            nativeQuery = true
    )
    Optional<CourseLeaderboardRow> findLeaderboardRowForUser(
            @Param("courseId") Long courseId,
            @Param("userId") Long userId
    );

    interface CourseLeaderboardRow {
        Long getRankPlace();

        Long getUserId();

        String getNickname();

        BigDecimal getProgressPercent();
    }
}
