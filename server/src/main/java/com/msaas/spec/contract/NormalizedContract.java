package com.msaas.spec.contract;

import java.util.ArrayList;
import java.util.List;

public class NormalizedContract {
    private String title;
    private String version;
    private List<MockRoute> routes = new ArrayList<>();

    public NormalizedContract() {
    }

    public NormalizedContract(String title, String version, List<MockRoute> routes) {
        this.title = title;
        this.version = version;
        this.routes = routes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<MockRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<MockRoute> routes) {
        this.routes = routes;
    }
}
