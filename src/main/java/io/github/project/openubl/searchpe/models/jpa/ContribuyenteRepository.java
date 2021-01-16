package io.github.project.openubl.searchpe.models.jpa;

import io.github.project.openubl.searchpe.models.PageBean;
import io.github.project.openubl.searchpe.models.PageModel;
import io.github.project.openubl.searchpe.models.SortBean;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Transactional
@ApplicationScoped
public class ContribuyenteRepository implements PanacheRepositoryBase<ContribuyenteEntity, String> {

    public static final String[] SORT_BY_FIELDS = {"ruc", "razonSocial"};

    public static Optional<ContribuyenteEntity> findByRuc(VersionEntity version, String ruc) {
        return ContribuyenteEntity.find(
                "version.id =:versionId and ruc =:ruc",
                Parameters.with("versionId", version.id).and("ruc", ruc)
        ).firstResultOptional();
    }

    public static PageModel<ContribuyenteEntity> list(VersionEntity version, PageBean pageBean, List<SortBean> sortBy) {
        Sort sort = Sort.by();
        sortBy.forEach(f -> sort.and(f.getFieldName(), f.isAsc() ? Sort.Direction.Ascending : Sort.Direction.Descending));

        PanacheQuery<ContribuyenteEntity> query = VersionEntity
                .find(
                        "From ContribuyenteEntity as c where c.version.id =:versionId",
                        sort,
                        Parameters.with("versionId", version.id)
                )
                .range(pageBean.getOffset(), pageBean.getOffset() + pageBean.getLimit() - 1);

        long count = query.count();
        List<ContribuyenteEntity> list = query.list();
        return new PageModel<>(pageBean, count, list);
    }

    public static PageModel<ContribuyenteEntity> list(VersionEntity version, String filterText, PageBean pageBean, List<SortBean> sortBy) {
        Sort sort = Sort.by();
        sortBy.forEach(f -> sort.and(f.getFieldName(), f.isAsc() ? Sort.Direction.Ascending : Sort.Direction.Descending));

        PanacheQuery<ContribuyenteEntity> query = VersionEntity
                .find(
                        "From ContribuyenteEntity as c where c.version.id =:versionId and (c.ruc like :filterText or lower(c.razonSocial) like :filterText) ",
                        sort,
                        Parameters.with("versionId", version.id).and("filterText", "%" + filterText.toLowerCase() + "%")
                )
                .range(pageBean.getOffset(), pageBean.getOffset() + pageBean.getLimit() - 1);

        long count = query.count();
        List<ContribuyenteEntity> list = query.list();
        return new PageModel<>(pageBean, count, list);
    }

}
