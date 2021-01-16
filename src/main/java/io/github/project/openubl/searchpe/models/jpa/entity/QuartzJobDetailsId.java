package io.github.project.openubl.searchpe.models.jpa.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Embeddable
public class QuartzJobDetailsId implements Serializable {

    @NotNull
    @Column(name = "SCHED_NAME")
    public String schedName;

    @NotNull
    @Column(name = "JOB_NAME")
    public String jobName;

    @NotNull
    @Column(name = "JOB_GROUP")
    public String jobGroup;

}
