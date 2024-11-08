package edu.duke.cs.is_v2;

import edu.duke.cs.is_v2.zookeeper.ZooKeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.framework.recipes.shared.VersionedValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class StateAccessor {

    @Autowired
    private ZooKeeperClient zkClient;

    // Set up constructor
    public StateAccessor(ZooKeeperClient zkClient) {
        this.zkClient = zkClient;

//        initializeNodes();
    }

    private void initializeNodes() {
        String path = "/state/hashLength";
        String path2 = "/state/count/" + 1;
        try {
            if(zkClient.getCurator().checkExists().forPath(path) == null) {
                zkClient.getCurator().create().creatingParentsIfNeeded().forPath(path, "1".getBytes());
            }
            if(zkClient.getCurator().checkExists().forPath(path2) == null) {
                zkClient.getCurator().create().creatingParentsIfNeeded().forPath(path2, "0".getBytes());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getCurrentHashLength() {
        String path = "/state/hashLength";

        SharedCount sharedCount = new SharedCount(zkClient.getCurator(), path, 1);

        try {
            sharedCount.start();
            return sharedCount.getCount();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                sharedCount.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean incrementHashLength() {
        String path = "/state/hashLength";

        return increment(path);
    }

    public void incrementCountForLength(int n) {
        String path = "/state/count/" + n;

        increment(path);
    }

    private boolean increment(String path) {
        // TODO run on separate, detached thread
        SharedCount sharedCount = new SharedCount(zkClient.getCurator(), path, 0);

        try {
            sharedCount.start();
            VersionedValue<Integer> versionedValue = sharedCount.getVersionedValue();
            sharedCount.trySetCount(versionedValue, versionedValue.getValue() + 1);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                sharedCount.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
