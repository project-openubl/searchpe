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
package io.github.project.openubl.searchpe.models.jpa.entity;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RegisterForReflection
public enum EstadoContribuyente {
    ACTIVO,
    SUSPENSION_TEMPORAL,
    BAJA_DE_OFICIO,
    BAJA_DEFINITIVA;

    private static final Map<String, EstadoContribuyente> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    /**
     * @param value the status as written in the padrón, e.g. {@code BAJA DE OFICIO}
     * @return the status, or empty if {@code value} is null or unknown
     */
    public static Optional<EstadoContribuyente> fromString(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(value.toUpperCase(Locale.ROOT).replace(' ', '_')));
    }
}
