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

import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import javax.json.bind.annotation.JsonbDateFormat;
import java.util.Date;

@Data
@RegisterForReflection
public class VersionDto {

    private Long id;

    @JsonbDateFormat(value = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private Date createdAt;

    @JsonbDateFormat(value = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private Date updatedAt;

    private Status status;
    private int records;
    private boolean isActive;

}
