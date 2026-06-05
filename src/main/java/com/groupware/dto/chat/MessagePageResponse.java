package com.groupware.dto.chat;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MessagePageResponse {
    private List<MessageResponse> messages;
    private boolean hasMore;
}
