/*
 *
 *  Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package io.openshift.booster;

import io.restassured.RestAssured;
import org.arquillian.cube.openshift.impl.enricher.AwaitRoute;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;

/**
 * @author Heiko Braun
 */
@RunWith(Arquillian.class)
public class OpenshiftIT {

    @RouteURL(value = "${app.name}", path = "/api/greeting")
    @AwaitRoute
    private String url;

    @Before
    public void setup() {
        RestAssured.baseURI = url;
    }

    @Test
    public void testServiceInvocation() {
        when()
                .get()
        .then()
                .statusCode(200)
                .body(containsString("Hello, World!"));
    }

    @Test
    public void testServiceInvocationWithParam() {
        given()
                .queryParam("name", "Peter")
        .when()
                .get()
        .then()
                .statusCode(200)
                .body(containsString("Hello, Peter!"));
    }
}
