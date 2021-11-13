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
package io.github.project.openubl.searchpe.resources.config;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.commons.io.IOUtils;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static java.util.jar.Attributes.Name.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.CACHE_CONTROL;
import static javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION;
import static org.mockserver.model.BinaryBody.binary;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.OK_200;

public class SunatServer implements QuarkusTestResourceLifecycleManager {

    private MockServerContainer sunat;

    @Override
    public Map<String, String> start() {
        sunat = new MockServerContainer(DockerImageName.parse("jamesdbloom/mockserver:mockserver-5.11.2"));
        sunat.start();

        final String fileName = "padron_reducido_ruc.zip";
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
        if (is == null) {
            throw new IllegalStateException("Could not find:" + fileName);
        }

        try {
            byte[] zipFile = IOUtils.toByteArray(is);
            new MockServerClient(sunat.getHost(), sunat.getServerPort())
                    .when(request()
                            .withPath("/" + fileName)
                    )
                    .respond(response()
                            .withStatusCode(OK_200.code())
                            .withReasonPhrase(OK_200.reasonPhrase())
                            .withHeaders(
                                    header(CONTENT_TYPE.toString(), MediaType.APPLICATION_BINARY.toString()),
                                    header(CONTENT_DISPOSITION, "form-data; name=\"test.pdf\"; filename=\"" + fileName + "\""),
                                    header(CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                            )
                            .withBody(binary(zipFile))
                    );

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return new HashMap<>() {{
            put("searchpe.sunat.padronReducidoUrl", "http://" + sunat.getHost() + ":" + sunat.getServerPort() + "/" + fileName);
        }};
    }

    @Override
    public void stop() {
        sunat.stop();
    }
}
