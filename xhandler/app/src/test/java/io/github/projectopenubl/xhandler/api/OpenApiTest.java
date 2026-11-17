package io.github.projectopenubl.xhandler.api;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The OpenAPI document is good enough for SDK generators as is (DESIGN.md §12). */
@QuarkusTest
class OpenApiTest {

    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");
    private static final Pattern OPERATION_ID = Pattern.compile("[a-z][A-Za-z0-9]*");
    private static final Pattern SNAKE_CASE = Pattern.compile("[a-z0-9_]+");

    private JsonNode spec;

    @BeforeEach
    void load() throws Exception {
        String body = given().accept("application/json").get("/q/openapi?format=json")
                .then().statusCode(200).extract().asString();
        spec = new ObjectMapper().readTree(body);
    }

    private List<Map.Entry<String, JsonNode>> operations() {
        List<Map.Entry<String, JsonNode>> operations = new ArrayList<>();
        spec.get("paths").properties().forEach(path -> path.getValue().properties().forEach(op -> {
            if (METHODS.contains(op.getKey())) {
                operations.add(Map.entry(op.getKey().toUpperCase() + " " + path.getKey(), op.getValue()));
            }
        }));
        return operations;
    }

    @Test
    void every_operation_has_a_unique_explicit_operation_id() {
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, JsonNode> op : operations()) {
            JsonNode id = op.getValue().get("operationId");
            assertNotNull(id, op.getKey() + " needs @Operation(operationId = ...)");
            assertTrue(OPERATION_ID.matcher(id.asText()).matches(), op.getKey() + ": " + id.asText());
            assertTrue(seen.add(id.asText()), "duplicate operationId " + id.asText());
        }
    }

    @Test
    void public_operations_have_no_security_and_the_rest_need_a_bearer_token() {
        for (Map.Entry<String, JsonNode> op : operations()) {
            String path = op.getKey().substring(op.getKey().indexOf(' ') + 1);
            JsonNode security = op.getValue().get("security");
            if (OpenApiDocumentFilter.isPublic(path)) {
                assertNull(security, op.getKey());
            } else {
                assertEquals("[{\"bearer\":[]}]", String.valueOf(security), op.getKey());
            }
        }
        assertEquals("bearer", spec.at("/components/securitySchemes/bearer/scheme").asText());
        spec.get("paths").fieldNames().forEachRemaining(
                path -> assertFalse(path.startsWith("/api/admin/"), "the admin API stays out of the document: " + path));
    }

    @Test
    void errors_reference_the_api_error_schema() {
        assertEquals(Set.of("kind", "message", "detail"), names(spec.at("/components/schemas/ApiError/properties")));
        for (Map.Entry<String, JsonNode> op : operations()) {
            if (op.getKey().contains(" /api/")) {
                assertEquals("#/components/schemas/ApiError",
                        op.getValue().at("/responses/4XX/content/application~1json/schema/$ref").asText(), op.getKey());
            }
        }
    }

    @Test
    void property_names_are_snake_case_as_on_the_wire() {
        List<String> camel = new ArrayList<>();
        collect(spec, "", camel);
        assertTrue(camel.isEmpty(), "not snake_case: " + camel);
        assertTrue(spec.at("/components/schemas/DocumentView/properties").has("serie_numero"));
        assertEquals(Set.of("items", "limit", "offset"), names(spec.at("/components/schemas/PageDocumentView/properties")));
    }

    private static void collect(JsonNode node, String where, List<String> camel) {
        if (node.isObject()) {
            JsonNode properties = node.get("properties");
            if (properties != null && properties.isObject()) {
                for (String name : names(properties)) {
                    if (!SNAKE_CASE.matcher(name).matches()) {
                        camel.add(where + "." + name);
                    }
                }
            }
            node.properties().forEach(e -> collect(e.getValue(), where + "/" + e.getKey(), camel));
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, where, camel));
        }
    }

    private static Set<String> names(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
