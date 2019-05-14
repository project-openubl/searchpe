package io.searchpe.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.searchpe.models.Status;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Entity
@Table(name = "version", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"number"})
})
public class VersionEntity extends PanacheEntity{

    @NotNull
    @Column(name = "number")
    public long number;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    public Date createdAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    public Status status;

    @NotNull
    @Type(type = "org.hibernate.type.TrueFalseType")
    @Column(name = "active")
    public boolean active;
}
