package com.waclone.repo;

import com.waclone.model.Conversation;
import com.waclone.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParticipantRepo extends JpaRepository<Participant, Participant.Key> {

    List<Participant> findByConversation(Conversation conversation);

    @Query("select p.conversation from Participant p where p.user.username = :username")
    List<Conversation> conversationsOf(@Param("username") String username);
}
