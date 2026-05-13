package com.msaas.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {
    private final TemplateRenderer renderer = new TemplateRenderer(new ObjectMapper());

    @Test
    void rendersPathQueryHeaderBodyAndHelpers() {
        Object rendered = renderer.render(
                Map.of(
                        "id", "{{path.id}}",
                        "tenant", "{{query.tenant}}",
                        "trace", "{{header.x-trace-id}}",
                        "title", "{{body.title}}",
                        "requestId", "{{uuid}}"
                ),
                Map.of("id", "42"),
                Map.of("tenant", List.of("acme")),
                Map.of("X-Trace-Id", "trace-1"),
                "{\"title\":\"Demo\"}"
        );

        assertThat(rendered).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> renderedMap = (Map<String, Object>) rendered;
        assertThat(renderedMap)
                .containsEntry("id", "42")
                .containsEntry("tenant", "acme")
                .containsEntry("trace", "trace-1")
                .containsEntry("title", "Demo");
        assertThat(renderedMap.get("requestId")).isNotEqualTo("");
    }
}
