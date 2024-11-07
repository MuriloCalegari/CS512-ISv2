package edu.duke.cs.is_v2;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.shared.SharedCount;
import org.apache.curator.framework.recipes.shared.VersionedValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class StateAccessor {

    @Autowired
    private CuratorFramework curatorFramework;

    public int getCurrentHashLength() {
        String path = "/state/hashLength";

        try {
            byte[] data = curatorFramework.getData().forPath(path);
            return Integer.parseInt(new String(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        SharedCount sharedCount = new SharedCount(curatorFramework, path, 0);

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
