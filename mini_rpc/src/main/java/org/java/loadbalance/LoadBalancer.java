package org.java.loadbalance;

import org.java.register.ServiceMetadata;

import java.util.List;

public interface LoadBalancer {
    
    ServiceMetadata select(List<ServiceMetadata> metadataList);
}
