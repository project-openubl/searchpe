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

import io.github.project.openubl.searchpe.dto.ContribuyenteDto;
import io.github.project.openubl.searchpe.models.jpa.entity.ContribuyenteEntity;
import io.github.project.openubl.searchpe.utils.DataHelper;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "cdi")
public interface ContribuyenteMapper {

    ContribuyenteDto toDto(ContribuyenteEntity entity);

    @AfterMapping
    default void setDni(ContribuyenteEntity entity, @MappingTarget ContribuyenteDto dto) {
        dto.setDni(DataHelper.getDniFromRuc(entity.getRuc()).orElse(null));
    }

}
