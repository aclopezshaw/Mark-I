package com.jarvis.markone.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.markone.config.NotionConfig;
import com.jarvis.markone.models.DatabaseRegistryItem;
import com.jarvis.markone.models.TaskItem;
import com.jarvis.markone.models.WorkstreamItem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;

public class NotionService {
    private final HttpClient client;
    private final String notionToken;
    private final ObjectMapper objectMapper;

    public NotionService(String notionToken) {
        this.client = HttpClient.newHttpClient();
        this.notionToken = notionToken;
        this.objectMapper = new ObjectMapper();
    }

    public boolean validateAuthentication() throws Exception {
        HttpRequest request = baseRequest("https://api.notion.com/v1/users/me")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Authentication status code: " + response.statusCode());

        if (response.statusCode() == 200) {
            System.out.println("✅ Mark I authenticated with Notion.");
            return true;
        }

        System.out.println("❌ Notion authentication failed.");
        System.out.println(response.body());
        return false;
    }

    public String fetchPage(String pageId) throws Exception {
        HttpRequest request = baseRequest("https://api.notion.com/v1/pages/" + pageId)
                .GET()
                .build();

        return send(request);
    }

    public String queryDatabase(String databaseId) throws Exception {
        String requestBody = """
                {
                  "page_size": 10
                }
                """;

        HttpRequest request = baseRequest("https://api.notion.com/v1/databases/" + databaseId + "/query")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return send(request);
    }

    public String search(String query) throws Exception {
        String requestBody = """
                {
                  "query": "%s",
                  "page_size": 10
                }
                """.formatted(query);

        HttpRequest request = baseRequest("https://api.notion.com/v1/search")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return send(request);
    }

    public String getDatabaseRegistryPage() throws Exception {
        return fetchPage(NotionConfig.DATABASE_REGISTRY_PAGE_ID);
    }

    public String getMasterTasklist() throws Exception {
        return queryDatabase(NotionConfig.MASTER_TASKLIST_DATABASE_ID);
    }

    public String getProjects() throws Exception {
        return queryDatabase(NotionConfig.PROJECTS_DATABASE_ID);
    }

    public String getWorkstreams() throws Exception {
        return queryDatabase(NotionConfig.WORKSTREAMS_DATABASE_ID);
    }

    public String getWorkoutLog() throws Exception {
        return queryDatabase(NotionConfig.WORKOUT_LOG_DATABASE_ID);
    }

    public String getFocusQueue() throws Exception {
        String requestBody = """
            {
              "filter": {
                "and": [
                  {
                    "property": "Focus",
                    "checkbox": {
                      "equals": true
                    }
                  },
                  {
                    "property": "Status",
                    "select": {
                      "does_not_equal": "Complete"
                    }
                  }
                ]
              },
              "page_size": 25
            }
            """;

        HttpRequest request = baseRequest("https://api.notion.com/v1/databases/" + NotionConfig.MASTER_TASKLIST_DATABASE_ID + "/query")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        return send(request);
    }

    public List<TaskItem> getFocusQueueItems() throws Exception {
        String json = getFocusQueue();
        return parseTaskItems(json);
    }

    public List<TaskItem> getMasterTasklistItems() throws Exception {
        String json = getMasterTasklist();
        return parseTaskItems(json);
    }

    private List<TaskItem> parseTaskItems(String json) throws Exception {
        List<TaskItem> tasks = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        for (JsonNode page : results) {
            JsonNode properties = page.path("properties");

            String name = properties.path("Task").path("title").isArray()
                    && properties.path("Task").path("title").size() > 0
                    ? properties.path("Task").path("title").get(0).path("plain_text").asText("")
                    : "";

            String status = properties.path("Status").path("select").path("name").asText("");
            String priority = properties.path("Priority").path("select").path("name").asText("");
            boolean focus = properties.path("Focus").path("checkbox").asBoolean(false);
            String url = page.path("url").asText("");

            tasks.add(new TaskItem(name, status, priority, focus, url));
        }

        return tasks;
    }

    public List<WorkstreamItem> getWorkstreamItems() throws Exception {
        String json = getWorkstreams();
        return parseWorkstreamItems(json);
    }

    private List<WorkstreamItem> parseWorkstreamItems(String json) throws Exception {
        List<WorkstreamItem> workstreams = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        for (JsonNode page : results) {
            JsonNode properties = page.path("properties");

            String name = properties.path("Title").path("title").isArray()
                && properties.path("Title").path("title").size() > 0
                ? properties.path("Title").path("title").get(0).path("plain_text").asText("")
                : "";

            String status = properties.path("Status").path("select").path("name").asText("");
            String url = page.path("url").asText("");

            workstreams.add(new WorkstreamItem(name, status, url));
        }

        return workstreams;
    }

    public String getBlockChildren(String blockId) throws Exception {
        HttpRequest request = baseRequest(
                "https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100"
        )
                .GET()
                .build();

        return send(request);
    }

    public List<DatabaseRegistryItem> getDatabaseRegistryItems() throws Exception {
        String json = getBlockChildren(NotionConfig.DATABASE_REGISTRY_TABLE_BLOCK_ID);
        return parseDatabaseRegistryItems(json);
    }
    
    private List<DatabaseRegistryItem> parseDatabaseRegistryItems(String json) throws Exception {
        List<DatabaseRegistryItem> items = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        boolean headerSkipped = false;

        for (JsonNode row : results) {
            JsonNode cells = row.path("table_row").path("cells");

            if (!headerSkipped) {
                headerSkipped = true;
                continue;
            }

            String project = getCellText(cells, 0);
            String database = getCellText(cells, 1);
            String purpose = getCellText(cells, 2);
            String databaseId = getCellText(cells, 3);
            String url = getCellText(cells, 4);

            items.add(new DatabaseRegistryItem(project, database, purpose, databaseId, url));
        }

        return items;
    }

private String getCellText(JsonNode cells, int index) {
    JsonNode cell = cells.get(index);

    if (cell == null || !cell.isArray() || cell.size() == 0) {
        return "";
    }

    StringBuilder text = new StringBuilder();

    for (JsonNode richText : cell) {
        text.append(richText.path("plain_text").asText(""));
    }

    return text.toString();
}

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", NotionConfig.NOTION_VERSION);
    }

    private String send(HttpRequest request) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status code: " + response.statusCode());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            System.out.println("❌ Request failed.");
            System.out.println(response.body());
            throw new RuntimeException("Notion request failed with status " + response.statusCode());
        }

        System.out.println("✅ Request successful.");
        return response.body();
    }
}