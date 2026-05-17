package com.msaas.spec.contract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockSchemaDefinition {
    private String type;
    private String format;
    private boolean nullable;
    private List<Object> enumValues = new ArrayList<>();
    private List<String> requiredProperties = new ArrayList<>();
    private Map<String, MockSchemaDefinition> properties = new LinkedHashMap<>();
    private MockSchemaDefinition items;

    public MockSchemaDefinition() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public List<Object> getEnumValues() {
        if (enumValues == null) {
            enumValues = new ArrayList<>();
        }
        return enumValues;
    }

    public void setEnumValues(List<Object> enumValues) {
        this.enumValues = enumValues == null ? new ArrayList<>() : enumValues;
    }

    public List<String> getRequiredProperties() {
        if (requiredProperties == null) {
            requiredProperties = new ArrayList<>();
        }
        return requiredProperties;
    }

    public void setRequiredProperties(List<String> requiredProperties) {
        this.requiredProperties = requiredProperties == null ? new ArrayList<>() : requiredProperties;
    }

    public Map<String, MockSchemaDefinition> getProperties() {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        return properties;
    }

    public void setProperties(Map<String, MockSchemaDefinition> properties) {
        this.properties = properties == null ? new LinkedHashMap<>() : properties;
    }

    public MockSchemaDefinition getItems() {
        return items;
    }

    public void setItems(MockSchemaDefinition items) {
        this.items = items;
    }
}
