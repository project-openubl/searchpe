package io.github.project.openubl.searchpe.models.jpa.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Embeddable
public class QuartzTriggersId implements Serializable {

    @NotNull
    @Column(name = "SCHED_NAME")
    public String schedName;

    @NotNull
    @Column(name = "TRIGGER_NAME")
    public String triggerName;

    @NotNull
    @Column(name = "TRIGGER_GROUP")
    public String triggerGroup;

}
