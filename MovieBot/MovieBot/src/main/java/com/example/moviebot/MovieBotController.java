package com.example.moviebot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
@RestController
public class MovieBotController {

    // Task 1: values come from application.properties (the key itself comes from an env variable)
    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // Matches the JSON we receive: {"question": "..."}
    public static class QuestionRequest {
        public String question;
    }

    // Task 2: POST /askMovieBot
    @PostMapping("/askMovieBot")
    public ResponseEntity<Map<String, String>> askMovieBot(@RequestBody QuestionRequest request) {

        // Task 3: the key is missing
        if (apiKey == null || apiKey.isBlank()) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Gemini API key is missing. Set the GEMINI_API_KEY environment variable "
                            + "(or add it to secrets.properties) and restart the app.");
        }

        if (request.question == null || request.question.isBlank()) {
            return error(HttpStatus.BAD_REQUEST,
                    "Please send JSON like: {\"question\": \"Who directed Inception?\"}");
        }

        try {
            // Build the JSON body for Gemini. Using Map + Jackson means quotes inside the
            // user's question are escaped automatically.
            Map<String, Object> body = Map.of(
                    "system_instruction", Map.of("parts", List.of(Map.of("text",
                            "You are MovieBot. Answer only questions about movies, actors and directors. "
                                    + "If the question is not about movies, politely say you can only talk about movies."))),
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", request.question)))));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            // Success: pull the answer text out of the response
            if (status == 200) {
                JsonNode root = mapper.readTree(response.body());
                String answer = root.get("candidates").get(0)
                        .get("content").get("parts").get(0)
                        .get("text").asText();
                return ResponseEntity.ok(Map.of("answer", answer));
            }

            // Task 3: handle non-200 codes from Gemini
            boolean badKey = status == 401 || status == 403
                    || (status == 400 && response.body().contains("API key"));
            if (badKey) {
                return error(HttpStatus.UNAUTHORIZED,
                        "Gemini API key is invalid or not allowed. Check the key and try again.");
            }
            if (status == 429) {
                return error(HttpStatus.TOO_MANY_REQUESTS,
                        "Gemini rate limit reached. Wait a moment and try again.");
            }
            return error(HttpStatus.BAD_GATEWAY,
                    "Gemini returned status " + status + ": " + response.body());

        } catch (Exception e) {
            return error(HttpStatus.BAD_GATEWAY, "Something went wrong while calling Gemini: " + e);
        }
    }

    // Small helper so every error looks the same: {"error": "..."}
    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
