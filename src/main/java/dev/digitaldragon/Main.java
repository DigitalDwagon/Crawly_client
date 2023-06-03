package dev.digitaldragon;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLHandshakeException;


public class Main {
    public static final int NUM_THREADS = 100;
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36";
    public static final String CLIENT_NAME = "Crawly_prod 0.0.2";
    public static final String TRACKER = "http://localhost:443";
    public static final int MAX_URLS = 1000; //set 0 to disable
    public static final String USERNAME = "DigitalDragon";
    public static String IP;

    public static void main(String[] args) throws InterruptedException, IOException {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        int done = 0;

        IP = getIpAddress();
        if (IP == null)
            return;

        while (done < MAX_URLS || MAX_URLS == 0) {
            List<String> urls = retrieveUrlsFromQueue(USERNAME, Math.min(MAX_URLS - done, 2000));

            for (String url : urls) {
                executor.submit(() -> {
                    //try {
                        System.out.println("Trying crawl: " + url);
                        CrawlResult result = getOutlinks(url);
                    try {
                        submitToTracker(result);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    System.out.println("Submitting crawl: "  + url);
                    //} catch (Exception e) {
                    //    System.out.println("Crawl failed: " + e.getClass().getName() + " (for" + url + ")");
                    //}
                });
                done++;
            }
        }
        executor.shutdown();
    }

    public static CrawlResult getOutlinks(String url) {
        List<String> outlinks = new ArrayList<>();
        CrawlResult crawlResult = new CrawlResult();

        try {
            Connection connection = Jsoup.connect(url);
            connection.followRedirects(false);
            connection.ignoreHttpErrors(true);
            Connection.Response response = connection.execute();
            Document doc = response.parse();

            crawlResult.setStatus(response.statusCode());
            crawlResult.setCrawlUrl(url);
            crawlResult.setIp(IP);

            if (response.statusCode() == 301 || response.statusCode() == 302) {
                crawlResult.addUrl(response.header("Location"));
                return crawlResult;
            }

            // Find all anchor tags
            Elements anchorTags = doc.select("a[href]");
            for (Element anchorTag : anchorTags) {
                String href = anchorTag.attr("abs:href");
                crawlResult.addUrl(href);
            }

            // Find all image tags
            Elements imageTags = doc.select("img[src]");
            for (Element imageTag : imageTags) {
                String src = imageTag.attr("abs:src");
                crawlResult.addUrl(src);
            }

            // Find all script tags
            Elements scriptTags = doc.select("script[src]");
            for (Element scriptTag : scriptTags) {
                String src = scriptTag.attr("abs:src");
                crawlResult.addUrl(src);
            }

            // Find all link tags (CSS)
            Elements linkTags = doc.select("link[href]");
            for (Element linkTag : linkTags) {
                String href = linkTag.attr("abs:href");
                crawlResult.addUrl(href);
            }
        } catch (UnsupportedMimeTypeException e) {
            return null;
        } catch (ConnectException e) {
            crawlResult.setStatus(0);
            crawlResult.setCrawlUrl(url);
            crawlResult.setIp(IP);
        } catch (FileNotFoundException e) {
            System.out.println(e.getCause().getMessage());
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return crawlResult;
    }

    private static String getIpAddress() throws IOException {
        // Get the external IP address
        URL ipUrl = new URL("https://api.ipify.org");
        HttpURLConnection ipConnection = (HttpURLConnection) ipUrl.openConnection();
        ipConnection.setRequestMethod("GET");

        int ipStatusCode = ipConnection.getResponseCode();
        String externalIpAddress = "";
        if (ipStatusCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ipConnection.getInputStream()))) {
                externalIpAddress = reader.readLine();
                return externalIpAddress;
            }
        } else {
            System.out.println("Error occurred while retrieving external IP address. Status code: " + ipStatusCode);
        }
        return null;
    }

    private static List<String> retrieveUrlsFromQueue(String username, int maxUrlsToRetrieve) throws IOException {
        String url = String.format(TRACKER + "/queue?username=%s&amount=%d", username, maxUrlsToRetrieve);
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

    private static void submitToTracker(CrawlResult result) throws IOException {
        String apiUrl = TRACKER + "/jobs/submit?username=DigitalDragon";
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("url", result.getCrawlUrl());
        jsonBody.put("discovered", new JSONArray(result.getUrls()));
        jsonBody.put("user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Safari/537.36");
        jsonBody.put("response", result.getStatus());
        jsonBody.put("username", USERNAME);
        jsonBody.put("ip", result.getIp());
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
}
