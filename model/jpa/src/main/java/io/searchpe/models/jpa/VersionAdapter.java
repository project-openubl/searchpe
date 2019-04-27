package io.searchpe.models.jpa;

import io.searchpe.models.Status;
import io.searchpe.models.VersionModel;
import io.searchpe.models.jpa.entity.VersionEntity;

import javax.persistence.EntityManager;
import java.util.Date;

public class VersionAdapter implements VersionModel, JpaModel<VersionEntity> {

    private final EntityManager em;
    private final VersionEntity version;

    public VersionAdapter(EntityManager em, VersionEntity version) {
        this.em = em;
        this.version = version;
    }

    public static VersionEntity toEntity(EntityManager em, VersionModel version) {
        if (version instanceof VersionAdapter) {
            return ((VersionAdapter) version).getEntity();
        } else {
            return em.getReference(VersionEntity.class, version.getId());
        }
    }

    @Override
    public VersionEntity getEntity() {
        return version;
    }

    @Override
    public long getId() {
        return version.id;
    }

    @Override
    public long getNumber() {
        return version.number;
    }

    @Override
    public Date getCreatedAt() {
        return version.createdAt;
    }

    @Override
    public Status getStatus() {
        return version.status;
    }

    @Override
    public void setStatus(Status status) {
        version.status = status;
    }

    @Override
    public boolean isActive() {
        return version.active;
    }

}
