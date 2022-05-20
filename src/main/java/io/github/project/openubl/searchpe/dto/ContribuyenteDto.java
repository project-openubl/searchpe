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
import lombok.Data;

@Data
@RegisterForReflection
public class ContribuyenteDto {
    private Long versionId;
    private String ruc;
    private String dni;
    private String nombre;
    private String estado;
    private String condicionDomicilio;
    private String ubigeo;
    public String tipoVia;
    public String nombreVia;
    public String codigoZona;
    public String tipoZona;
    public String numero;
    public String interior;
    public String lote;
    public String departamento;
    public String manzana;
    public String kilometro;
}
