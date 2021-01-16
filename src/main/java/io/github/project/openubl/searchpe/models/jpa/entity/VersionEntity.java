package io.github.project.openubl.searchpe.models.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Entity
@Table(name = "version")
public class VersionEntity extends PanacheEntity {

    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    public Date createdAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    public Status status;

    @NotNull
    @Type(type = "true_false")
    @Column(name = "active")
    public boolean active;

    public static final class Builder {
        public Date createdAt;
        public Status status;
        public boolean active;

        private Builder() {
        }

        public static Builder aVersionEntity() {
            return new Builder();
        }

        public Builder withCreatedAt(Date createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withActive(boolean active) {
            this.active = active;
            return this;
        }

        public VersionEntity build() {
            VersionEntity versionEntity = new VersionEntity();
            versionEntity.status = this.status;
            versionEntity.createdAt = this.createdAt;
            versionEntity.active = this.active;
            return versionEntity;
        }
    }
}
