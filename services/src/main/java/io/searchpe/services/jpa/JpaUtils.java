package io.searchpe.services.jpa;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.searchpe.services.models.SearchResultModel;

import java.util.List;
import java.util.Optional;

public class JpaUtils {

    public static <T> Optional<T> transformSingle(List<T> list) {
        if (list.size() == 1) {
            return Optional.of(list.get(0));
        } else if (list.isEmpty()) {
            return Optional.empty();
        } else {
            throw new IllegalStateException("More than one active version found");
        }
    }

    public static <T> SearchResultModel<T> transformToSearchResult(PanacheQuery<T> query, Page page) {
        long total = query.count();
        List<T> items = query.page(page).list();
        return new SearchResultModel<>(items, total);
    }
}
