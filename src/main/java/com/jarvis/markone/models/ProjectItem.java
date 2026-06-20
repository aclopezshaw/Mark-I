package com.jarvis.markone.models;

public class ProjectItem {
    private final String name;
    private final String status;
    private final String url;

    public ProjectItem(String name, String status, String url) {
        this.name = name;
        this.status = status;
        this.url = url;
    }

    @Override
    public String toString() {
        return "- " + name + " | Status: " + status;
    }
}