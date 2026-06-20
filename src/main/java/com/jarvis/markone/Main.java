package com.jarvis.markone;

import com.jarvis.markone.models.DatabaseRegistryItem;
import com.jarvis.markone.models.ProjectItem;
import com.jarvis.markone.models.TaskItem;
import com.jarvis.markone.models.WorkstreamItem;
import com.jarvis.markone.services.NotionService;

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

            NotionService notionService = new NotionService(notionToken);

            if (!notionService.validateAuthentication()) {
                return;
            }

            System.out.println();
            System.out.println("=== Database Registry Items ===");
            for (DatabaseRegistryItem item : notionService.getDatabaseRegistryItems()) {
                System.out.println(item);
            }

            System.out.println();
            System.out.println("=== Master Tasklist Items ===");
            for (TaskItem task : notionService.getMasterTasklistItems()) {
                System.out.println(task);
            }

            System.out.println();
            System.out.println("=== Focus Queue Items ===");
            for (TaskItem task : notionService.getFocusQueueItems()) {
                System.out.println(task);
            }

            System.out.println();
            System.out.println("=== Project Items ===");
            for (ProjectItem project : notionService.getProjectItems()) {
                System.out.println(project);
            }

            System.out.println();
            System.out.println("=== Workstream Items ===");
            for (WorkstreamItem workstream : notionService.getWorkstreamItems()) {
                System.out.println(workstream);
            }

            /*
            System.out.println();
            System.out.println("=== MK1-006 Test ===");
            String mk6Result = notionService.createTaskByName(
                    "MK1-006 Name Resolver Test Task",
                    "Active",
                    "Low",
                    false,
                    "Created by Mark I using project/workstream name resolution.",
                    "Jarvis Command Center",
                    "Review and Maintenance"
            );
            System.out.println(mk6Result);

            System.out.println();
            System.out.println("=== MK1-007 Test ===");
            String mk7Result = notionService.updateTaskStatus(
                    "385bc7d80f4581cab1a3ff26a9cf9107",
                    "Complete"
            );
            System.out.println(mk7Result);
            
            System.out.println();
            System.out.println("=== MK1-008 Test ===");
            String focusResult = notionService.updateTaskFocus(
                    "385bc7d80f45813fada4f78611656975",
                    true
            );
            System.out.println(focusResult);
            */

           System.out.println();
            System.out.println("=== MK1-009 Test ===");
            notionService.generateTaskSummaryReport();

            System.out.println();
            System.out.println("=== MK1-010 Test ===");
            notionService.printFieldNotes();

            System.out.println();
            System.out.println("=== Workout Log ===");
            String workoutLog = notionService.getWorkoutLog();
            System.out.println("Workout Log response length: " + workoutLog.length());

            System.out.println();
            System.out.println("✅ Mark I read-only Notion service layer smoke test complete.");

        } catch (Exception e) {
            System.out.println("❌ Mark I service layer test failed.");
            e.printStackTrace();
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