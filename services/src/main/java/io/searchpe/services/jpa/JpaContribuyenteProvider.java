package io.searchpe.services.jpa;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.searchpe.services.jpa.entity.ContribuyenteEntity;
import io.searchpe.services.models.SearchResultModel;
import io.searchpe.services.providers.ContribuyenteProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Transactional
@ApplicationScoped
public class JpaContribuyenteProvider implements ContribuyenteProvider {

    @Inject
    EntityManager em;

    @Override
    public Optional<ContribuyenteEntity> getById(long id) {
        return Optional.ofNullable(ContribuyenteEntity.findById(id));
    }

    @Override
    public Optional<ContribuyenteEntity> getByRuc(Long versionId, String ruc) {
        Map<String, Object> params = new HashMap<>();
        params.put("versionId", versionId);
        params.put("ruc", ruc);

        List<ContribuyenteEntity> entities = ContribuyenteEntity.find("version.id = :versionId and ruc = :ruc", params)
                .list();
        return JpaUtils.transformSingle(entities);
    }

    @Override
    public SearchResultModel<ContribuyenteEntity> search(Long versionId, Page page) {
        Map<String, Object> params = new HashMap<>();
        params.put("versionId", versionId);

        PanacheQuery<ContribuyenteEntity> query = ContribuyenteEntity.find("version.id = :versionId", Sort.by("razonSocial"), params);
        return JpaUtils.transformToSearchResult(query, page);
    }

    @Override
    public SearchResultModel<ContribuyenteEntity> searchByFilterText(Long versionId, String filterText, Page page) {
        Map<String, Object> params = new HashMap<>();
        params.put("versionId", versionId);
        params.put("filterText", "%" + filterText + "%");

        PanacheQuery<ContribuyenteEntity> query = ContribuyenteEntity.find("version.id = :versionId and lower(razonSocial) like :filterText", params);
        return JpaUtils.transformToSearchResult(query, page);
    }

}

