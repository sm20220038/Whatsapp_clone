package com.waclone.repo;

import com.waclone.model.KeyBundle;
import com.waclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KeyBundleRepo extends JpaRepository<KeyBundle, Long> {
    Optional<KeyBundle> findByUser(User user);
}
