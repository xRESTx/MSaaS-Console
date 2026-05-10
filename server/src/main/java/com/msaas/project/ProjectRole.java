package com.msaas.project;

public enum ProjectRole {
    VIEWER(10),
    MEMBER(20),
    OWNER(30);

    private final int level;

    ProjectRole(int level) {
        this.level = level;
    }

    public boolean atLeast(ProjectRole required) {
        return level >= required.level;
    }
}
