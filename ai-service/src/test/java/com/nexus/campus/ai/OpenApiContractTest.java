package com.nexus.campus.ai;

import com.nexus.campus.ai.api.dto.ErrorResponse;
import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.api.dto.SafetyRequest;
import com.nexus.campus.ai.api.dto.SafetyResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractTest {

    private final OpenAPI api = new OpenAPIV3Parser().read("openapi/ai-service.yaml");

    @Test
    void theCommittedContractDeclaresTheTwoInferenceEndpoints() {
        assertNotNull(api, "OpenAPI file must parse");
        assertTrue(api.getPaths().containsKey("/v1/reviews"));
        assertTrue(api.getPaths().containsKey("/v1/safety"));
        assertTrue(api.getPaths().containsKey("/readyz"));
    }

    @Test
    void theCommittedContractPassesSemanticValidation() {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
                "openapi/ai-service.yaml", null, new ParseOptions());
        assertNotNull(result.getOpenAPI(), "OpenAPI must be readable");
        assertTrue(result.getMessages() == null || result.getMessages().isEmpty(),
                () -> "semantic validation errors: " + result.getMessages());
    }

    @Test
    void everyDtoFieldMatchesTheCommittedSchema() {
        assertSchema(ReviewRequest.class);
        assertSchema(ReviewRequest.Budget.class);
        assertSchema(ReviewResponse.class);
        assertSchema(ReviewResponse.Usage.class);
        assertSchema(SafetyRequest.class);
        assertSchema(SafetyResponse.class);
        assertSchema(ErrorResponse.class);
    }

    private void assertSchema(Class<? extends Record> type) {
        Schema<?> schema = api.getComponents().getSchemas().get(type.getSimpleName());
        assertNotNull(schema, "missing schema " + type.getSimpleName());
        Set<String> dtoFields = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(dtoFields, new LinkedHashSet<>(schema.getProperties().keySet()),
                type.getSimpleName() + " drifted from openapi/ai-service.yaml");
    }
}
