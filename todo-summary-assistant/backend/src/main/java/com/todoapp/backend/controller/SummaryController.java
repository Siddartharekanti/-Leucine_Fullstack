package com.todoapp.backend.controller;

import com.todoapp.backend.model.Todo;
import com.todoapp.backend.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SummaryController {

    private final TodoService todoService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${slack.webhook.url}")
    private String slackWebhookUrl;

    @PostMapping("/summarize")
    public ResponseEntity<String> summarizeAndSendToSlack() {
        List<Todo> todos = todoService.getPendingTodos();

        if (todos.isEmpty()) {
            return ResponseEntity.ok("✅ No pending todos to summarize.");
        }

        StringBuilder prompt = new StringBuilder("Summarize the following pending to-do items:\n");
        for (Todo todo : todos) {
            prompt.append("- ").append(todo.getTitle())
                  .append(": ").append(todo.getDescription()).append("\n");
        }

        try {
            String summary = callGeminiAPI(prompt.toString());
            sendToSlack(summary);
            return ResponseEntity.ok("✅ Summary posted to Slack successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("❌ Error: " + e.getMessage());
        }
    }

    private String callGeminiAPI(String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        Map responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("candidates")) {
            throw new RuntimeException("Invalid response from Gemini API");
        }

        Map firstCandidate = (Map) ((List<?>) responseBody.get("candidates")).get(0);
        Map contentMap = (Map) firstCandidate.get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");

        return parts.get(0).get("text").toString().trim();
    }

    private void sendToSlack(String summary) {
        Map<String, String> payload = new HashMap<>();
        payload.put("text", "*📋 Todo Summary:*\n" + summary);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

        restTemplate.postForEntity(slackWebhookUrl, entity, String.class);
    }
}
