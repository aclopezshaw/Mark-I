package com.jarvis.markone.models;

public class TaskItem {
    private final String name;
    private final String status;
    private final String priority;
    private final boolean focus;
    private final String url;

    public TaskItem(String name, String status, String priority, boolean focus, String url) {
        this.name = name;
        this.status = status;
        this.priority = priority;
        this.focus = focus;
        this.url = url;
    }

    @Override
    public String toString() {
        return "- " + name + " | Status: " + status + " | Priority: " + priority + " | Focus: " + focus;
    }
}