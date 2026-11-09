package com.waclone.model;

public final class Enums {
    private Enums() {}

    public enum Role { ADMIN, USER }

    public enum MemberRole { OWNER, ADMIN, MEMBER }

    public enum ConversationType { DIRECT, GROUP }

    public enum MessageType { TEXT, IMAGE, FILE, SYSTEM }

    public enum MessageStatus { PENDING, SENT, DELIVERED, READ }

    public enum CallType { VOICE, VIDEO }

    public enum CallStatus { MISSED, ACCEPTED, REJECTED, ENDED }
}
