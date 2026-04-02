package com.chatassist.aid.repository;

import com.chatassist.aid.entity.AidConversationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AidConversationStateRepository extends JpaRepository<AidConversationState, Long> {
    Optional<AidConversationState> findByUsername(String username);
}

