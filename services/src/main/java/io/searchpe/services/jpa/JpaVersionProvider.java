package io.searchpe.services.jpa;

import io.searchpe.services.jpa.entity.VersionEntity;
import io.searchpe.services.providers.VersionProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Transactional
@ApplicationScoped
public class JpaVersionProvider implements VersionProvider {

    @Inject
    EntityManager em;

    @Override
    public Optional<VersionEntity> getActive() {
        List<VersionEntity> activeVersions = VersionEntity.list("active", true);
        return JpaUtils.transformSingle(activeVersions);
    }

    @Override
    public Optional<VersionEntity> getById(Long id) {
        VersionEntity versionEntity = em.find(VersionEntity.class, id);
        return Optional.ofNullable(versionEntity);
    }
}
