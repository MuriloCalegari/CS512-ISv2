package edu.duke.cs.is_v2;

import com.google.common.hash.Hashing;
import edu.duke.cs.is_v2.exception.UnusedHashNotFoundException;
import edu.duke.cs.is_v2.zookeeper.ZooKeeperClient;
import lombok.extern.log4j.Log4j2;
import org.apache.zookeeper.KeeperException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.RandomStringUtils;

import static edu.duke.cs.is_v2.StateAccessor.LIMIT;

@Log4j2
@Component
public class UrlAccessor {

    @Autowired
    private ZooKeeperClient zkClient;

    @Autowired
    private StateAccessor stateAccessor;

    public record UrlAttemptsPair(String url, int attempts) {}

    public UrlAttemptsPair generateShortened(String url) throws UnusedHashNotFoundException {

        int n = 0;

        while (n < LIMIT * 10) {
            int length = stateAccessor.getCurrentHashLength();
            String shortenedUrl = hash(url, (int) (Math.random() * Integer.MAX_VALUE), length);

            if (atomicCheckAndPersist(shortenedUrl, url)) {
                stateAccessor.incrementCountForLength(length);
                log.debug("Generated URL after {} attempts: {}", n + 1, shortenedUrl);
                return new UrlAttemptsPair(shortenedUrl, n + 1);
            }

            if(n >= 5 * LIMIT) {
                if(n == 5 * LIMIT) {
                    log.warn("We haven't been able to generate a unique shortened URL for {} after {} attempts. So we're adding some jitter", url, n);
                }
                // Backoff from 10ms to 1000ms, linearly to n, plus some jitter
                try {
                    Thread.sleep(20 * (n - 5L * LIMIT) + (int) (Math.random() * 50));
                } catch (InterruptedException e) {
                    log.error("Thread interrupted", e);
                }
            }

            n++;
        }

        log.error("Failed to generate a unique shortened URL for {}", url);

        throw new UnusedHashNotFoundException(
                "Failed to generate a unique shortened URL for %s after %d attempts".formatted(url, LIMIT)
        );
    }

    private boolean atomicCheckAndPersist(String shortenedUrl, String originalUrl) {
        try {
            String path = "/urls/" + shortenedUrl;
            zkClient.getCurator()
                    .create()
                    .creatingParentsIfNeeded()
                    .forPath(path, originalUrl.getBytes());

            return true;
        } catch (KeeperException.NodeExistsException e) {
            return false;
        } catch (RuntimeException e) {
            log.error("Error accessing ZooKeeper", e);
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getOriginalUrl(String shortenedUrl) {
        try {
            String path = "/urls/" + shortenedUrl;
            byte[] data = zkClient.getCurator().getData().forPath(path);
            return new String(data);
        } catch (KeeperException.NoNodeException e) {
            log.warn("Shortened URL not found in ZooKeeper: {}", shortenedUrl);
            return null;
        } catch (Exception e) {
            log.error("Error accessing ZooKeeper for {}: {}", shortenedUrl, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String hash(String url, int n, int length) {
        return RandomStringUtils.randomAlphanumeric(length);
    }
}
