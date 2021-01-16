package io.github.project.openubl.searchpe.models.jpa;

import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import java.util.Optional;

@Transactional
@ApplicationScoped
public class VersionRepository implements PanacheRepository<VersionEntity> {

    public static final String[] SORT_BY_FIELDS = {"createdAt", "status", "active"};

    public static Optional<VersionEntity> findActive() {
        return VersionEntity.find("active", true).firstResultOptional();
    }

}
