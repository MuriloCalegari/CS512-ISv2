package edu.duke.cs.is_v2.controller;

import edu.duke.cs.is_v2.UrlAccessor;
import edu.duke.cs.is_v2.exception.UnusedHashNotFoundException;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@Log4j2
public class UrlShortenerController {

    @Autowired
    private UrlAccessor urlAccessor;

    @Data
    public static class UrlRequest {
        private String url;
    }

    @PostMapping("/")
    public Object shortenUrl(@RequestBody UrlRequest urlRequest) throws UnusedHashNotFoundException {
        String url = urlRequest.getUrl();
        try {
            return urlAccessor.generateShortened(url);
        } catch (Exception e) {
            log.error("Failed to shorten URL: {}\nFull stack trace: {}", url, e);
            throw new UnusedHashNotFoundException("Failed to shorten URL: " + url);
        }
    }

    @GetMapping("/{shortenedUrl}")
    public ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String shortenedUrl) {
        try {
            String originalUrl = urlAccessor.getOriginalUrl(shortenedUrl);
            if (originalUrl != null) {
                return ResponseEntity.status(302).location(URI.create(originalUrl)).build();
            } else {
                log.warn("Shortened URL not found: {}", shortenedUrl);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error retrieving original URL for {}: {}", shortenedUrl, e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
}
