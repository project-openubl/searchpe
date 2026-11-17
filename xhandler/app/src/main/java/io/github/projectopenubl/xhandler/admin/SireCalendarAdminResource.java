package io.github.projectopenubl.xhandler.admin;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * SUNAT's calendar of monthly obligations (DESIGN.md §11.7: RS 281-2022 annex II and its yearly
 * successors), loaded by platform admins: the due date of each period by the RUC's last digit.
 */
@Path("/api/admin/v1/sire/calendar")
public class SireCalendarAdminResource {

    public record Entry(@NotNull @Pattern(regexp = "\\d{4}(0[1-9]|1[0-2])") String period, @Min(0) @Max(9) int rucLastDigit,
            @NotNull LocalDate dueDate) {}

    private final PlatformAdmin admin;
    private final EntityManager em;

    public SireCalendarAdminResource(PlatformAdmin admin, EntityManager em) {
        this.admin = admin;
        this.em = em;
    }

    @PUT
    @Transactional
    public Map<String, Object> load(@Valid List<@Valid Entry> entries) {
        String who = admin.require();
        if (entries == null || entries.isEmpty()) {
            throw io.github.projectopenubl.xhandler.api.ApiException.invalid("invalid_request", "Envíe al menos una fecha del cronograma.", null);
        }
        for (Entry e : entries) {
            em.createNativeQuery("""
                            INSERT INTO xhandler.sire_calendar (period, ruc_last_digit, due_date) VALUES (:p, :d, :due)
                            ON CONFLICT (period, ruc_last_digit) DO UPDATE SET due_date = excluded.due_date""")
                    .setParameter("p", e.period()).setParameter("d", (short) e.rucLastDigit()).setParameter("due", e.dueDate()).executeUpdate();
        }
        AdminResource.PLATFORM_AUDIT.infof("admin %s loaded %d SIRE calendar entries", who, entries.size());
        return Map.of("loaded", entries.size());
    }

    @GET
    @Transactional
    public List<Map<String, Object>> list(@QueryParam("period") String period) {
        admin.require();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT period, ruc_last_digit, due_date FROM xhandler.sire_calendar
                        WHERE cast(:p AS text) IS NULL OR period = :p ORDER BY period DESC, ruc_last_digit""")
                .setParameter("p", period).getResultList();
        return rows.stream().map(r -> Map.<String, Object>of("period", r[0], "ruc_last_digit", ((Number) r[1]).intValue(),
                "due_date", String.valueOf(r[2]))).toList();
    }
}
