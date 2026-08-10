package com.pvmgroupfinder.model;

import lombok.Data;

@Data
public class GroupMember
{
    private String rsn;
    private String role;
    private Integer experienceKc;
    private boolean isHost;
    private boolean ready;
    private String joinedAt;
}
