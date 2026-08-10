package com.pvmgroupfinder.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class JoinRequestResponse
{
    private List<JoinRequest> requests = Collections.emptyList();
}

