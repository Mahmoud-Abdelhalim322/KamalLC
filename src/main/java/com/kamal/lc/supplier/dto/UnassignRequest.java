package com.kamal.lc.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Ends a slot's active assignment. Both fields are optional; validTo defaults to today. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnassignRequest {

    private LocalDate validTo;
    private String reason;
}
