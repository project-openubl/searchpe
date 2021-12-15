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
package io.github.project.openubl.searchpe.models.jpa.search;

import org.hibernate.search.mapper.orm.automaticindexing.session.AutomaticIndexingSynchronizationConfigurationContext;
import org.hibernate.search.mapper.orm.automaticindexing.session.AutomaticIndexingSynchronizationStrategy;

import javax.enterprise.context.Dependent;
import javax.inject.Named;

@Dependent
@Named(SearchpeNoneIndexer.BEAN_NAME)
public class SearchpeNoneIndexer implements AutomaticIndexingSynchronizationStrategy {

    public static final String BEAN_NAME = "searchpeNoneIndexer";
    public static final String BEAN_FULL_NAME = "bean:" + BEAN_NAME;

    @Override
    public void apply(AutomaticIndexingSynchronizationConfigurationContext context) {
        // Nothing to do since we don't want to index in Elasticsearch
    }

}
