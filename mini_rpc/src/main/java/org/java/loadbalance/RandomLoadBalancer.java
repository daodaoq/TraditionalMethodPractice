package org.java.loadbalance;

import org.java.register.ServiceMetadata;

import java.util.List;
import java.util.Random;

public class RandomLoadBalancer implements LoadBalancer {

    private final Random random = new Random();

    @Override
    public ServiceMetadata select(List<ServiceMetadata> metadataList) {
        int metadataIndex = random.nextInt(0, metadataList.size());
        return metadataList.get(Math.abs(metadataIndex));
    }
}
