package com.lifeos.coach.conversation.repository;

import com.lifeos.coach.conversation.entity.Conversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    /** Most recently active first. */
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    @Modifying
    @Query("DELETE FROM Conversation c WHERE c.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
