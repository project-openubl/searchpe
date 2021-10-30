/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Eclipse Public License - v 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.project.openubl.searchpe.resources.interceptors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.BadRequestException;

@Interceptor
@HTTPBasicAuthEnabled
public class HTTPBasicAuthEnabledInterceptor {

    @ConfigProperty(name = "quarkus.http.auth.basic")
    Boolean isAuthBasicEnabled;

    @AroundInvoke
    public Object invoke(InvocationContext ctx) throws Exception {
        if (isAuthBasicEnabled) {
            return ctx.proceed();
        } else {
            throw new BadRequestException("HTTP Basic auth is disabled, can not proceed");
        }
    }

}
