package edu.duke.cs.is_v2.controller;

import edu.duke.cs.is_v2.UrlAccessor;
import edu.duke.cs.is_v2.UrlAccessor.UrlAttemptsPair;
import edu.duke.cs.is_v2.exception.UnusedHashNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Log4j2
public class UrlShortenerController {

    @Autowired
    private UrlAccessor urlAccessor;

    @PostMapping("/")
    public Object shortenUrl(@RequestBody String url) throws UnusedHashNotFoundException {
        try {
            return urlAccessor.generateShortened(url);
        } catch (Exception e) {
            log.error("Failed to shorten URL: {}\nFull stack trace: {}", url, e);
            throw new UnusedHashNotFoundException("Failed to shorten URL: " + url);
        }
    }
}