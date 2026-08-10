package com.pvmgroupfinder.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class GroupListing
{
    private String id;
    private String hostRsn;
    private String activity;
    private int teamSize;
    private int currentSize;
    private Integer experienceKc;
    private String role;
    private String language;
    private String region;
    private String note;
    private String status;
    private int version;
    private boolean isHost;
    private boolean ready;
    private Integer preferredWorld;
    private boolean useDiscord;
    private String discordContact;
    private List<GroupMember> members = Collections.emptyList();
    private String createdAt;
    private String expiresAt;
}
