package edu.duke.cs.is_v2.zookeeper;

import lombok.extern.log4j.Log4j2;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Log4j2
public class DynamicEnsembleProvider implements EnsembleProvider {

    private final CuratorFramework coordinatorClient;
    static final String ENSEMBLE_PATH = "/ensemble/members";
    private String connectionString;

    public DynamicEnsembleProvider(String coordinatorAddress) {
        log.info("Connecting to coordinator at {}", coordinatorAddress);
        this.coordinatorClient = CuratorFrameworkFactory.newClient(
                coordinatorAddress, new ExponentialBackoffRetry(1000, 3)
        );
        this.coordinatorClient.start();
        refreshConnectionString(); // Initial connection string
    }

    // Get updated connection string from the coordinator
    private void refreshConnectionString() {
        try {

            if(coordinatorClient.checkExists().forPath(ENSEMBLE_PATH) == null) {
                coordinatorClient.create().creatingParentsIfNeeded().forPath(ENSEMBLE_PATH);
            }

            List<String> ensembleNodes = coordinatorClient.getChildren().forPath(ENSEMBLE_PATH);
            connectionString = String.join(",", ensembleNodes); // Combine nodes into connection string
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch ensemble members", e);
        }
    }

    @Override
    public void start() {
        refreshConnectionString();  // Initial fetch when starting
    }

    @Override
    public void close() {
        coordinatorClient.close();
    }

    @Override
    public String getConnectionString() {
        refreshConnectionString();  // Fetch updated connection string
        return connectionString;
    }

    @Override
    public boolean updateServerListEnabled() {
        return true;
    }

    @Override
    public void setConnectionString(String connectionString) {
        // No-op as connectionString is managed dynamically
    }
}

