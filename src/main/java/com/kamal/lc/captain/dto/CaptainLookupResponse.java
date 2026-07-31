package com.kamal.lc.captain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Lightweight captain projection used by search-as-you-type pickers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainLookupResponse {

    private UUID id;
    private String kamalCode;
    private String fullNameAr;
    private String captainType;
}
