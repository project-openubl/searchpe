package io.github.projectopenubl.xhandler.documents;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Legal send deadlines (DESIGN.md §3.1). D is the issue date; days are calendar days; every legal
 * date is computed in America/Lima. A deadline is the end of its last day.
 */
public final class Deadlines {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private Deadlines() {}

    public static LocalDate todayInLima() {
        return LocalDate.now(LIMA);
    }

    /** Last moment to send a document individually. Summaries get theirs from their reference day. */
    public static Instant sendDeadline(String tipoComprobante, String serie, LocalDate fechaEmision) {
        int days = switch (tipoComprobante) {
            case "01" -> 3;
            case "03" -> 5;
            case "07", "08" -> serie.startsWith("B") ? 5 : 3;
            // Before transport starts; the issue date is the latest safe day
            case "09", "31" -> 0;
            case "20", "40" -> 7;
            case "RC", "RA", "RR" -> 7;
            default -> 3;
        };
        return endOfDay(fechaEmision.plusDays(days));
    }

    /** A boleta not accepted when D+5 passes moves into a resumen diario (§9.2.4). */
    public static boolean boletaPastIndividualWindow(LocalDate fechaEmision, LocalDate today) {
        return today.isAfter(fechaEmision.plusDays(5));
    }

    /** Voiding window: 7 days from the reference instant (§3.1, 2027 rules: from generation). */
    public static boolean withinVoidingWindow(LocalDate from, LocalDate today) {
        return !today.isAfter(from.plusDays(7));
    }

    public static Instant endOfDay(LocalDate day) {
        return day.atTime(LocalTime.MAX).atZone(LIMA).toInstant();
    }
}
