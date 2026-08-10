package com.pvmgroupfinder.model;

import lombok.Data;

@Data
public class ChatMessage
{
    private String id;
    private String senderRsn;
    private String body;
    private String createdAt;
}

