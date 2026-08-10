package com.pvmgroupfinder.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateListingRequest
{
    String hostRsn;
    String activity;
    int teamSize;
    Integer experienceKc;
    String role;
    String language;
    String region;
    String note;
    Integer preferredWorld;
    boolean useDiscord;
    String discordContact;
}
