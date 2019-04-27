package io.searchpe.models.jpa;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class Utils {

    public static <T, R> Optional<R> transformSingle(List<T> list, Function<T, R> func) {
        if (list.size() == 1) {
            return Optional.of(func.apply(list.get(0)));
        } else if (list.isEmpty()) {
            return Optional.empty();
        } else {
            throw new IllegalStateException("More than one active version found");
        }
    }
}
