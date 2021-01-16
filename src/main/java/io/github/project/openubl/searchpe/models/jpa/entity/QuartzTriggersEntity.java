package io.github.project.openubl.searchpe.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "QRTZ_TRIGGERS")
public class QuartzTriggersEntity extends PanacheEntityBase {

    @JsonbTransient
    @EmbeddedId
    public QuartzTriggersId id;

//    @NotNull
//    @Column(name = "SCHED_NAME", insertable = false, updatable = false)
//    public String schedName;
//
//    @NotNull
//    @Column(name = "JOB_NAME")
//    public String jobName;
//
//    @NotNull
//    @Column(name = "JOB_GROUP")
//    public String jobGroup;

    @NotNull
    @Column(name = "TRIGGER_NAME", insertable = false, updatable = false)
    public String triggerName;

    @NotNull
    @Column(name = "TRIGGER_GROUP", insertable = false, updatable = false)
    public String triggerGroup;

//    @Column(name = "DESCRIPTION")
//    public String description;
//
//    @Column(name = "NEXT_FIRE_TIME")
//    public Long nextFireTime;
//
//    @Column(name = "PREV_FIRE_TIME")
//    public Long prevFireTime;
//
//    @Column(name = "PRIORITY")
//    public Integer priority;
//
//    @NotNull
//    @Column(name = "TRIGGER_STATE")
//    public String triggerState;
//
//    @NotNull
//    @Column(name = "TRIGGER_TYPE")
//    public String triggerType;
//
//    @NotNull
//    @Column(name = "START_TIME")
//    public Long startTime;
//
//    @Column(name = "END_TIME")
//    public Long endTime;
//
//    @Column(name = "CALENDAR_NAME")
//    public String calendarName;
//
//    @Column(name = "MISFIRE_INSTR")
//    public Integer misFireInstr;

    @JsonbTransient
    @MapsId("id")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "SCHED_NAME", referencedColumnName = "SCHED_NAME"),
            @JoinColumn(name = "JOB_NAME", referencedColumnName = "JOB_NAME"),
            @JoinColumn(name = "JOB_GROUP", referencedColumnName = "JOB_GROUP")
    })
    public QuartzJobDetailsEntity jobDetails;
}
