package com.chatassist.chat.repository;

import com.chatassist.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            select m from ChatMessage m
            where (m.senderUsername = :userA and m.receiverUsername = :userB)
               or (m.senderUsername = :userB and m.receiverUsername = :userA)
               or (m.receiverUsername = :userA and m.contextUsername = :userB)
               or (m.receiverUsername = :userB and m.contextUsername = :userA)
            order by m.sentAt asc
            """)
    List<ChatMessage> findConversation(@Param("userA") String userA, @Param("userB") String userB);

    @Query(value = """
            SELECT COUNT(DISTINCT peers.peer_username)
            FROM (
                SELECT m.receiver_username AS peer_username
                FROM chat_messages m
                WHERE m.sender_username = :username
                  AND DATE(m.sent_at) = :date

                UNION

                SELECT m.sender_username AS peer_username
                FROM chat_messages m
                WHERE m.receiver_username = :username
                  AND DATE(m.sent_at) = :date
            ) peers
            WHERE peers.peer_username <> :username
              AND peers.peer_username NOT IN ('bot', 'aid')
            """, nativeQuery = true)
    long countDistinctPeersForDate(@Param("username") String username, @Param("date") LocalDate date);

    @Query(value = """
            SELECT paired.username, COUNT(DISTINCT paired.peer_username) AS chat_peer_count
            FROM (
                SELECT m.sender_username AS username, m.receiver_username AS peer_username
                FROM chat_messages m
                WHERE DATE(m.sent_at) = :date

                UNION ALL

                SELECT m.receiver_username AS username, m.sender_username AS peer_username
                FROM chat_messages m
                WHERE DATE(m.sent_at) = :date
            ) paired
            WHERE paired.username NOT IN ('bot', 'aid')
              AND paired.peer_username <> paired.username
              AND paired.peer_username NOT IN ('bot', 'aid')
            GROUP BY paired.username
            """, nativeQuery = true)
    List<Object[]> countDistinctPeersForAllUsersOnDate(@Param("date") LocalDate date);
}
