package io.searchpe.model;

import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "version")
@NamedQueries(value = {
        @NamedQuery(name = "getVersions", query = "select v from Version v order by v.number desc"),
        @NamedQuery(name = "getVersionsByCompleteStatus", query = "select v from Version v where v.complete=:complete order by v.number desc"),
        @NamedQuery(name = "getCompleteVersionsBefore", query = "select v from Version v where v.complete =:complete and v.date < :date order by v.date asc")
})
public class Version {

    @Id
    private String id;

    @NotNull
    @Column(name = "number")
    private long number;

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date")
    private Date date;

    @NotNull
    @Type(type = "org.hibernate.type.TrueFalseType")
    @Column(name = "complete")
    private boolean complete;

    @ElementCollection
    @MapKeyColumn(name="name")
    @Column(name="value")
    @CollectionTable(name="version_metrics", joinColumns={ @JoinColumn(name="version_id") })
    private Map<String, Long> metrics = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long version) {
        this.number = version;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public Map<String, Long> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Long> metrics) {
        this.metrics = metrics;
    }
}
