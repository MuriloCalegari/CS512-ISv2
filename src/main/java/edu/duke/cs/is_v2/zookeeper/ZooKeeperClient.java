package edu.duke.cs.is_v2.zookeeper;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.curator.ensemble.EnsembleProvider;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import static edu.duke.cs.is_v2.zookeeper.DynamicEnsembleProvider.ENSEMBLE_PATH;

@Getter
@Log4j2
@Component
public class ZooKeeperClient {

    private final CuratorFramework curator;

    @Autowired
    public ZooKeeperClient(@Value("${zookeeper.coordinator_address}") String coordinatorAddress,
                           @Value("${zookeeper.server_to_manage}") String zookeeperServer) {
        // Initialize Curator with DynamicEnsembleProvider

        DynamicEnsembleProvider ensembleProvider = new DynamicEnsembleProvider(coordinatorAddress);

        this.curator = CuratorFrameworkFactory.builder()
                .ensembleProvider(ensembleProvider)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .ensembleTracker(true)
                .build();
        this.curator.start();

        // Add a watch to update the ensemble when changes are detected
        try {
            curator.getChildren().usingWatcher((Watcher) event -> {
                if (event.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                    try {
                        log.debug("Refreshing ensemble due to watch event");
                        ensembleProvider.start();

                        // Reconfigure ZooKeeper with the new ensemble
                        List<String> newEnsemble = getReconfigEnsembleString(ensembleProvider.getEnsembleNodes());
                        curator.reconfig().joining(newEnsemble).forEnsemble();
                    } catch (Exception e) {
                        log.error("Error refreshing ensemble", e);
                    }
                }
            }).forPath(ENSEMBLE_PATH);
        } catch (Exception e) {
            log.error("Error setting watch on ensemble path", e);
            throw new RuntimeException(e);
        }

        registerMember(zookeeperServer);
    }

//Specifying the client port
//A client port of a server is the port on which the server accepts client connection requests. Starting with 3.5.0 the clientPort and clientPortAddress configuration parameters should no longer be used. Instead, this information is now part of the server keyword specification, which becomes as follows:
//
//server.<positive id> = <address1>:<port1>:<port2>[:role];[<client port address>:]<client port>
//
//The client port specification is to the right of the semicolon. The client port address is optional, and if not specified it defaults to "0.0.0.0". As usual, role is also optional, it can be participant or observer (participant by default).
//
//Examples of legal server statements:
//
//server.5 = 125.23.63.23:1234:1235;1236
    private List<String> getReconfigEnsembleString(List<Node> nodes) {
        return nodes.stream()
                .map(node ->
                        "server.%d=%s:%d:%d:participant;%d".formatted(
                                node.id(),
                                node.address(),
                                node.quorumPort(),
                                node.leaderElectionPort(),
                                node.clientPort()))
                .toList();
    }

    private void registerThisMachine(String networkInterface, String port) {
        try {
            assert(networkInterface != null);

            log.info("Trying to register with network interface: {}", networkInterface);

            Enumeration<InetAddress> netInterface = Optional.of(NetworkInterface.getByName(networkInterface))
                    .map(NetworkInterface::getInetAddresses).orElseThrow(() -> new RuntimeException("Network interface not found"));

            String hostAddress = null;

            while(netInterface.hasMoreElements()) {
                InetAddress inetAddress = netInterface.nextElement();
                if(inetAddress instanceof Inet4Address) {
                    hostAddress = inetAddress.getHostAddress() + ":" + port;;
                    log.info("Registering this client with address: {}", hostAddress);
                    registerMember(hostAddress);
                }
            }

            if(hostAddress == null) {
                throw new RuntimeException("No IPv4 address found");
            }
        } catch (SocketException e) {
            log.error("Couldn't find network interface: {}", networkInterface);
            throw new RuntimeException(e);
        }
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
