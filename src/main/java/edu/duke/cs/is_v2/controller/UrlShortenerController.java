package edu.duke.cs.is_v2.controller;

import edu.duke.cs.is_v2.UrlAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UrlShortenerController {

    @Autowired
    private UrlAccessor urlAccessor;

    @PostMapping("/")
    public String shortenUrl(@RequestBody String url) {
        try {
            return urlAccessor.generateShortened(url);
        } catch (Exception e) {
            return "Failed to shorten URL: " + e.getMessage();
        }
    }
}