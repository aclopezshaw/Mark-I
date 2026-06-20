package com.jarvis.markone.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.markone.config.NotionConfig;
import com.jarvis.markone.models.DatabaseRegistryItem;
import com.jarvis.markone.models.ProjectItem;
import com.jarvis.markone.models.TaskItem;
import com.jarvis.markone.models.WorkstreamItem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;


/**
 * Primary integration layer between JARVIS and Notion.
 *
 * Provides retrieval, mutation, reporting, and field note
 * operations against the JARVIS workspace databases.
 *
 * Mark I Responsibilities:
 * - Task retrieval
 * - Focus Queue retrieval
 * - Project retrieval
 * - Workstream retrieval
 * - Task creation
 * - Task updates
 * - Reporting
 * - Field Notes retrieval
 *
 * Used by:
 * - Mark I Service Layer
 * - Future Access Layer controllers
 * - Future automation workflows
 */
public class NotionService {
    private final HttpClient client;
    private final String notionToken;
    private final ObjectMapper objectMapper;

    // =====================================================
    // CONSTRUCTOR
    // =====================================================

    /**
     * Creates a NotionService instance using the configured Notion integration token.
     *
     * @param notionToken Notion API integration token used for authenticated requests.
     */
    public NotionService(String notionToken) {
        this.client = HttpClient.newHttpClient();
        this.notionToken = notionToken;
        this.objectMapper = new ObjectMapper();
    }

    // =====================================================
    // AUTHENTICATION
    // =====================================================

    /**
     * Validates that the configured Notion token can successfully authenticate
     * against the Notion API.
     *
     * @return true if authentication succeeds.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
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

    // =====================================================
    // LOW LEVEL NOTION OPERATIONS
    // =====================================================

    /**
     * Fetches a Notion page by page ID.
     *
     * @param pageId Notion page ID.
     * @return Raw JSON response from the Notion API.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String fetchPage(String pageId) throws Exception {
        HttpRequest request = baseRequest("https://api.notion.com/v1/pages/" + pageId)
                .GET()
                .build();

        return send(request);
    }

    /**
     * Queries a Notion database by database ID.
     *
     * @param databaseId Notion database ID.
     * @return Raw JSON response containing database query results.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String queryDatabase(String databaseId) throws Exception {
        List<JsonNode> allResults = new ArrayList<>();
        String nextCursor = null;
        boolean hasMore;

        do {
            String requestBody;

            if (nextCursor == null) {
                requestBody = """
                {
                "page_size": 100
                }
                """;
            } else {
                requestBody = """
                {
                "page_size": 100,
                "start_cursor": "%s"
                }
                """.formatted(nextCursor);
            }

            HttpRequest request = baseRequest("https://api.notion.com/v1/databases/" + databaseId + "/query")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            String responseJson = send(request);
            JsonNode root = objectMapper.readTree(responseJson);

            for (JsonNode result : root.path("results")) {
                allResults.add(result);
            }

            hasMore = root.path("has_more").asBoolean(false);
            nextCursor = root.path("next_cursor").isNull()
                    ? null
                    : root.path("next_cursor").asText(null);

        } while (hasMore && nextCursor != null);

        return objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .put("object", "list")
                        .set("results", objectMapper.valueToTree(allResults))
        );
    }

    /**
     * Searches the Notion workspace for pages or databases matching the query.
     *
     * @param query Search text.
     * @return Raw JSON response containing search results.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
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

    /**
     * Retrieves child blocks for a Notion block or page.
     *
     * @param blockId Notion block or page ID.
     * @return Raw JSON response containing child blocks.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String getBlockChildren(String blockId) throws Exception {
        HttpRequest request = baseRequest(
                "https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100"
        )
                .GET()
                .build();

        return send(request);
    }

    // =====================================================
    // DATABASE REGISTRY
    // =====================================================

    /**
     * Retrieves the configured Database Registry page.
     *
     * @return Raw JSON response for the Database Registry page.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String getDatabaseRegistryPage() throws Exception {
        return fetchPage(NotionConfig.DATABASE_REGISTRY_PAGE_ID);
    }

    /**
     * Retrieves and parses Database Registry entries.
     *
     * @return List of DatabaseRegistryItem objects.
     * @throws Exception if retrieval or parsing fails.
     */
    public List<DatabaseRegistryItem> getDatabaseRegistryItems() throws Exception {
        String json = getBlockChildren(NotionConfig.DATABASE_REGISTRY_TABLE_BLOCK_ID);
        return parseDatabaseRegistryItems(json);
    }
    
