package com.tastenotebook.dto;

import com.tastenotebook.domain.Friend;
import java.time.Instant;

public class FriendResponse {
    public Long id;
    public String name;
    public Instant createdAt;

    public FriendResponse() {}

    public static FriendResponse from(Friend f) {
        FriendResponse r = new FriendResponse();
        r.id = f.getId();
        r.name = f.getName();
        r.createdAt = f.getCreatedAt();
        return r;
    }
}
