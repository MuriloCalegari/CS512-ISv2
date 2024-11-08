package edu.duke.cs.is_v2.zookeeper;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.springframework.stereotype.Component;

import static edu.duke.cs.is_v2.zookeeper.DynamicEnsembleProvider.ENSEMBLE_PATH;

@Getter
@Log4j2
public class ZooKeeperClient {

    private final CuratorFramework curator;

    public ZooKeeperClient(String coordinatorAddress) {
        // Initialize Curator with DynamicEnsembleProvider

        EnsembleProvider ensembleProvider = new DynamicEnsembleProvider(coordinatorAddress);

        this.curator = CuratorFrameworkFactory.builder()
                .ensembleProvider(ensembleProvider)
                .connectString(coordinatorAddress)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        this.curator.start();

        // Todo dynamically retrieve
        registerMember("localhost:2181");

        // Start a thread that updates the ensemble every 30 seconds
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30000);
                    log.debug("Refreshing ensemble");
                    ensembleProvider.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void registerMember(String memberAddress) {
        String memberId = "server-" + System.currentTimeMillis();  // Unique ID for this member
        try {

            log.info("Registering member with ID: {} and address: {}", memberId, memberAddress);
            curator.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(ENSEMBLE_PATH + "/" + memberId, memberAddress.getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
