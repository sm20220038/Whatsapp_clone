package com.waclone.repo;

import com.waclone.model.Message;
import com.waclone.model.Reaction;
import com.waclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReactionRepo extends JpaRepository<Reaction, Long> {
    Optional<Reaction> findByMessageAndUser(Message message, User user);
    List<Reaction> findByMessage(Message message);
}
