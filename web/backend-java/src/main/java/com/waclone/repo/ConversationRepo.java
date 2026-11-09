package com.waclone.repo;

import com.waclone.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepo extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByExternalId(String externalId);
}
