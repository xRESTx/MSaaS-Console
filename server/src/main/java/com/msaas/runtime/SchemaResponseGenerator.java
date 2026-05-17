package com.msaas.runtime;

import com.msaas.spec.contract.MockSchemaDefinition;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Component
public class SchemaResponseGenerator {

    public Object generate(MockSchemaDefinition schema, String seed) {
        return generateValue(schema, new Random(seed(seed == null ? "" : seed)));
    }

    private Object generateValue(MockSchemaDefinition schema, Random random) {
        if (schema == null) {
            return Map.of("ok", true);
        }
        if (schema.isNullable() && random.nextInt(10) == 0) {
            return null;
        }
        if (!schema.getEnumValues().isEmpty()) {
            return schema.getEnumValues().get(random.nextInt(schema.getEnumValues().size()));
        }

        String type = schema.getType() == null ? "" : schema.getType().toLowerCase(Locale.ROOT);
        if ("object".equals(type) || !schema.getProperties().isEmpty()) {
            return objectValue(schema, random);
        }
        if ("array".equals(type)) {
            int count = 1 + random.nextInt(2);
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                items.add(generateValue(schema.getItems(), random));
            }
            return items;
        }
        if ("integer".equals(type)) {
            return integerValue(schema.getFormat(), random);
        }
        if ("number".equals(type)) {
            return numberValue(random);
        }
        if ("boolean".equals(type)) {
            return random.nextBoolean();
        }
        if ("string".equals(type) || type.isBlank()) {
            return stringValue(schema.getFormat(), random);
        }
        return Map.of("ok", true);
    }

    private Map<String, Object> objectValue(MockSchemaDefinition schema, Random random) {
        Map<String, Object> value = new LinkedHashMap<>();
        schema.getProperties().forEach((name, property) -> {
            boolean required = schema.getRequiredProperties().contains(name);
            if (required || random.nextInt(100) < 80) {
                value.put(name, generateValue(property, random));
            }
        });
        if (value.isEmpty() && !schema.getProperties().isEmpty()) {
            Map.Entry<String, MockSchemaDefinition> first = schema.getProperties().entrySet().iterator().next();
            value.put(first.getKey(), generateValue(first.getValue(), random));
        }
        return value.isEmpty() ? new LinkedHashMap<>(Map.of("ok", true)) : value;
    }

    private Object integerValue(String format, Random random) {
        if ("int64".equals(format)) {
            return Math.abs(random.nextLong() % 1_000_000_000L);
        }
        return random.nextInt(100_000);
    }

    private Object numberValue(Random random) {
        return Math.round(random.nextDouble(0.0, 10_000.0) * 100.0) / 100.0;
    }

    private String stringValue(String format, Random random) {
        if ("uuid".equals(format)) {
            return new UUID(random.nextLong(), random.nextLong()).toString();
        }
        if ("date".equals(format)) {
            return LocalDate.of(2024, 1, 1).plusDays(random.nextInt(730)).toString();
        }
        if ("date-time".equals(format)) {
            return OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)
                    .plusSeconds(random.nextInt(31_536_000))
                    .toString();
        }
        if ("email".equals(format)) {
            return "user" + random.nextInt(10_000) + "@example.com";
        }
        if ("uri".equals(format) || "url".equals(format)) {
            return "https://example.com/resource/" + random.nextInt(10_000);
        }
        return "value-" + random.nextInt(10_000);
    }

    private long seed(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException ex) {
            return value.hashCode();
        }
    }
}
