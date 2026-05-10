package com.msaas.spec;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractParserTest {

    private final OpenApiContractParser parser = new OpenApiContractParser();

    @Test
    void normalizesOpenApiRoutesAndExamples() {
        String source = """
                openapi: 3.0.3
                info:
                  title: Orders
                  version: 1.0.0
                paths:
                  /orders/{id}:
                    get:
                      responses:
                        "200":
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: object
                                properties:
                                  id:
                                    type: string
                                  paid:
                                    type: boolean
                """;

        OpenApiContractParser.ParsedContract parsed = parser.parse(source);

        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.contract().getRoutes()).hasSize(1);
        assertThat(parsed.contract().getRoutes().getFirst().getPathTemplate()).isEqualTo("/orders/{id}");
        assertThat(parsed.contract().getRoutes().getFirst().defaultResponse().getBody()).isInstanceOf(Map.class);
    }

    @Test
    void extractsRequiredQueryHeaderAndBodyRules() {
        String source = """
                openapi: 3.0.3
                info:
                  title: Orders
                  version: 1.0.0
                paths:
                  /orders:
                    post:
                      parameters:
                        - name: tenant
                          in: query
                          required: true
                          schema:
                            type: string
                        - name: X-Trace-Id
                          in: header
                          required: true
                          schema:
                            type: string
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                      responses:
                        "201":
                          description: Created
                """;

        OpenApiContractParser.ParsedContract parsed = parser.parse(source);

        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.contract().getRoutes().getFirst().getRequiredQueryParameters()).containsExactly("tenant");
        assertThat(parsed.contract().getRoutes().getFirst().getRequiredHeaderParameters()).containsExactly("X-Trace-Id");
        assertThat(parsed.contract().getRoutes().getFirst().isRequestBodyRequired()).isTrue();
    }
}
