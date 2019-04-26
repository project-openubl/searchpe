package io.searchpe.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "version")
public class VersionEntity extends PanacheEntity {

    @NotNull
    @Column(name = "number")
    public long number;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date")
    public Date date;

    @NotNull
    @Type(type = "org.hibernate.type.TrueFalseType")
    @Column(name = "complete")
    public boolean complete;

}
