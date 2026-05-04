package com.msaas.runtime;

import com.msaas.spec.contract.MockRoute;

import java.util.Map;

public record RouteMatch(MockRoute route, Map<String, String> variables) {
}
