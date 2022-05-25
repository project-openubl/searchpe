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
package io.github.project.openubl.searchpe.services;

import io.github.project.openubl.searchpe.dto.BasicUserDto;
import io.github.project.openubl.searchpe.dto.BasicUserPasswordChangeDto;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import io.quarkus.elytron.security.common.BcryptUtil;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;

@Transactional
@ApplicationScoped
public class BasicUserService {

    public BasicUserEntity create(BasicUserDto dto) {
        BasicUserEntity user = new BasicUserEntity();
        user.fullName = dto.getFullName();
        user.username = dto.getUsername();
        user.password = BcryptUtil.bcryptHash(dto.getPassword());
        user.permissions = String.join(",", dto.getPermissions());
        user.persist();
        return user;
    }

    public BasicUserEntity update(BasicUserEntity entity, BasicUserDto dto) {
        entity.fullName = dto.getFullName();
        entity.username = dto.getUsername();
        if (dto.getPassword() != null) {
            entity.password = BcryptUtil.bcryptHash(dto.getPassword());
        }
        if (dto.getPermissions() != null) {
            entity.permissions = String.join(",", dto.getPermissions());
        }
        entity.persist();
        return entity;
    }

    public void changePassword(BasicUserEntity entity, BasicUserPasswordChangeDto dto) {
        entity.password = BcryptUtil.bcryptHash(dto.getNewPassword());
        entity.persist();
    }

}
