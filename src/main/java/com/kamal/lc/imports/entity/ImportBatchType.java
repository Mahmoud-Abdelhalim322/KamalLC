package com.kamal.lc.imports.entity;

/**
 * The two shapes of file the system imports. They share {@code import_batch} but come
 * from different sources and mean different things, so a batch records which it is.
 */
public enum ImportBatchType {

    /** Kamal's own captain roster: KSLSC codes, supplier IDs, and personal details. */
    CAPTAINS,

    /** The supplier's shift export — the daily file that feeds reports and payroll. */
    SHIFTS
}
