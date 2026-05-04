package com.msaas.runtime;

import com.msaas.spec.contract.MockRoute;
import com.msaas.spec.contract.NormalizedContract;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
