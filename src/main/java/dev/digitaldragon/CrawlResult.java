package dev.digitaldragon;

import java.util.ArrayList;
import java.util.List;

public class CrawlResult {
    public List<String> urls = new ArrayList<>();
    public int status;
    public String crawlUrl;
    public String ip;

    public List<String> getUrls() {
        return urls;
    }

    public void addUrl(String url) {
        urls.add(url);
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCrawlUrl() {
        return crawlUrl;
    }

    public void setCrawlUrl(String url) {
        crawlUrl = url;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
