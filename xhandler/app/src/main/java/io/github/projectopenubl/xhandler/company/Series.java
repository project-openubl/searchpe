package io.github.projectopenubl.xhandler.company;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "series")
public class Series extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public UUID companyId;
    public String sunatEnv;
    /** SUNAT catalog 01 code: 01 factura, 03 boleta, 07 nota de crédito, ... */
    public String tipoComprobante;
    public String serie;
    public long nextNumber = 1;
    public boolean isDefault;
    public boolean active = true;

    @CreationTimestamp
    public Instant createdAt;

    @UpdateTimestamp
    public Instant updatedAt;
}
