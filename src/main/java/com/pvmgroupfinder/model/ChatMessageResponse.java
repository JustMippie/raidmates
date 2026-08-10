package com.pvmgroupfinder.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class ChatMessageResponse
{
    private List<ChatMessage> messages = Collections.emptyList();
}

