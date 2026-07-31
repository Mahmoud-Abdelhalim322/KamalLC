package com.kamal.lc.shift.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** Query criteria for the shifts screen. Every field is optional. */
@Data
public class ShiftFilter {
    private LocalDate from;
    private LocalDate to;
    private UUID captainId;
    private String zoneName;
    private String search;
    /** null = both; true = attended only; false = absences only. */
    private Boolean attended;
    private Boolean excluded;
    /** true = only rows whose supplier ID is not linked to a captain. */
    private Boolean unmatched;
}
