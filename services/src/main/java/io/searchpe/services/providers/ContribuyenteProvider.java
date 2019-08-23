package io.searchpe.services.providers;

import io.quarkus.panache.common.Page;
import io.searchpe.services.jpa.entity.ContribuyenteEntity;
import io.searchpe.services.models.SearchResultModel;

import java.util.Optional;

public interface ContribuyenteProvider {

    Optional<ContribuyenteEntity> getById(long id);

    Optional<ContribuyenteEntity> getByRuc(Long versionId, String ruc);

    SearchResultModel<ContribuyenteEntity> search(Long versionId, Page page);

    SearchResultModel<ContribuyenteEntity> searchByFilterText(Long versionId, String filterText, Page page);

}
