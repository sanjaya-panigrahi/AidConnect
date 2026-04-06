package com.chatassist.user.repository;

import com.chatassist.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByUsernameNotOrderByBotAscFirstNameAsc(String username);

    // Optimized: Use exists query instead of loading full entity
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM AppUser u WHERE u.username = :username")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM AppUser u WHERE u.email = :email")
    boolean existsByEmail(@Param("email") String email);

    // Batch update: single query with status update
    @Modifying
    @Query("UPDATE AppUser u SET u.online = :online, u.lastActive = CURRENT_TIMESTAMP WHERE u.username = :username")
    void updateOnlineStatus(@Param("username") String username, @Param("online") boolean online);

}
