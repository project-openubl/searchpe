package io.searchpe.models.jpa;

import io.searchpe.models.VersionManager;
import io.searchpe.models.VersionModel;
import io.searchpe.models.jpa.entity.VersionEntity;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@Transactional
@ApplicationScoped
public class JpaVersionManager implements VersionManager {

    @Inject
    EntityManager em;

    @Override
    public void activate(VersionModel version) {
        em.createQuery("update VersionEntity v set v.active = false where v.active=true")
                .executeUpdate();

        VersionEntity versionEntity = VersionAdapter.toEntity(em, version);
        versionEntity.active = true;

        em.merge(versionEntity);
        em.flush();
    }

}
