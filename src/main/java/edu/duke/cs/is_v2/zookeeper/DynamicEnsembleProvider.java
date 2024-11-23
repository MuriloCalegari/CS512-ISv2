package edu.duke.cs.is_v2.zookeeper;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
public class DynamicEnsembleProvider implements EnsembleProvider {

    private final CuratorFramework coordinatorClient;
    static final String ENSEMBLE_PATH = "/ensemble/members";
    @NonNull
    private final String coordinatorAddress;

    @Getter
    List<Node> ensembleNodes = new ArrayList<>();
    Lock lock = new ReentrantLock();

    public DynamicEnsembleProvider(@NonNull String coordinatorAddress) {
        this.coordinatorAddress = coordinatorAddress;
        log.info("Connecting to coordinator at {}", coordinatorAddress);
        this.coordinatorClient = CuratorFrameworkFactory.newClient(
                coordinatorAddress, new ExponentialBackoffRetry(1000, 3)
        );
        this.coordinatorClient.start();
        refreshConnectionString(); // Initial connection string
    }

    // Get updated connection string from the coordinator
    private void refreshConnectionString() {
        lock.lock();
        try {
            if (coordinatorClient.checkExists().forPath(ENSEMBLE_PATH) == null) {
                coordinatorClient.create().creatingParentsIfNeeded().forPath(ENSEMBLE_PATH);
            }

            List<String> ensembleNodeNames = coordinatorClient.getChildren().forPath(ENSEMBLE_PATH);
            List<Node> updatedNodes = new ArrayList<>();

            for (String nodeName : ensembleNodeNames) {
                String nodeData = new String(coordinatorClient.getData().forPath(ENSEMBLE_PATH + "/" + nodeName));
                String[] parts = nodeData.split(":");
//                Node node = new Node(parts[0], Integer.parseInt(parts[1]));
                Node node = new Node(parts[0]);
                updatedNodes.add(node);
            }

            // Add the coordinator itself to the ensemble
            // TODO dynamically retrieve ports
            updatedNodes.add(new Node(coordinatorAddress.split(":")[0], 2181, 2888, 3888));
            updatedNodes.add(new Node(coordinatorAddress.split(":")[0], 2182, 2889, 3889));

            ensembleNodes = updatedNodes;
            log.info("Updated ensemble nodes: {}", ensembleNodes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch ensemble members", e);
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            List<String> nodesAndPorts = new ArrayList<>();

            for (Node node : ensembleNodes) {
                nodesAndPorts.add(node.address() + ":" + node.clientPort());
            }

            return String.join(",", nodesAndPorts); // Combine nodes into connection string
        } finally {
            lock.unlock();
        }
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

