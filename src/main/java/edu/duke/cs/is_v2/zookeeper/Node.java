package edu.duke.cs.is_v2.zookeeper;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

public record Node(Integer id, String address, int clientPort, int quorumPort, int leaderElectionPort) {
    public Node(Integer id, String address) {
        this(id, address, 2181, 2888, 3888);
    }
}
