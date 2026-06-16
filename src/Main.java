import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            String notionToken = loadEnvValue("NOTION_TOKEN");

            if (notionToken == null || notionToken.isBlank()) {
                System.out.println("❌ NOTION_TOKEN not found.");
                return;
            }

            HttpClient client = HttpClient.newHttpClient();

            validateAuthentication(client, notionToken);
            String[] targets = {
                "Master Tasklist",
                "Workstreams",
                "Jarvis Development",
                "Focus Queue",
                "Backlog",
                "Completed Tasks",
                "Reading Dashboard",
                "RP Command Station",
                "Spartan 2027",
                "Nursing School HQ"
            };

            for (String target : targets) {
                searchNotion(client, notionToken, target);
            }

        } catch (Exception e) {
            System.out.println("❌ Mark I test failed.");
            e.printStackTrace();
        }
    }

    private static void validateAuthentication(HttpClient client, String notionToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/users/me"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Authentication status code: " + response.statusCode());

        if (response.statusCode() == 200) {
            System.out.println("✅ Mark I authenticated with Notion.");
        } else {
            System.out.println("❌ Notion authentication failed.");
            System.out.println(response.body());
        }
    }

    private static void searchNotion(HttpClient client, String notionToken, String query) throws Exception {
        String requestBody = """
                {
                  "query": "%s",
                  "page_size": 10
                }
                """.formatted(query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/search"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println();
        System.out.println("Search status code: " + response.statusCode());

        if (response.statusCode() == 200) {
            System.out.println("✅ Search request successful.");
            System.out.println("Raw response:");
            System.out.println(response.body());
        } else {
            System.out.println("❌ Search request failed.");
            System.out.println(response.body());
        }
    }

    private static String loadEnvValue(String key) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(".env"));

        for (String line : lines) {
            if (line.startsWith(key + "=")) {
                return line.substring((key + "=").length()).trim();
            }
        }

        return null;
    }
}