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
package io.github.project.openubl.searchpe.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class SearchResultDto<T> {

    private Meta meta;
    private List<T> data;

    public static <T> SearchResultDto<T> getEmptyResult(int offset, int limit) {
        SearchResultDto<T> result = new SearchResultDto<>();

        SearchResultDto.Meta meta = new SearchResultDto.Meta();
        meta.setOffset(offset);
        meta.setLimit(limit);
        meta.setCount(0L);

        result.setMeta(meta);
        result.setData(Collections.emptyList());

        return result;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @RegisterForReflection
    public static class Meta {
        private Integer offset;
        private Integer limit;
        private Long count;
    }

}
