/**
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at")
    public Date updatedAt;

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
        public Date updatedAt;
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

        public Builder withUpdatedAt(Date updatedAt) {
            this.updatedAt = updatedAt;
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
            versionEntity.active = this.active;
            versionEntity.createdAt = this.createdAt;
            versionEntity.updatedAt = this.updatedAt;
            versionEntity.status = this.status;
            return versionEntity;
        }
    }
}
