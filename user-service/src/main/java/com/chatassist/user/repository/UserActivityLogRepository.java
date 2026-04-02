package com.chatassist.user.repository;

import com.chatassist.user.entity.ActivityEventType;
import com.chatassist.user.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    @Query("SELECT COUNT(l) FROM UserActivityLog l " +
           "WHERE l.username = :username " +
           "AND l.activityDate = :date " +
           "AND l.eventType = :eventType")
    long countByUsernameAndDateAndType(
            @Param("username")  String username,
            @Param("date")      LocalDate date,
            @Param("eventType") ActivityEventType eventType);

    /** Returns one row per username: [username, loginCount, logoutCount] for the given date. */
    @Query(nativeQuery = true,
           value = "SELECT username, " +
                   "SUM(CASE WHEN event_type = 'LOGIN'  THEN 1 ELSE 0 END) AS loginCount, " +
                   "SUM(CASE WHEN event_type = 'LOGOUT' THEN 1 ELSE 0 END) AS logoutCount " +
                   "FROM user_activity_log WHERE activity_date = :date " +
                   "GROUP BY username")
    List<Object[]> findActivityCountsForDate(@Param("date") LocalDate date);
}

