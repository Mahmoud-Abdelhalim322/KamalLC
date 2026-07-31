package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.NinjaShiftRow;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Undoes the day/month swap that a supplier export picks up when it is opened — or
 * round-tripped through CSV — under a locale whose short-date order differs from the one
 * it was written with. {@code 01/06/2026} (1 June) is then read as 6 January and saved
 * back as that serial, so by the time the workbook reaches us the damage is already
 * baked into the numbers and no amount of careful cell reading recovers it.
 *
 * <p>Only days 1–12 can be swapped, which is exactly what makes the corruption
 * recoverable: the rows that survived intact pin down the month the export covers, and a
 * corrupted row is one that lands back in that month when its day and month are exchanged.
 * Two independent rules are applied:
 *
 * <ul>
 *   <li><b>Start</b> — repaired only when the file's unambiguous rows (day &gt; 12) all sit
 *       in a single calendar month, and swapping the row's start lands it in that month.
 *       A file that genuinely spans two months leaves no anchor, so nothing is touched.</li>
 *   <li><b>End and check-in</b> — repaired when they are impossible relative to the start
 *       (a shift cannot end before it begins) and the swap makes them possible. This needs
 *       no anchor, so it also catches a file whose starts are all intact and only a single
 *       end date was mangled.</li>
 * </ul>
 *
 * <p>Every repair is recorded as a row warning, so a corrected date is visible in the
 * import's issue list rather than applied behind the operator's back.
 */
final class ShiftDateRepair {

    /** Above this, a day-of-month cannot be mistaken for a month, so the date is trustworthy. */
    private static final int AMBIGUOUS_MAX_DAY = 12;

    /** Generous upper bound on a shift's span — long enough for any real roster, short enough
     *  that a month-scale error stands out. */
    private static final Duration MAX_SHIFT_SPAN = Duration.ofHours(48);

    /** A captain may show up before the shift opens, but not by days. */
    private static final Duration MAX_EARLY_CHECKIN = Duration.ofHours(12);

    private ShiftDateRepair() {}

    /** Repairs the rows in place. Rows with an unreadable start are left alone. */
    static void repair(List<NinjaShiftRow> rows, ZoneId zone) {
        YearMonth anchor = anchorMonth(rows, zone);
        for (NinjaShiftRow row : rows) {
            if (anchor != null) {
                repairStart(row, anchor, zone);
            }
            repairEndAndCheckin(row, zone);
        }
    }

    /**
     * The calendar month this export covers, according to the rows that cannot have been
     * swapped. Null when they disagree — a period spanning two months, or a file with no
     * unambiguous row at all — in which case a start date carries no evidence against it.
     */
    private static YearMonth anchorMonth(List<NinjaShiftRow> rows, ZoneId zone) {
        Set<YearMonth> months = new LinkedHashSet<>();
        for (NinjaShiftRow row : rows) {
            LocalDateTime start = local(row.getScheduledStart(), zone);
            if (start != null && start.getDayOfMonth() > AMBIGUOUS_MAX_DAY) {
                months.add(YearMonth.from(start));
            }
        }
        return months.size() == 1 ? months.iterator().next() : null;
    }

    private static void repairStart(NinjaShiftRow row, YearMonth anchor, ZoneId zone) {
        LocalDateTime start = local(row.getScheduledStart(), zone);
        if (start == null || YearMonth.from(start).equals(anchor)) return;

        LocalDateTime fixed = swapDayAndMonth(start);
        if (fixed == null || !YearMonth.from(fixed).equals(anchor)) return;

        row.setScheduledStart(offset(fixed, zone));
        row.setShiftDate(fixed.toLocalDate());
        warn(row, "صُحّح تاريخ بداية الشفت من " + start.toLocalDate() + " إلى " + fixed.toLocalDate()
                + " — اليوم والشهر مقلوبان في الملف");
    }

    private static void repairEndAndCheckin(NinjaShiftRow row, ZoneId zone) {
        LocalDateTime start = local(row.getScheduledStart(), zone);
        if (start == null) return;

        LocalDateTime end = local(row.getScheduledEnd(), zone);
        LocalDateTime fixedEnd = repaired(start, end, Duration.ZERO, MAX_SHIFT_SPAN);
        if (fixedEnd != null) {
            row.setScheduledEnd(offset(fixedEnd, zone));
            warn(row, "صُحّح تاريخ نهاية الشفت من " + end.toLocalDate() + " إلى " + fixedEnd.toLocalDate()
                    + " — اليوم والشهر مقلوبان في الملف");
        }

        LocalDateTime checkin = local(row.getActualCheckin(), zone);
        LocalDateTime fixedCheckin = repaired(start, checkin, MAX_EARLY_CHECKIN.negated(), MAX_SHIFT_SPAN);
        if (fixedCheckin != null) {
            row.setActualCheckin(offset(fixedCheckin, zone));
            warn(row, "صُحّح تاريخ تسجيل الحضور من " + checkin.toLocalDate() + " إلى "
                    + fixedCheckin.toLocalDate() + " — اليوم والشهر مقلوبان في الملف");
        }
    }

    /**
     * The swapped form of {@code value}, but only when {@code value} is impossible relative
     * to {@code start} and the swap makes it possible. Null means "leave it alone" — either
     * it was already fine, or the swap does not explain it and guessing further would be
     * inventing data.
     */
    private static LocalDateTime repaired(LocalDateTime start, LocalDateTime value,
                                          Duration min, Duration max) {
        if (value == null || within(start, value, min, max)) return null;
        LocalDateTime swapped = swapDayAndMonth(value);
        return swapped != null && within(start, swapped, min, max) ? swapped : null;
    }

    private static boolean within(LocalDateTime start, LocalDateTime value,
                                  Duration min, Duration max) {
        return !value.isBefore(start.plus(min)) && !value.isAfter(start.plus(max));
    }

    /** Exchanges day and month, keeping the time of day. Null when no such date exists. */
    private static LocalDateTime swapDayAndMonth(LocalDateTime value) {
        int day = value.getDayOfMonth();
        int month = value.getMonthValue();
        if (day > AMBIGUOUS_MAX_DAY || day == month) return null;
        try {
            // Day first, so the intermediate value is never an invalid date such as 31 Feb.
            return value.withDayOfMonth(1).withMonth(day).withDayOfMonth(month);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static LocalDateTime local(OffsetDateTime value, ZoneId zone) {
        return value != null ? value.atZoneSameInstant(zone).toLocalDateTime() : null;
    }

    private static OffsetDateTime offset(LocalDateTime value, ZoneId zone) {
        return value.atZone(zone).toOffsetDateTime();
    }

    private static void warn(NinjaShiftRow row, String message) {
        List<String> warnings = row.getWarnings();
        if (!(warnings instanceof ArrayList<String>)) {
            warnings = new ArrayList<>(Objects.requireNonNullElse(warnings, List.of()));
            row.setWarnings(warnings);
        }
        warnings.add(message);
    }
}
