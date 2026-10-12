import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ApiDemo {

    // These two objects are created once and reused by every task below.
    static HttpClient client = HttpClient.newHttpClient();   // sends HTTP requests
    static ObjectMapper mapper = new ObjectMapper();         // reads JSON text

    // A simple class that matches the joke JSON. Jackson fills in the fields for us.
    // "ignoreUnknown" means: skip any JSON fields we didn't list (like "id" and "type").
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Joke {
        public String setup;
        public String punchline;
    }

    public static void main(String[] args) throws Exception {
        task1();
        task2();
        task3();
        task4();
        task5();
    }

    // Task 1: send a GET request and print the raw JSON
    static void task1() throws Exception {
        System.out.println("--- Task 1: raw JSON ---");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.adviceslip.com/advice"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());
    }

    // Task 2: parse the JSON and print only the advice text
    static void task2() throws Exception {
        System.out.println("\n--- Task 2: advice only ---");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.adviceslip.com/advice"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // The JSON looks like: {"slip": {"id": 5, "advice": "..."}}
        JsonNode root = mapper.readTree(response.body());
        String advice = root.get("slip").get("advice").asText();

        System.out.println(advice);
    }

    // Task 3: get a random joke and print setup + punchline
    static void task3() throws Exception {
        System.out.println("\n--- Task 3: joke ---");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://official-joke-api.appspot.com/random_joke"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Joke joke = mapper.readValue(response.body(), Joke.class);

        System.out.println(joke.setup);
        System.out.println(joke.punchline);
    }

    // Task 4: the same joke code, moved into a reusable method.
    // <T> means "any type": you pass in the class you want back (Joke.class, JsonNode.class, ...).
    static <T> T fetchAndParseJson(String url, Class<T> valueType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return mapper.readValue(response.body(), valueType);
    }

    static void task4() throws Exception {
        System.out.println("\n--- Task 4: joke using fetchAndParseJson ---");

        Joke joke = fetchAndParseJson("https://official-joke-api.appspot.com/random_joke", Joke.class);

        System.out.println(joke.setup);
        System.out.println(joke.punchline);
    }

    // Task 5: ask the Gemini AI for the weather in Ahmedabad
    static void task5() throws Exception {
        System.out.println("\n--- Task 5: weather from Gemini ---");

        // Read the API key from an environment variable so it isn't stored in the code.
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null) {
            System.out.println("Please set the GEMINI_API_KEY environment variable first.");
            return;
        }

        // The question we send to Gemini. We ask for JSON so it is easy to read back.
        // (The \\\" marks are just escaped quote characters, needed because this text sits inside JSON.)
        String prompt = "What is the weather in Ahmedabad, India right now? "
                + "Reply with only JSON like: {\\\"temperature_celsius\\\": 30, \\\"description\\\": \\\"clear sky\\\"}";

        // The JSON body that Gemini expects.
        String body = """
                {"contents": [{"parts": [{"text": "%s"}]}]}
                """.formatted(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("Error " + response.statusCode() + ": " + response.body());
            return;
        }

        // Gemini's answer is hidden at: candidates -> first item -> content -> parts -> first item -> text
        JsonNode root = mapper.readTree(response.body());
        String answer = root.get("candidates").get(0)
                .get("content").get("parts").get(0)
                .get("text").asText();

        // Sometimes the model wraps its answer in ``` marks. Remove them.
        answer = answer.replace("```json", "").replace("```", "").trim();

        // The answer is itself JSON, so we parse it once more.
        JsonNode weather = mapper.readTree(answer);

        System.out.println("Temperature: " + weather.get("temperature_celsius").asText() + " °C");
        System.out.println("Weather: " + weather.get("description").asText());
        System.out.println("(Note: this is Gemini's estimate, not live weather data.)");
    }
}
