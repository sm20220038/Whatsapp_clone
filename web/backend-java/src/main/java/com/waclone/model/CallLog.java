package com.waclone.model;

import com.waclone.model.Enums.CallStatus;
import com.waclone.model.Enums.CallType;
import jakarta.persistence.*;

@Entity
@Table(name = "call_log")
public class CallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long callId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "callerId",
                foreignKey = @ForeignKey(name = "fk_call_log_caller",
                        foreignKeyDefinition = "FOREIGN KEY (caller_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User caller;

    @ManyToOne(optional = false)
    @JoinColumn(name = "calleeId",
                foreignKey = @ForeignKey(name = "fk_call_log_callee",
                        foreignKeyDefinition = "FOREIGN KEY (callee_id) REFERENCES users (user_id) ON UPDATE CASCADE"))
    private User callee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CallType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CallStatus status;

    private long startedAt;
    private long endedAt;

    protected CallLog() {}

    public CallLog(User caller, User callee, CallType type, CallStatus status) {
        this.caller = caller;
        this.callee = callee;
        this.type = type;
        this.status = status;
        this.startedAt = System.currentTimeMillis();
    }

    public Long getCallId() { return callId; }
    public User getCaller() { return caller; }
    public User getCallee() { return callee; }
    public CallType getType() { return type; }
    public CallStatus getStatus() { return status; }
    public void setStatus(CallStatus s) { this.status = s; }
    public long getStartedAt() { return startedAt; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long t) { this.endedAt = t; }
}
