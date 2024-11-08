package edu.duke.cs.is_v2;

import com.google.common.cache.CacheBuilder;
import edu.duke.cs.is_v2.zookeeper.ZooKeeperClient;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.framework.recipes.shared.VersionedValue;
import org.apache.zookeeper.ZooKeeper;
import org.springframework.stereotype.Component;
import com.google.common.cache.Cache;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Log4j2
public class StateAccessor {

    public static final String HASH_LENGTH = "/state/hashLength";
    private final ZooKeeperClient zkClient;

    private final Cache<String, Integer> hashLengthCache;

    public static int LIMIT = 10;

    public StateAccessor(ZooKeeperClient zkClient) {
        this.zkClient = zkClient;

        initialize();

        this.hashLengthCache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build();

        log.debug("StateAccessor initialized");
        new Thread(this::hashLengthIncrementerHandler).start();
    }

    private void initialize() {
        try {
            DistributedAtomicLong atomicLong = new DistributedAtomicLong(
                    zkClient.getCurator(),
                    HASH_LENGTH,
                    zkClient.getCurator().getZookeeperClient().getRetryPolicy());

            atomicLong.initialize(1L);
        } catch (Exception e) {
            log.debug("Hash length already exists");
        }
    }

    public int getCurrentHashLength() {
        return getCurrentHashLength(false);
    }

    public int getCurrentHashLength(boolean forceRefresh) {

        if (forceRefresh) {
            hashLengthCache.invalidate(HASH_LENGTH);
        }

        try {
            return hashLengthCache.get(HASH_LENGTH, () -> {
                DistributedAtomicLong atomicLong = new DistributedAtomicLong(
                        zkClient.getCurator(),
                        HASH_LENGTH,
                        zkClient.getCurator().getZookeeperClient().getRetryPolicy());

                try {
                    AtomicValue<Long> result = atomicLong.get();
                    if (result.succeeded()) {
                        return result.postValue().intValue();
                    } else {
                        throw new RuntimeException("Failed to get the current hash length");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    int LETTER_OR_DIGIT_COUNT = 26 + 26 + 10;

    // The minimum probability that we will be able to find an unused hash
    // after LIMIT attempts
    float COLLISION_PROBABILITY_THRESHOLD = 0.9f;
    private void hashLengthIncrementerHandler() {
        log.debug("Starting hash length incrementer handler");
        LeaderLatch leaderLatch = new LeaderLatch(zkClient.getCurator(), "/state/leaderLatch");
        try {
            leaderLatch.start();

            while (true) {
                if (leaderLatch.hasLeadership()) {
                    log.debug("We're the leader now");
                    int currentHashLength = getCurrentHashLength();
                    int currentCount = getCountForLength(currentHashLength);

                    double totalPossibleHashes = Math.pow(LETTER_OR_DIGIT_COUNT, currentHashLength);
                    double collisionProbabilityForOneAttempt = currentCount / totalPossibleHashes;

                    // Compute the probability of not finding a collision over LIMIT attempts
                    double probOfFindingAnUnusedHash = 1 - Math.pow(collisionProbabilityForOneAttempt, LIMIT);

                    if (probOfFindingAnUnusedHash < COLLISION_PROBABILITY_THRESHOLD) {
                        log.info("Incrementing hash length to from {} to {}", currentHashLength, currentHashLength + 1);
                        incrementHashLength();
                    } else {
                        log.info("Probability was still at {} which is above the threshold of {}", probOfFindingAnUnusedHash, COLLISION_PROBABILITY_THRESHOLD);
                    }
                }

                Thread.sleep(Duration.ofSeconds(1));
                leaderLatch.await();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean incrementHashLength() {
        String path = "/state/hashLength";

        boolean increment = increment(path);

        hashLengthCache.invalidate(path);

        return increment;
    }

    public void incrementCountForLength(int n) {
        String path = "/state/count/" + n;

        increment(path);
    }

    private int getCountForLength(int n) {
        String path = "/state/count/" + n;
        DistributedAtomicLong atomicLong = new DistributedAtomicLong(
                zkClient.getCurator(),
                path,
                zkClient.getCurator().getZookeeperClient().getRetryPolicy());

        try {
            AtomicValue<Long> result = atomicLong.get();
            if (result.succeeded()) {
                return result.postValue().intValue();
            } else {
                throw new RuntimeException("Failed to get the count for length: " + n);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean increment(String path) {
        // TODO run on a separate thread
        DistributedAtomicLong atomicLong = new DistributedAtomicLong(
                zkClient.getCurator(),
                path,
                zkClient.getCurator().getZookeeperClient().getRetryPolicy());

        boolean updatePending = true;

        while (updatePending) {
            try {
                AtomicValue<Long> result = atomicLong.increment();
                updatePending = !result.succeeded();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

}
