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

import io.github.project.openubl.searchpe.dto.VersionDto;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import javax.inject.Inject;
import java.util.Optional;

@Mapper(componentModel = "cdi")
public abstract class VersionMapper {

    @Inject
    VersionRepository versionRepository;

    @Mapping(source = "id", target = "id")
    public abstract VersionDto toDto(VersionEntity entity);

    @AfterMapping
    public void setId(VersionEntity entity, @MappingTarget VersionDto dto) {
        Optional<VersionEntity> activeVersion = versionRepository.findActive();

        dto.setId(entity.id);
        dto.setActive(activeVersion.isPresent() && activeVersion.get().id.equals(entity.id));
    }

}