    /**
     * Parses raw Database Registry block JSON into DatabaseRegistryItem objects.
     *
     * @param json Raw JSON response from the Notion API.
     * @return List of parsed DatabaseRegistryItem objects.
     * @throws Exception if JSON parsing fails.
     */
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

    /**
     * Extracts plain text from a table cell at the provided index.
     *
     * @param cells JSON node containing table row cells.
     * @param index Cell index to extract.
     * @return Plain text content of the cell, or an empty string if unavailable.
     */
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

    // =====================================================
    // MASTER TASKLIST
    // =====================================================

    /**
     * Retrieves the Master Tasklist database contents.
     *
     * @return Raw JSON response containing Master Tasklist records.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String getMasterTasklist() throws Exception {
        return queryDatabase(NotionConfig.MASTER_TASKLIST_DATABASE_ID);
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

    // =====================================================
    // FOCUS QUEUE
    // =====================================================

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

    // =====================================================
    // PROJECTS
    // =====================================================

    public String getProjects() throws Exception {
        return queryDatabase(NotionConfig.PROJECTS_DATABASE_ID);
    }

    /**
     * Retrieves and parses all Project records.
     *
     * @return List of ProjectItem objects.
     * @throws Exception if retrieval or parsing fails.
     */
    public List<ProjectItem> getProjectItems() throws Exception {
        String json = getProjects();
        return parseProjectItems(json);
    }

    /**
     * Parses raw Projects database JSON into ProjectItem objects.
     *
     * @param json Raw JSON response from the Notion API.
     * @return List of parsed ProjectItem objects.
     * @throws Exception if JSON parsing fails.
     */
    private List<ProjectItem> parseProjectItems(String json) throws Exception {
        List<ProjectItem> projects = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        for (JsonNode page : results) {
            JsonNode properties = page.path("properties");

            String name = properties.path("Project").path("title").isArray()
                    && properties.path("Project").path("title").size() > 0
                    ? properties.path("Project").path("title").get(0).path("plain_text").asText("")
                    : "";

            String status = properties.path("Status").path("select").path("name").asText("");
            String url = page.path("url").asText("");

            projects.add(new ProjectItem(name, status, url));
        }

        return projects;
    }

    // =====================================================
    // WORKSTREAMS
    // =====================================================

    public String getWorkstreams() throws Exception {
        return queryDatabase(NotionConfig.WORKSTREAMS_DATABASE_ID);
    }

    public List<WorkstreamItem> getWorkstreamItems() throws Exception {
        String json = getWorkstreams();
        return parseWorkstreamItems(json);
    }

    /**
     * Parses raw Workstreams database JSON into WorkstreamItem objects.
     *
     * @param json Raw JSON response from the Notion API.
     * @return List of parsed WorkstreamItem objects.
     * @throws Exception if JSON parsing fails.
     */
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

    // =====================================================
    // OTHER DATABASES
    // =====================================================

    public String getWorkoutLog() throws Exception {
        return queryDatabase(NotionConfig.WORKOUT_LOG_DATABASE_ID);
    }

    // =====================================================
    // TASK CREATION
    // =====================================================

    /**
     * Creates a task in the Master Tasklist using explicit Notion relation IDs.
     *
     * @param title Task title.
     * @param status Task status.
     * @param priority Task priority.
     * @param focus Whether the task should be added to the Focus Queue.
     * @param notes Task notes.
     * @param projectId Related Notion project page ID.
     * @param workstreamId Related Notion workstream page ID.
     * @return Raw JSON response for the created task page.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String createTask(
        String title,
        String status,
        String priority,
        boolean focus,
        String notes,
        String projectId,
        String workstreamId
        ) throws Exception {

        String body = """
        {
        "parent": {
            "database_id": "%s"
        },
        "properties": {
            "Task": {
            "title": [
                {
                "text": {
                    "content": "%s"
                }
                }
            ]
            },
            "Project": {
            "relation": [
                {
                "id": "%s"
                }
            ]
            },
            "Workstream": {
            "relation": [
                {
                "id": "%s"
                }
            ]
            },
            "Status": {
            "select": {
                "name": "%s"
            }
            },
            "Priority": {
            "select": {
                "name": "%s"
            }
            },
            "Focus": {
            "checkbox": %s
            },
            "Notes": {
            "rich_text": [
                {
                "text": {
                    "content": "%s"
                }
                }
            ]
            }
        }
        }
        """.formatted(
                NotionConfig.MASTER_TASKLIST_DATABASE_ID,
                title,
                projectId,
                workstreamId,
                status,
                priority,
                focus,
                notes
        );

        HttpRequest request = baseRequest("https://api.notion.com/v1/pages")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return send(request);
    }

    /**
     * Creates a task in the Master Tasklist using project and workstream names.
     *
     * Resolves the provided project and workstream names into Notion relation IDs
     * before creating the task.
     *
     * @param title Task title.
     * @param status Task status.
     * @param priority Task priority.
     * @param focus Whether the task should be added to the Focus Queue.
     * @param notes Task notes.
     * @param projectName Name of the related project.
     * @param workstreamName Name of the related workstream.
     * @return Raw JSON response for the created task page.
     * @throws Exception if relation resolution, request execution, or response handling fails.
     */
    public String createTaskByName(
        String title,
        String status,
        String priority,
        boolean focus,
        String notes,
        String projectName,
        String workstreamName
    ) throws Exception {

        String projectId = findProjectIdByName(projectName);
        String workstreamId = findWorkstreamIdByName(workstreamName);

        return createTask(
                title,
                status,
                priority,
                focus,
                notes,
                projectId,
                workstreamId
        );
    }

