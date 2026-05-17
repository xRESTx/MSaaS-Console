package com.msaas.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msaas.instance.ResponseRule;
import com.msaas.spec.contract.MockRoute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseRuleApplierTest {
    private final ResponseRuleApplier applier = new ResponseRuleApplier(new TemplateRenderer(new ObjectMapper()));

    @Test
    void appliesRulesOnlyToMatchingOperations() {
        MockRoute route = new MockRoute("GET", "/orders/{id}", "getOrder", 200);
        RouteMatch match = new RouteMatch(route, Map.of("id", "ord-1"));

        ResponseRule fixed = new ResponseRule();
        fixed.setId("fixed");
        fixed.setEnabled(true);
        fixed.setPriority(100);
        fixed.setOperationId("getOrder");
        fixed.setFieldPath("customer.email");
        fixed.setType("FIXED");
        fixed.setFixedValue("vip@example.com");

        ResponseRule template = new ResponseRule();
        template.setId("template");
        template.setEnabled(true);
        template.setPriority(90);
        template.setMethod("GET");
        template.setPathTemplate("/orders/{id}");
        template.setFieldPath("trace");
        template.setType("TEMPLATE");
        template.setTemplate("{{path.id}}:{{query.tenant}}");

        ResponseRule other = new ResponseRule();
        other.setId("other");
        other.setEnabled(true);
        other.setPriority(200);
        other.setMethod("POST");
        other.setPathTemplate("/orders");
        other.setFieldPath("customer.email");
        other.setType("FIXED");
        other.setFixedValue("wrong@example.com");

        ResponseRuleApplier.AppliedResponse applied = applier.applyWithMetadata(
                Map.of("customer", Map.of("email", "old@example.com")),
                List.of(fixed, template, other),
                match,
                null,
                Map.of("tenant", List.of("acme")),
                Map.of(),
                "seed"
        );
        Object result = applied.body();

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) map.get("customer");
        assertThat(customer).containsEntry("email", "vip@example.com");
        assertThat(map).containsEntry("trace", "ord-1:acme");
        assertThat(applied.appliedRuleIds()).containsExactly("fixed", "template");
    }
}
