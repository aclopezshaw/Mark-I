package com.jarvis.markone.models;

public class DatabaseRegistryItem {
    private final String project;
    private final String database;
    private final String purpose;
    private final String databaseId;
    private final String url;

    public DatabaseRegistryItem(String project, String database, String purpose, String databaseId, String url) {
        this.project = project;
        this.database = database;
        this.purpose = purpose;
        this.databaseId = databaseId;
        this.url = url;
    }

    @Override
    public String toString() {
        return "- " + database + " | Project: " + project + " | Purpose: " + purpose + " | ID: " + databaseId;
    }
}