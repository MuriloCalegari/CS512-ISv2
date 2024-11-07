package edu.duke.cs.is_v2.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UrlShortenerController {

    @PostMapping("/")
    public String shortenUrl(@RequestBody String url) {
        // TODO: Implement URL shortening logic
        return "shortenedUrl";
    }
}