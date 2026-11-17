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
@Grab('org.yaml:snakeyaml:1.33')
import org.yaml.snakeyaml.*
import groovy.yaml.*
import java.time.LocalDate

def file = new File(this.args[0])
def version = this.args[1]

def fileReader = new FileReader(file)
def yaml = new Yaml().load(fileReader)

yaml.metadata.name = 'searchpe-operator.v' + version

// Quarkus Operator SDK >= 7 already writes the CSV annotations under metadata.annotations (where
// OperatorHub.io expects them); older versions wrote them under spec.annotations. Support both.
def annotations = yaml.metadata.annotations ?: [:]
if (yaml.spec.annotations instanceof Map) {
    annotations.putAll(yaml.spec.annotations)
}
yaml.spec.remove('annotations')
annotations.containerImage = 'quay.io/projectopenubl/searchpe-operator:v' + version
annotations.description = 'Searchpe is an enterprise solution for managing natural and legal entities in your organization. Valid for Peru.'
annotations.createdAt = LocalDate.now().toString()
yaml.metadata.annotations = annotations

// The operator and searchpe images are published for both architectures
def labels = yaml.metadata.labels ?: [:]
labels['operatorframework.io/arch.amd64'] = 'supported'
labels['operatorframework.io/arch.arm64'] = 'supported'
labels['operatorframework.io/os.linux'] = 'supported'
yaml.metadata.labels = labels

// yaml.spec.install.spec.deployments[0].spec.template.spec.containers[0].image = 'quay.io/projectopenubl/searchpe-operator:v' + version
yaml.spec.version = version
yaml.spec.customresourcedefinitions.owned[0].displayName = 'Searchpe'
yaml.spec.customresourcedefinitions.owned[0].description = 'Searchpe'

DumperOptions options = new DumperOptions();
options.indent = 2
options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
options.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
options.prettyFlow = true

new Yaml(options).dump(yaml, new FileWriter(file))