    /**
     * Finds a Notion project page ID by project name.
     *
     * @param projectName Project name to search for.
     * @return Matching Notion project page ID.
     * @throws Exception if the project cannot be found or retrieval fails.
     */
    private String findProjectIdByName(String projectName) throws Exception {
        String json = getProjects();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        for (JsonNode page : results) {
            JsonNode properties = page.path("properties");

            String name = properties.path("Project").path("title").isArray()
                    && properties.path("Project").path("title").size() > 0
                    ? properties.path("Project").path("title").get(0).path("plain_text").asText("")
                    : "";

            if (name.equalsIgnoreCase(projectName)) {
                return page.path("id").asText("");
            }
        }

        throw new IllegalArgumentException("Project not found: " + projectName);
    }

    /**
     * Finds a Notion workstream page ID by workstream name.
     *
     * @param workstreamName Workstream name to search for.
     * @return Matching Notion workstream page ID.
     * @throws Exception if the workstream cannot be found or retrieval fails.
     */
    private String findWorkstreamIdByName(String workstreamName) throws Exception {
        String json = getWorkstreams();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        for (JsonNode page : results) {
            JsonNode properties = page.path("properties");

            String name = properties.path("Title").path("title").isArray()
                    && properties.path("Title").path("title").size() > 0
                    ? properties.path("Title").path("title").get(0).path("plain_text").asText("")
                    : "";

            if (name.equalsIgnoreCase(workstreamName)) {
                return page.path("id").asText("");
            }
        }

        throw new IllegalArgumentException("Workstream not found: " + workstreamName);
    }

    // =====================================================
    // TASK UPDATES
    // =====================================================

    /**
     * Updates the Status property of a Master Tasklist task.
     *
     * @param pageId Notion page ID of the task to update.
     * @param status New status value.
     * @return Raw JSON response for the updated task page.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String updateTaskStatus(String pageId, String status) throws Exception {
        String body = """
        {
        "properties": {
            "Status": {
            "select": {
                "name": "%s"
            }
            }
        }
        }
        """.formatted(status);

        HttpRequest request = baseRequest("https://api.notion.com/v1/pages/" + pageId)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();

        return send(request);
    }

    /**
     * Updates the Focus checkbox property of a Master Tasklist task.
     *
     * @param pageId Notion page ID of the task to update.
     * @param focus Whether the task should appear in the Focus Queue.
     * @return Raw JSON response for the updated task page.
     * @throws Exception if the request fails or the Notion API returns an error.
     */
    public String updateTaskFocus(String pageId, boolean focus) throws Exception {
        String body = """
        {
        "properties": {
            "Focus": {
            "checkbox": %s
            }
        }
        }
        """.formatted(focus);

        HttpRequest request = baseRequest("https://api.notion.com/v1/pages/" + pageId)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();

        return send(request);
    }

    // =====================================================
    // REPORTING
    // =====================================================

