package com.waclone.repo;

import com.waclone.model.CallLog;
import com.waclone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallLogRepo extends JpaRepository<CallLog, Long> {
    List<CallLog> findTop50ByCallerOrCalleeOrderByStartedAtDesc(User caller, User callee);
}
