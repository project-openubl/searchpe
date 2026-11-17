package io.github.projectopenubl.xhandler.company;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The third-party OSE a company sends through (DESIGN.md §3.2). Its credentials: kind {@code ose}. */
@Entity
@Table(name = "ose_endpoint")
public class OseEndpoint extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public UUID companyId;
    public String name;
    public String invoiceUrl;
    public String perceptionUrl;
    public Instant createdAt;
    public Instant updatedAt;

    public static Optional<OseEndpoint> of(UUID companyId) {
        return find("companyId", companyId).firstResultOptional();
    }
}
