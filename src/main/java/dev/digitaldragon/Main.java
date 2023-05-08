package dev.digitaldragon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLHandshakeException;


public class Main {

    public static void main(String[] args) throws InterruptedException {
        String username = "DigitalDragon";
        String user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36";
        int maxUrlsToRetrieve = 2000;
        int numThreads = 200; // set the number of threads to use
        int minTasksWaiting = 250;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        boolean runOnceAndExit = true; //do not change


        while (runOnceAndExit) {
            //runOnceAndExit = false; //comment out to run once and exit
            if (((ThreadPoolExecutor) executor).getQueue().size() < minTasksWaiting) {
                try {
                    List<String> urls = new ArrayList<>();
                    try {
                        urls = retrieveUrlsFromQueue(username, maxUrlsToRetrieve);
                        System.out.printf("Got %s urls from tracker.%n", urls.size());
                    } catch (IOException e){
                        //do nothing.
                    }
                    for (String url : urls) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            System.out.println("Skipping URL " + url + " due to unsupported protocol");
                            continue;
                        }
                        //System.out.println("Queuing URL: " + url);

                        executor.submit(() -> {
                            try {
                                // Send HTTP request to the URL
                                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                                connection.setRequestMethod("GET");
                                connection.setInstanceFollowRedirects(false); // disable automatic redirect following
                                connection.setRequestProperty("User-Agent", user_agent);

                                int responseCode = connection.getResponseCode();
                                InputStream inputStream;

                                if (responseCode >= 400) {
                                    inputStream = connection.getErrorStream();
                                } else {
                                    inputStream = connection.getInputStream();
                                }

                                // Extract discovered URLs from the response HTML
                                List<String> discoveredUrls = extractUrlsFromHtml(inputStream, url);

                                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                                    discoveredUrls.add(connection.getHeaderField("Location"));
                                }

                                // Submit finished URL and discovered URLs to the tracker
                                submitToTracker(url, discoveredUrls, responseCode, username);

                            } catch (IOException e) {
                                System.out.printf("Item failure due to %s (on %s)%n", e.getClass(), url);
                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /*try {
            // Retrieve URLs from the queue

            System.out.println(String.format("Processing %s URLs as %s", urls.size(), username));

            // Create a fixed thread pool to execute tasks concurrently
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            // Process each URL using a separate thread
            for (String url : urls) {

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    System.out.println("Skipping URL " + url + " due to unsupported protocol");
                    continue;
                }
                System.out.println("Processing URL: " + url);

                // Submit a task to the executor to process the URL
                executor.submit(() -> {
                    try {
                        // Send HTTP request to the URL
                        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setRequestMethod("GET");
                        connection.setInstanceFollowRedirects(false); // disable automatic redirect following
                        connection.setRequestProperty("User-Agent", user_agent);

                        int responseCode = connection.getResponseCode();
                        InputStream inputStream;

                        if (responseCode >= 400) {
                            inputStream = connection.getErrorStream();
                        } else {
                            inputStream = connection.getInputStream();
                        }

                        // Extract discovered URLs from the response HTML
                        List<String> discoveredUrls = extractUrlsFromHtml(inputStream, url);

                        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                            discoveredUrls.add(connection.getHeaderField("Location"));
                        }

                        // Submit finished URL and discovered URLs to the tracker
                        submitToTracker(url, discoveredUrls, responseCode, username);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            // Shutdown the executor and wait for all tasks to complete
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }*/
    }

    private static List<String> retrieveUrlsFromQueue(String username, int maxUrlsToRetrieve) throws IOException {
        String url = String.format("http://localhost:1234/queue?username=%s&amount=%d", username, maxUrlsToRetrieve);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        String responseJson = readResponse(connection);
        JSONObject jsonResponse = new JSONObject(responseJson);
        JSONArray jsonUrls = jsonResponse.getJSONArray("urls");
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < jsonUrls.length(); i++) {
            urls.add(jsonUrls.getString(i));
        }
        return urls;
    }

    private static List<String> extractUrlsFromHtml(InputStream inputStream, String baseUrl) throws IOException {
        List<String> urls = new ArrayList<>();
        StringBuilder htmlBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            htmlBuilder.append(line);
        }
        Document doc = Jsoup.parse(htmlBuilder.toString(), baseUrl);
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String absUrl = link.absUrl("href");
            if (!absUrl.isEmpty()) {
                urls.add(absUrl);
            }
        }
        Elements media = doc.select("[src]");
        for (Element src : media) {
            String absUrl = src.absUrl("src");
            if (!absUrl.isEmpty()) {
                urls.add(absUrl);
            }
        }
        Elements imports = doc.select("link[href]");
        for (Element link : imports) {
            String absUrl = link.absUrl("href");
            if (!absUrl.isEmpty()) {
                urls.add(absUrl);
            }
        }
        return urls;
    }

    private static void submitToTracker(String url, List<String> discoveredUrls, int responseCode, String username) throws IOException {
        String apiUrl = "http://localhost:1234/submit?username=DigitalDragon";
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("url", url);
        jsonBody.put("discovered", new JSONArray(discoveredUrls));
        jsonBody.put("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36");
        jsonBody.put("response", responseCode);
        jsonBody.put("username", username);
        String requestBody = jsonBody.toString();
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", String.valueOf(requestBody.length()));
        connection.setDoOutput(true);
        connection.getOutputStream().write(requestBody.getBytes());
        String responseJson = readResponse(connection);
        //System.out.println("Submitted URL: " + url);
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        Scanner scanner = new Scanner(connection.getInputStream());
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    private static List<String> extractUrlsFromLine(String line) {
        List<String> urls = new ArrayList<>();
        Document doc = Jsoup.parse(line);
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String url = link.attr("abs:href");
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        return urls;
    }
}
