package edu.duke.cs.is_v2;

import com.google.common.cache.CacheBuilder;
import edu.duke.cs.is_v2.zookeeper.ZooKeeperClient;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.google.common.cache.Cache;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Log4j2
public class StateAccessor {

    public static final String HASH_LENGTH = "/state/hashLength";
    private final ZooKeeperClient zkClient;

    // Used mainly for the hashLength value
    private final Cache<String, Integer> cache;

    public static int LIMIT = 10;

    @Autowired
    public StateAccessor(ZooKeeperClient zkClient) {
        this.zkClient = zkClient;

        initialize();

        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build();

        log.debug("StateAccessor initialized");
        new Thread(this::hashLengthIncrementerHandler).start();
        new Thread(this::accumulateIncrements).start();
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
            cache.invalidate(HASH_LENGTH);
        }

        try {
            return cache.get(HASH_LENGTH, () -> {
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
                    double probOfFindingAnUnusedHash = probabilityFindingUnusedHash();

                    if (probOfFindingAnUnusedHash < COLLISION_PROBABILITY_THRESHOLD) {
                        log.info("Incrementing hash length to from {} to {}", getCurrentHashLength(), getCurrentHashLength() + 1);
                        incrementHashLength();
                    } else {
                        log.debug("Probability was still at {} which is above the threshold of {}", probOfFindingAnUnusedHash, COLLISION_PROBABILITY_THRESHOLD);
                    }
                }

                Thread.sleep(Duration.ofSeconds(1));
                leaderLatch.await();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private double probabilityFindingUnusedHash() {
        int currentCount = getCountForLength(getCurrentHashLength());

        double totalPossibleHashes = Math.pow(LETTER_OR_DIGIT_COUNT, getCurrentHashLength());
        double collisionProbabilityForOneAttempt = currentCount / totalPossibleHashes;

        // Compute the probability of not finding a collision over LIMIT attempts
        return 1 - Math.pow(collisionProbabilityForOneAttempt, LIMIT);
    }

    private void incrementHashLength() {
        incrementSync(HASH_LENGTH);
        cache.invalidate(HASH_LENGTH);
    }

    public void incrementCountForLength(int n) {
        incrementAsync(countPath(n));
    }

    private static String countPath(int n) {
        return "/state/count/" + n;
    }

    private int getCountForLength(int n) {
        String path = countPath(n);
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

    private final Lock counterLock = new ReentrantLock(true);
    private final ConcurrentHashMap<String, Long> localCounters = new ConcurrentHashMap<>();

    private boolean incrementSync(String path) {
        return addSync(path, 1L);
    }

    private boolean addSync(String path, Long value) {
        DistributedAtomicLong atomicLong = new DistributedAtomicLong(
                zkClient.getCurator(),
                path,
                zkClient.getCurator().getZookeeperClient().getRetryPolicy());

        boolean updatePending = true;
        while (updatePending) {
            try {
                AtomicValue<Long> result = atomicLong.add(value);
                updatePending = !result.succeeded();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private boolean incrementAsync(String path) {
        counterLock.lock();
        try {
            localCounters.put(path, localCounters.getOrDefault(path, 0L) + 1);
        } finally {
            counterLock.unlock();
        }
        return true;
    }

    private void accumulateIncrements() {
        while (true) {
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            Map<String, Long> mapCopy;

            try {
                counterLock.lock();
                mapCopy = new HashMap<>(localCounters);
                localCounters.clear();
            } finally {
                counterLock.unlock();
            }

            if(mapCopy.isEmpty()) {
                continue;
            }

            mapCopy.forEach((path, incrementsToApply) -> {
                if (incrementsToApply > 0) {
                    addSync(path, incrementsToApply);
                }
            });
        }
    }

}
