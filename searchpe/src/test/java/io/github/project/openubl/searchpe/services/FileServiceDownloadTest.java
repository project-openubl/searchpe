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

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileServiceDownloadTest {

    private static final byte[] CONTENT = {1, 2, 3, 4, 5};

    @TempDir
    Path workspace;

    private HttpServer server;

    private final CountDownLatch testFinished = new CountDownLatch(1);

    @AfterEach
    public void stopServer() {
        testFinished.countDown();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void downloadFile() throws Exception {
        FileService fileService = fileServiceFor(exchange -> {
            exchange.sendResponseHeaders(200, CONTENT.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(CONTENT);
            }
        });

        File downloadedFile = fileService.downloadFile();

        assertArrayEquals(CONTENT, Files.readAllBytes(downloadedFile.toPath()));
    }

    @Test
    public void downloadFileFailsOnHttpError() throws Exception {
        FileService fileService = fileServiceFor(exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        IOException exception = assertThrows(IOException.class, fileService::downloadFile);
        assertTrue(exception.getMessage().endsWith("failed with HTTP 404"), exception.getMessage());
        assertWorkspaceIsEmpty();
    }

    @Test
    public void downloadFileGivesUpWhenTheServerStalls() throws Exception {
        FileService fileService = fileServiceFor(exchange -> {
            exchange.sendResponseHeaders(200, CONTENT.length * 2L);
            OutputStream body = exchange.getResponseBody();
            body.write(CONTENT);
            body.flush();
            try {
                // Never send the rest of the body, until the test ends
                testFinished.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThrows(SocketTimeoutException.class, fileService::downloadFile);
        assertWorkspaceIsEmpty();
    }

    private FileService fileServiceFor(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/padron_reducido_ruc.zip", handler);
        server.start();

        FileService fileService = new FileService();
        fileService.workingDirectory = workspace.toString();
        fileService.zipURL = "http://localhost:" + server.getAddress().getPort() + "/padron_reducido_ruc.zip";
        fileService.connectTimeout = Duration.ofSeconds(5);
        fileService.readTimeout = Duration.ofMillis(500);
        return fileService;
    }

    private void assertWorkspaceIsEmpty() throws IOException {
        try (var files = Files.list(workspace)) {
            assertTrue(files.findAny().isEmpty(), "A partially downloaded file was left in the workspace");
        }
    }
}
