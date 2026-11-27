package com.nexus.campus.ai.model;

import java.util.Map;

/**
 * A provider-enforced JSON schema. The service owns this rather than the application:
 * the schema is part of the model-facing request, like the prompt text.
 */
public record OutputSchema(String name, Map<String, Object> schema, boolean strict) {
}