    /**
     * Generates and prints a Master Tasklist summary report.
     *
     * The report includes task totals, status counts, priority counts,
     * Focus Queue count, and basic data integrity indicators.
     *
     * @throws Exception if task retrieval or parsing fails.
     */
    public void generateTaskSummaryReport() throws Exception {
        String json = getMasterTasklist();
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        int total = 0;

        int active = 0;
        int backlog = 0;
        int complete = 0;

        int critical = 0;
        int high = 0;
        int medium = 0;
        int low = 0;

        int focusCount = 0;
        int missingProject = 0;
        int missingWorkstream = 0;

        for (JsonNode page : results) {
            total++;

            JsonNode properties = page.path("properties");

            String status = properties.path("Status").path("select").path("name").asText("");
            String priority = properties.path("Priority").path("select").path("name").asText("");
            boolean focus = properties.path("Focus").path("checkbox").asBoolean(false);

            int projectCount = properties.path("Project").path("relation").size();
            int workstreamCount = properties.path("Workstream").path("relation").size();

            if (status.equalsIgnoreCase("Active")) active++;
            if (status.equalsIgnoreCase("Backlog")) backlog++;
            if (status.equalsIgnoreCase("Complete")) complete++;

            if (priority.equalsIgnoreCase("Critical")) critical++;
            if (priority.equalsIgnoreCase("High")) high++;
            if (priority.equalsIgnoreCase("Medium")) medium++;
            if (priority.equalsIgnoreCase("Low")) low++;

            if (focus) focusCount++;
            if (projectCount == 0) missingProject++;
            if (workstreamCount == 0) missingWorkstream++;
        }

        System.out.println();
        System.out.println("=== MK1-009 Master Tasklist Summary Report ===");
        System.out.println();
        System.out.println("Total Tasks: " + total);
        System.out.println();
        System.out.println("By Status:");
        System.out.println("- Active: " + active);
        System.out.println("- Backlog: " + backlog);
        System.out.println("- Complete: " + complete);
        System.out.println();
        System.out.println("By Priority:");
        System.out.println("- Critical: " + critical);
        System.out.println("- High: " + high);
        System.out.println("- Medium: " + medium);
        System.out.println("- Low: " + low);
        System.out.println();
        System.out.println("Focus Queue:");
        System.out.println("- Focused Tasks: " + focusCount);
        System.out.println();
        System.out.println("Data Integrity:");
        System.out.println("- Missing Project: " + missingProject);
        System.out.println("- Missing Workstream: " + missingWorkstream);
    }

    // =====================================================
    // FIELD NOTES
    // =====================================================

    /**
     * Retrieves and prints visible Field Notes page content.
     *
     * Supports common Notion block types such as paragraphs, headings,
     * bulleted list items, and numbered list items.
     *
     * @throws Exception if block retrieval or parsing fails.
     */
    public void printFieldNotes() throws Exception {
        String json = getBlockChildren(NotionConfig.FIELD_NOTES_PAGE_ID);
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");

        System.out.println();
        System.out.println("=== MK1-010 Field Notes Retrieval ===");
        System.out.println();

        for (JsonNode block : results) {
            String type = block.path("type").asText("");

            if (type.equals("paragraph")) {
                printRichTextBlock(block, "paragraph");
            } else if (type.equals("bulleted_list_item")) {
                System.out.print("- ");
                printRichTextBlock(block, "bulleted_list_item");
            } else if (type.equals("numbered_list_item")) {
                System.out.print("- ");
                printRichTextBlock(block, "numbered_list_item");
            } else if (type.equals("heading_1")) {
                System.out.print("# ");
                printRichTextBlock(block, "heading_1");
            } else if (type.equals("heading_2")) {
                System.out.print("## ");
                printRichTextBlock(block, "heading_2");
            } else if (type.equals("heading_3")) {
                System.out.print("### ");
                printRichTextBlock(block, "heading_3");
            }
        }
    }

    /**
     * Prints plain text content from a supported Notion rich text block.
     *
     * @param block Notion block JSON node.
     * @param type Notion block type.
     */
    private void printRichTextBlock(JsonNode block, String type) {
        JsonNode richText = block.path(type).path("rich_text");

        if (!richText.isArray() || richText.size() == 0) {
            return;
        }

        StringBuilder text = new StringBuilder();

        for (JsonNode item : richText) {
            text.append(item.path("plain_text").asText(""));
        }

        System.out.println(text);
    }

    // =====================================================
    // HTTP INFRASTRUCTURE
    // =====================================================

    /**
     * Creates a base authenticated HTTP request builder for Notion API requests.
     *
     * @param url Target Notion API URL.
     * @return Configured HttpRequest.Builder with standard Notion headers.
     */
    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", NotionConfig.NOTION_VERSION);
    }

    /**
     * Sends an HTTP request to the Notion API and validates the response.
     *
     * @param request Prepared HTTP request.
     * @return Raw response body.
     * @throws Exception if the request fails, is interrupted, or returns an error status.
     */
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