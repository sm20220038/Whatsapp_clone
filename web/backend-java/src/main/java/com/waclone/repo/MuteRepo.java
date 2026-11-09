package com.waclone.repo;

import com.waclone.model.Mute;
import com.waclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MuteRepo extends JpaRepository<Mute, Mute.Key> {
    List<Mute> findByMuter(User muter);
}
