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
package io.github.project.openubl.searchpe.resources.interceptors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.BadRequestException;

@Interceptor
@AllowAdvancedSearch
public class AllowAdvancedSearchInterceptor {

    private static final Logger LOGGER = Logger.getLogger(AllowAdvancedSearchInterceptor.class);

    @ConfigProperty(name = "searchpe.allow.advancedSearch")
    boolean allowAdvancedSearch;

    @AroundInvoke
    public Object invoke(InvocationContext ctx) throws Exception {
        if (allowAdvancedSearch) {
            return ctx.proceed();
        } else {
            LOGGER.warn("REST endpoint blocked: you can not perform advanced search");
            throw new BadRequestException("Advanced search has been disabled, can not proceed");
        }
    }

}
