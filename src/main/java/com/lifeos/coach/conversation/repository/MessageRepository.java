package com.lifeos.coach.conversation.repository;

import com.lifeos.coach.conversation.entity.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Oldest first. */
    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

    @Modifying
    @Query("DELETE FROM Message m WHERE m.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
