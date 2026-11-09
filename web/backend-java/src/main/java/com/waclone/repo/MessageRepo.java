package com.waclone.repo;

import com.waclone.model.Conversation;
import com.waclone.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepo extends JpaRepository<Message, Long> {

    List<Message> findByConversationOrderBySentAtAsc(Conversation conversation);

    Optional<Message> findFirstByConversationAndClientId(Conversation conversation, String clientId);

    List<Message> findByConversationOrderBySentAtDesc(Conversation conversation, Pageable page);

    List<Message> findByConversationAndSentAtLessThanOrderBySentAtDesc(
            Conversation conversation, long before, Pageable page);
}
