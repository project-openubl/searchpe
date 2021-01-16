/**
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.utils;

import io.github.project.openubl.searchpe.models.PageModel;
import io.github.project.openubl.searchpe.models.PageRepresentation;

import java.util.function.Function;
import java.util.stream.Collectors;

public class EntityToRepresentation {
    private EntityToRepresentation() {
        // Just static methods
    }

    public static <T, R> PageRepresentation<R> toRepresentation(
            PageModel<T> model,
            Function<T, R> mapper
    ) {
        PageRepresentation<R> rep = new PageRepresentation<>();

        // Meta
        PageRepresentation.Meta repMeta = new PageRepresentation.Meta();
        rep.setMeta(repMeta);

        repMeta.setCount(model.getTotalElements());
        repMeta.setOffset(model.getOffset());
        repMeta.setLimit(model.getLimit());

        // Data
        rep.setData(model.getPageElements().stream()
                .map(mapper)
                .collect(Collectors.toList())
        );

        return rep;
    }

}
