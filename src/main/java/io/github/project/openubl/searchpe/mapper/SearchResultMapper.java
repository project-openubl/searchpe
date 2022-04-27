/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.mapper;

import io.github.project.openubl.searchpe.dto.SearchResultDto;
import io.github.project.openubl.searchpe.models.SearchResultBean;

import javax.enterprise.context.ApplicationScoped;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class SearchResultMapper {
    private SearchResultMapper() {
        // Just static methods
    }

    public <I, O> SearchResultDto<O> toDto(
            SearchResultBean<I> bean,
            Function<I, O> function
    ) {
        return SearchResultDto.<O>builder()
                .meta(SearchResultDto.Meta.builder()
                        .count(bean.getTotalElements())
                        .offset(bean.getOffset())
                        .limit(bean.getLimit())
                        .build()
                )
                .data(bean.getPageElements()
                        .stream()
                        .map(function)
                        .collect(Collectors.toList())
                )
                .build();
    }

}
