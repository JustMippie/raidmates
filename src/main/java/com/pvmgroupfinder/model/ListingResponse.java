package com.pvmgroupfinder.model;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class ListingResponse
{
    private List<GroupListing> listings = Collections.emptyList();
}

