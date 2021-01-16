package io.github.project.openubl.searchpe.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.List;

@Entity
@Table(name = "QRTZ_JOB_DETAILS")
public class QuartzJobDetailsEntity extends PanacheEntityBase {

    @JsonbTransient
    @EmbeddedId
    public QuartzJobDetailsId id;

    @NotNull
    @Column(name = "SCHED_NAME", insertable = false, updatable = false)
    public String schedName;

    @NotNull
    @Column(name = "JOB_NAME", insertable = false, updatable = false)
    public String jobName;

    @NotNull
    @Column(name = "JOB_GROUP", insertable = false, updatable = false)
    public String jobGroup;

//    @Column(name = "DESCRIPTION")
//    public String description;
//
//    @NotNull
//    @Column(name = "JOB_CLASS_NAME")
//    public String jobClassName;
//
//    @NotNull
//    @Column(name = "IS_DURABLE")
//    public Boolean isDurable;
//
//    @NotNull
//    @Column(name = "IS_NONCONCURRENT")
//    public Boolean isNonConcurrent;
//
//    @NotNull
//    @Column(name = "IS_UPDATE_DATA")
//    public Boolean isUpdateData;
//
//    @NotNull
//    @Column(name = "REQUESTS_RECOVERY")
//    public Boolean requestsRecovery;

    @OneToMany(mappedBy = "jobDetails", fetch = FetchType.EAGER)
    public List<QuartzTriggersEntity> triggers;
}
