package com.msaas.runtime;

import com.msaas.spec.contract.MockRoute;
import com.msaas.spec.contract.NormalizedContract;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteMatcherTest {

    private final RouteMatcher matcher = new RouteMatcher();

    @Test
    void matchesTemplatedRouteAndExtractsVariables() {
        MockRoute route = new MockRoute("GET", "/orders/{id}", "getOrder", 200);
        NormalizedContract contract = new NormalizedContract("Demo", "1.0.0", List.of(route));

        RouteMatch match = matcher.match(contract, "GET", "/orders/42").orElseThrow();

        assertThat(match.route()).isSameAs(route);
        assertThat(match.variables()).containsEntry("id", "42");
    }

    @Test
    void doesNotMatchDifferentOwnerPathShape() {
        MockRoute route = new MockRoute("GET", "/orders/{id}", "getOrder", 200);
        NormalizedContract contract = new NormalizedContract("Demo", "1.0.0", List.of(route));

        assertThat(matcher.match(contract, "GET", "/orders/42/items")).isEmpty();
    }

    @Test
    void requiresDeclaredQueryHeaderAndBodyRules() {
        MockRoute route = new MockRoute("POST", "/orders", "createOrder", 201);
        route.setRequiredQueryParameters(List.of("tenant"));
        route.setRequiredHeaderParameters(List.of("X-Trace-Id"));
        route.setRequestBodyRequired(true);
        NormalizedContract contract = new NormalizedContract("Demo", "1.0.0", List.of(route));

        assertThat(matcher.match(contract, "POST", "/orders", Map.of("tenant", List.of("acme")), Map.of("x-trace-id", "1"), "{}"))
                .isPresent();
        assertThat(matcher.match(contract, "POST", "/orders", Map.of("tenant", List.of("acme")), Map.of(), "{}"))
                .isEmpty();
    }
}
