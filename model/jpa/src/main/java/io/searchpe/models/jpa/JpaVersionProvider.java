package io.searchpe.models.jpa;

import io.searchpe.models.Status;
import io.searchpe.models.VersionModel;
import io.searchpe.models.VersionProvider;
import io.searchpe.models.jpa.entity.VersionEntity;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Transactional
@ApplicationScoped
public class JpaVersionProvider implements VersionProvider {

    @Inject
    EntityManager em;

    Function<VersionEntity, VersionModel> toModel = versionEntity -> new VersionAdapter(em, versionEntity);

    @Override
    public VersionModel addVersion() {
        VersionEntity versionEntity = new VersionEntity();

        versionEntity.status = Status.NOT_PROCESSED;
        versionEntity.createdAt = new Date();
        versionEntity.active = false;
        versionEntity.number = 1;

        Object maxVersion = em.createQuery("select max(v.number) FROM VersionEntity v").getSingleResult();
        if (maxVersion != null) {
            versionEntity.number = ((long) maxVersion) + 1;
        }

        em.persist(versionEntity);
        em.flush();

        return new VersionAdapter(em, versionEntity);
    }

    @Override
    public Optional<VersionModel> getActiveVersion() {
        List<VersionEntity> activeVersions = VersionEntity.list("active", true);
        return Utils.transformSingle(activeVersions, toModel);
    }
}
