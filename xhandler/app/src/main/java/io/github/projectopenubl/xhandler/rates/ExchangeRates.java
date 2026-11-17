package io.github.projectopenubl.xhandler.rates;

import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The daily SUNAT/SBS exchange rate (DESIGN.md §26.7): imported by a job, used for the S/ 700
 * boleta check in foreign currency, PEN equivalents in reports, and the default tipo_cambio on
 * manual issuing. A day without a published rate (weekends, holidays) uses the latest earlier one.
 *
 * <p>The source is SUNAT's public file ({@code dd/MM/yyyy|compra|venta|}), configurable.
 */
@ApplicationScoped
public class ExchangeRates implements JobHandler {

    /** A day's rate. */
    public record Rate(LocalDate day, BigDecimal compra, BigDecimal venta, String source) {}

    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EntityManager em;
    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ExchangeRates(EntityManager em,
            @ConfigProperty(name = "xhandler.exchange-rates.url", defaultValue = "https://www.sunat.gob.pe/a/txt/tipoCambio.txt") String url) {
        this.em = em;
        this.url = url;
    }

    /** The rate for a day, or the latest earlier one. */
    @Transactional(Transactional.TxType.MANDATORY)
    public Optional<Rate> on(LocalDate day) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT day, compra, venta, source FROM xhandler.exchange_rate WHERE day <= :day ORDER BY day DESC LIMIT 1")
                .setParameter("day", day)
                .getResultList();
        return rows.stream().findFirst().map(r -> new Rate(toDate(r[0]), (BigDecimal) r[1], (BigDecimal) r[2], (String) r[3]));
    }

    @Override
    public String name() {
        return "platform.exchange-rates";
    }

    @Override
    public Map<String, Object> run(JobContext context) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("exchange rate source answered HTTP " + response.statusCode());
        }
        Rate rate = parse(response.body().strip(), url);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                        INSERT INTO xhandler.exchange_rate (day, compra, venta, source) VALUES (:day, :compra, :venta, :source)
                        ON CONFLICT (day) DO UPDATE SET compra = excluded.compra, venta = excluded.venta, source = excluded.source,
                            fetched_at = now()""")
                .setParameter("day", rate.day())
                .setParameter("compra", rate.compra())
                .setParameter("venta", rate.venta())
                .setParameter("source", rate.source())
                .executeUpdate());
        return Map.of("day", rate.day().toString(), "compra", rate.compra().toPlainString(), "venta", rate.venta().toPlainString());
    }

    /** {@code 16/11/2026|3.380|3.390|} */
    static Rate parse(String line, String source) {
        String[] parts = line.split("\\|");
        if (parts.length < 3) {
            throw new IllegalArgumentException("unexpected exchange rate line: " + line);
        }
        return new Rate(LocalDate.parse(parts[0].strip(), DMY), new BigDecimal(parts[1].strip()), new BigDecimal(parts[2].strip()), source);
    }

    static LocalDate toDate(Object value) {
        return value instanceof LocalDate d ? d : ((java.sql.Date) value).toLocalDate();
    }
}
