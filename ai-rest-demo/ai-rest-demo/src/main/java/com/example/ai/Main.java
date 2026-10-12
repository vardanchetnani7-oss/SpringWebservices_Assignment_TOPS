package com.example.ai;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/*
 * Before running, set your free key from https://aistudio.google.com/apikey :
 *     Windows (cmd):   set GEMINI_API_KEY=your-key
 *     Mac / Linux:     export GEMINI_API_KEY=your-key
 * Then run:  mvn compile exec:java
 */
public class Main {

    // ---------- Task 2 + Task 3: send a prompt, print the answer, handle errors ----------
    static String askGemini(String model, String prompt) throws Exception {

        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            return "Error: GEMINI_API_KEY is not set. Set it in this same terminal, then run again.";
        }

        // 1. Build the JSON body:  {"contents":[{"parts":[{"text":"<prompt>"}]}]}
        //    gson.toJson(prompt) adds the quotes and escapes special characters for us.
        String json = "{\"contents\":[{\"parts\":[{\"text\":" + new Gson().toJson(prompt) + "}]}]}";
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));

        // 2. Build the POST request
        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(body)
                .build();

        // 3. Send it and read the reply
        //    The default timeout is only 10 seconds, so we allow 60 for slow AI replies.
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .protocols(java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1))
                .build();
        Response response = client.newCall(request).execute();
        String reply = response.body().string();

        // 4. Task 3: if the status code is not 200, print a custom message instead
        int code = response.code();
        if (code != 200) {
            if (code == 400 || code == 401 || code == 403) {
                return "Error " + code + ": invalid API key or bad request. Check GEMINI_API_KEY.";
            } else if (code == 429) {
                return "Error 429: rate limit exceeded. Wait a minute and try again.";
            } else {
                return "Error " + code + ": something went wrong.\n" + reply;
            }
        }

        // 5. Dig the generated text out of the JSON reply
        return JsonParser.parseString(reply).getAsJsonObject()
                .getAsJsonArray("candidates").get(0).getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts").get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    public static void main(String[] args) throws Exception {

        // Task 2 + 3
        System.out.println("--- Task 2/3: Instagram captions ---");
        System.out.println(askGemini("gemini-3.5-flash",
                "Suggest 3 creative Instagram captions for a food photo"));

        // Task 4: same idea, but a different (lighter) model and a different prompt
        System.out.println("\n--- Task 4: second model ---");
        System.out.println(askGemini("gemini-3.5-flash-lite",
                "Write a two-sentence story about a robot learning to cook."));

        // Task 5: see the AI log at the bottom of this file
        System.out.println("\n--- Task 5: AI-assisted prompt ---");
        System.out.println(askGemini("gemini-3.5-flash",
                "Suggest a name for a pizza food truck and a one-line slogan."));
    }
}
