package com.pvmgroupfinder.model;

import lombok.Data;

@Data
public class JoinRequest
{
    private String id;
    private String listingId;
    private String requesterRsn;
    private String activity;
    private String role;
    private String message;
    private Integer experienceKc;
    private String status;
    private String createdAt;
}
