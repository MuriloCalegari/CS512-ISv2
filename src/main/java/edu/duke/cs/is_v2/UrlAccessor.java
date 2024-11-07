package edu.duke.cs.is_v2;

import com.google.common.hash.Hashing;
import edu.duke.cs.is_v2.exception.UnusedHashNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Log4j2
@Component
public class UrlAccessor {

    int LIMIT = 10;

    @Autowired
    private CuratorFramework curatorFramework;

    @Autowired
    private StateAccessor stateAccessor;

    public String generateShortened(String url) throws UnusedHashNotFoundException {

        int n = 0;

        while (n < LIMIT) {
            String shortenedUrl = hash(url, n, stateAccessor.getCurrentHashLength());
            if (atomicCheckAndPersist(shortenedUrl, url)) {
                stateAccessor.incrementCountForLength(shortenedUrl.length());
                return shortenedUrl;
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
            curatorFramework
                    .create()
                    .creatingParentsIfNeeded()
                    .withProtection()
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
            byte[] data = curatorFramework.getData().forPath(path);
            return new String(data);
        } catch (KeeperException.NoNodeException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String hash(String url, int n, int length) {
        String input = url + n;

        String sha256hex = Hashing.sha256()
                .hashString(input, StandardCharsets.UTF_8)
                .toString();

        return sha256hex.substring(0, length);
    }
}
