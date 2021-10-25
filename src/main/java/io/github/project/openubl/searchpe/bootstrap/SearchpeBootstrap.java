package io.github.project.openubl.searchpe.bootstrap;

import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.quarkus.runtime.StartupEvent;
import org.hibernate.search.mapper.orm.session.SearchSession;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class SearchpeBootstrap {

    @Inject
    SearchSession searchSession;

    @Transactional
    void onStart(@Observes StartupEvent ev) throws InterruptedException {
        // only reindex if we imported some content
        if (ContribuyenteEntity.count() > 0) {
            searchSession.massIndexer().startAndWait();
        }
    }

}
