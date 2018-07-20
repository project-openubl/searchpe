package io.github.carlosthe19916.repeid.model;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Entity
@Table(name = "import")
@NamedQueries(value = {
        @NamedQuery(name = "getVersions", query = "select v from Version v order by v.number desc")
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

}
