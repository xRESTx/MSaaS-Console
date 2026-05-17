package com.msaas.runtime;

import com.msaas.spec.contract.MockSchemaDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaResponseGeneratorTest {
    private final SchemaResponseGenerator generator = new SchemaResponseGenerator();

    @Test
    void generatesDeterministicNestedJsonFromSchema() {
        MockSchemaDefinition order = objectSchema();

        Object first = generator.generate(order, "same-seed");
        Object second = generator.generate(order, "same-seed");
        Object third = generator.generate(order, "other-seed");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(third);
        assertThat(first).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) first;
        assertThat(body).containsKeys("id", "status", "customer", "items");
    }

    private MockSchemaDefinition objectSchema() {
        MockSchemaDefinition order = new MockSchemaDefinition();
        order.setType("object");
        order.setRequiredProperties(List.of("id", "status", "customer", "items"));

        MockSchemaDefinition id = new MockSchemaDefinition();
        id.setType("string");
        id.setFormat("uuid");

        MockSchemaDefinition status = new MockSchemaDefinition();
        status.setType("string");
        status.setEnumValues(List.of("paid", "pending"));

        MockSchemaDefinition customer = new MockSchemaDefinition();
        customer.setType("object");
        MockSchemaDefinition email = new MockSchemaDefinition();
        email.setType("string");
        email.setFormat("email");
        customer.setProperties(Map.of("email", email));
        customer.setRequiredProperties(List.of("email"));

        MockSchemaDefinition item = new MockSchemaDefinition();
        item.setType("object");
        MockSchemaDefinition price = new MockSchemaDefinition();
        price.setType("number");
        item.setProperties(Map.of("price", price));
        item.setRequiredProperties(List.of("price"));
        MockSchemaDefinition items = new MockSchemaDefinition();
        items.setType("array");
        items.setItems(item);

        order.setProperties(Map.of("id", id, "status", status, "customer", customer, "items", items));
        return order;
    }
}
