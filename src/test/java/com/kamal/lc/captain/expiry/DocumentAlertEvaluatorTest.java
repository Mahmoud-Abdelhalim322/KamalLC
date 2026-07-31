package com.kamal.lc.captain.expiry;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.settings.entity.AppSetting;
import com.kamal.lc.settings.repository.AppSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the verdicts the office reads off the dashboard and the captains table.
 *
 * <p>HR used to write these by hand in a sheet, one status column per date column. Getting
 * them wrong in either direction has a cost: a false ساري means a captain keeps working on a
 * lapsed permit, and a false منتهى sends someone to renew a document that has months left.
 */
@DisplayName("DocumentAlertEvaluator")
class DocumentAlertEvaluatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 30);

    private AppSettingRepository settingRepo;
    private DocumentAlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        settingRepo = mock(AppSettingRepository.class);
        seedWindows(30, 60, 30, 30, 30);
        evaluator = new DocumentAlertEvaluator(new DocumentAlertPolicy(settingRepo));
    }

    /** The five DOCUMENT_ALERT rows as V20 seeds them, in enum order. */
    private void seedWindows(int iqama, int workPermit, int insurance, int passport, int licence) {
        when(settingRepo.findEffectiveOn(any())).thenReturn(List.of(
                setting("IQAMA_ALERT_DAYS", iqama),
                setting("WORK_PERMIT_ALERT_DAYS", workPermit),
                setting("INSURANCE_ALERT_DAYS", insurance),
                setting("PASSPORT_ALERT_DAYS", passport),
                setting("LICENCE_ALERT_DAYS", licence)));
    }

    private AppSetting setting(String key, int days) {
        return AppSetting.builder()
                .settingKey(key)
                .settingGroup("DOCUMENT_ALERT")
                .value(String.valueOf(days))
                .valueType("INTEGER")
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    /** A captain whose iqama sits on the given date and whose other documents are all clear. */
    private Captain withIqama(LocalDate iqamaExpiry) {
        return captain(iqamaExpiry, TODAY.plusYears(1), TODAY.plusYears(1),
                TODAY.plusYears(1), TODAY.plusYears(1));
    }

    private Captain captain(LocalDate iqama, LocalDate workPermit, LocalDate insurance,
                            LocalDate passport, LocalDate licence) {
        return Captain.builder()
                .kamalCode("KSC0001")
                .captainType("EMPLOYEE")
                .fullNameAr("علاء الدين عمر علي الامين")
                .status("ACTIVE")
                .iqamaExpiry(iqama)
                .workPermitExpiry(workPermit)
                .insuranceExpiry(insurance)
                .passportExpiry(passport)
                .licenceExpiry(licence)
                .build();
    }

    private Map<DocumentKind, DocumentStatus> statuses(Captain captain) {
        return evaluator.evaluate(captain, TODAY).stream()
                .collect(Collectors.toMap(DocumentAlert::kind, DocumentAlert::status));
    }

    private DocumentStatus statusOf(Captain captain, DocumentKind kind) {
        return statuses(captain).get(kind);
    }

    @Nested
    @DisplayName("one document against its window")
    class SingleDocument {

        @Test
        @DisplayName("no date on file reads as غير مسجل, not as valid")
        void missingDateIsFlagged() {
            // The sheet marks a blank date منتهى by hand. Treating it as valid — which the old
            // "expiry IS NOT NULL AND expiry <= cutoff" query did — hides the captain entirely.
            assertThat(statusOf(withIqama(null), DocumentKind.IQAMA))
                    .isEqualTo(DocumentStatus.MISSING);
        }

        @Test
        @DisplayName("yesterday is منتهى")
        void pastDateIsExpired() {
            assertThat(statusOf(withIqama(TODAY.minusDays(1)), DocumentKind.IQAMA))
                    .isEqualTo(DocumentStatus.EXPIRED);
        }

        @Test
        @DisplayName("today is على وشك الانتهاء, not yet منتهى")
        void todayIsExpiringNotExpired() {
            assertThat(statusOf(withIqama(TODAY), DocumentKind.IQAMA))
                    .isEqualTo(DocumentStatus.EXPIRING);
        }

        @Test
        @DisplayName("the last day of the window is still على وشك الانتهاء")
        void windowEdgeIsExpiring() {
            // Inclusive on purpose: a document must never go from ساري straight to منتهى
            // without a day of warning in between.
            assertThat(statusOf(withIqama(TODAY.plusDays(30)), DocumentKind.IQAMA))
                    .isEqualTo(DocumentStatus.EXPIRING);
        }

        @Test
        @DisplayName("one day past the window is ساري")
        void beyondWindowIsValid() {
            assertThat(statusOf(withIqama(TODAY.plusDays(31)), DocumentKind.IQAMA))
                    .isEqualTo(DocumentStatus.VALID);
        }
    }

    @Nested
    @DisplayName("windows differ per document")
    class PerDocumentWindows {

        @Test
        @DisplayName("45 days out: the work permit is warned about, the iqama is not")
        void workPermitHasTheLongerNotice() {
            // The whole reason the window is per-document. If either of these two ever agrees
            // with the other, someone has collapsed the windows back into one constant.
            Captain c = captain(TODAY.plusDays(45), TODAY.plusDays(45), TODAY.plusYears(1),
                    TODAY.plusYears(1), TODAY.plusYears(1));

            Map<DocumentKind, DocumentStatus> statuses = statuses(c);
            assertThat(statuses.get(DocumentKind.WORK_PERMIT)).isEqualTo(DocumentStatus.EXPIRING);
            assertThat(statuses.get(DocumentKind.IQAMA)).isEqualTo(DocumentStatus.VALID);
        }

        @Test
        @DisplayName("a window shortened in settings takes effect on the next read")
        void windowIsReadFromSettings() {
            Captain c = captain(TODAY.plusYears(1), TODAY.plusDays(45), TODAY.plusYears(1),
                    TODAY.plusYears(1), TODAY.plusYears(1));
            assertThat(statusOf(c, DocumentKind.WORK_PERMIT)).isEqualTo(DocumentStatus.EXPIRING);

            seedWindows(30, 15, 30, 30, 30);
            assertThat(statusOf(c, DocumentKind.WORK_PERMIT)).isEqualTo(DocumentStatus.VALID);
        }

        @Test
        @DisplayName("an unreadable window falls back to the document's default")
        void unparseableWindowFallsBackToDefault() {
            // Losing every alert to a CONFIG_ERROR would be worse than using the default.
            when(settingRepo.findEffectiveOn(any())).thenReturn(List.of(
                    AppSetting.builder().settingKey("WORK_PERMIT_ALERT_DAYS")
                            .value("ستون").valueType("INTEGER")
                            .effectiveFrom(LocalDate.of(2026, 1, 1)).build()));

            Captain c = captain(TODAY.plusYears(1), TODAY.plusDays(45), TODAY.plusYears(1),
                    TODAY.plusYears(1), TODAY.plusYears(1));
            assertThat(statusOf(c, DocumentKind.WORK_PERMIT)).isEqualTo(DocumentStatus.EXPIRING);
        }

        @Test
        @DisplayName("an empty settings table still produces verdicts")
        void missingSettingsFallBackToDefaults() {
            when(settingRepo.findEffectiveOn(any())).thenReturn(List.of());

            assertThat(statusOf(withIqama(TODAY.plusDays(10)), DocumentKind.IQAMA))
                    .isEqualTo(DocumentStatus.EXPIRING);
        }
    }

    @Nested
    @DisplayName("rolling several documents into one badge")
    class WorstAndCount {

        @Test
        @DisplayName("the badge shows the most serious document, not the first")
        void worstWins() {
            Captain c = captain(TODAY.plusDays(10), TODAY.minusDays(5), null,
                    TODAY.plusYears(1), TODAY.plusYears(1));

            // MISSING outranks EXPIRED, which outranks EXPIRING.
            assertThat(evaluator.worst(evaluator.evaluate(c, TODAY)))
                    .isEqualTo(DocumentStatus.MISSING);
        }

        @Test
        @DisplayName("the count is of documents needing work, not of documents held")
        void countExcludesValid() {
            Captain c = captain(TODAY.plusDays(10), TODAY.minusDays(5), null,
                    TODAY.plusYears(1), TODAY.plusYears(1));

            assertThat(evaluator.alertCount(evaluator.evaluate(c, TODAY))).isEqualTo(3);
        }

        @Test
        @DisplayName("a clear captain reads ساري with nothing to count")
        void allValidIsSilent() {
            Captain c = captain(TODAY.plusYears(1), TODAY.plusYears(1), TODAY.plusYears(1),
                    TODAY.plusYears(1), TODAY.plusYears(1));

            List<DocumentAlert> alerts = evaluator.evaluate(c, TODAY);
            assertThat(evaluator.worst(alerts)).isEqualTo(DocumentStatus.VALID);
            assertThat(evaluator.alertCount(alerts)).isZero();
        }

        @Test
        @DisplayName("every tracked document is reported, so none can be silently dropped")
        void coversEveryKind() {
            assertThat(evaluator.evaluate(withIqama(TODAY), TODAY))
                    .extracting(DocumentAlert::kind)
                    .containsExactlyInAnyOrder(DocumentKind.values());
        }
    }

    @Nested
    @DisplayName("who is tracked")
    class Tracking {

        @Test
        @DisplayName("a leaver's documents are not chased")
        void leaverIsNotTracked() {
            Captain leaver = withIqama(TODAY.minusDays(200));
            leaver.setStatus("TERMINATED");

            assertThat(evaluator.isTracked(leaver)).isFalse();
        }

        @Test
        @DisplayName("an active captain is")
        void activeIsTracked() {
            assertThat(evaluator.isTracked(withIqama(TODAY))).isTrue();
        }
    }

    @Nested
    @DisplayName("the countdown shown beside a date")
    class DaysLeft {

        /** findFirst cannot carry the null we are asserting on, so pick the alert then read it. */
        private Long daysLeftFor(LocalDate expiry) {
            return evaluator.evaluate(withIqama(expiry), TODAY).stream()
                    .filter(a -> a.kind() == DocumentKind.IQAMA)
                    .findFirst()
                    .orElseThrow()
                    .daysLeft();
        }

        @Test
        @DisplayName("counts forward while the document is in date")
        void positiveBeforeExpiry() {
            assertThat(daysLeftFor(TODAY.plusDays(12))).isEqualTo(12);
        }

        @Test
        @DisplayName("goes negative once the date has passed")
        void negativeAfterExpiry() {
            assertThat(daysLeftFor(TODAY.minusDays(40))).isEqualTo(-40);
        }

        @Test
        @DisplayName("is absent when there is no date to count from")
        void nullWhenMissing() {
            assertThat(daysLeftFor(null)).isNull();
        }
    }

    @Nested
    @DisplayName("against the rows HR marked by hand")
    class AgainstTheHrSheet {

        /**
         * Rows lifted from {@code حالات الانذارات.xlsx} with its own statuses, evaluated as of
         * the date that sheet was current. The point is not the arithmetic — it is that the
         * three-state judgement the office already trusts comes out unchanged.
         */
        @Test
        @DisplayName("reproduces ساري / على وشك الانتهاء / منتهى for known rows")
        void matchesTheSheet() {
            LocalDate asOf = LocalDate.of(2026, 7, 22);

            // محمد بيلو احمد — iqama 2026-06-08, marked منتهى
            assertStatus(asOf, LocalDate.of(2026, 6, 8), DocumentStatus.EXPIRED);
            // مبارك عبدالله — iqama 2026-07-26, marked على وشك الانتهاء
            assertStatus(asOf, LocalDate.of(2026, 7, 26), DocumentStatus.EXPIRING);
            // نور الدين محمد عبدالله — iqama 2026-12-17, marked ساري
            assertStatus(asOf, LocalDate.of(2026, 12, 17), DocumentStatus.VALID);
        }

        private void assertStatus(LocalDate asOf, LocalDate iqamaExpiry, DocumentStatus expected) {
            Captain c = captain(iqamaExpiry, asOf.plusYears(1), asOf.plusYears(1),
                    asOf.plusYears(1), asOf.plusYears(1));

            DocumentStatus actual = evaluator.evaluate(c, asOf).stream()
                    .filter(a -> a.kind() == DocumentKind.IQAMA)
                    .map(DocumentAlert::status)
                    .findFirst()
                    .orElseThrow();

            assertThat(actual)
                    .as("iqama expiring %s, judged on %s", iqamaExpiry, asOf)
                    .isEqualTo(expected);
        }
    }

}
