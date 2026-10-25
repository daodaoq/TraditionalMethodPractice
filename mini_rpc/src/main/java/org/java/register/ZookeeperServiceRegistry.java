package org.java.register;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ZookeeperServiceRegistry implements ServiceRegistry {

    private static final String BASE_PATH = "/shengsheng/rpc";

    private CuratorFramework client;

    private ServiceDiscovery<ServiceMetadata> discovery;

    @Override
    public void init(RegistryConfig config) throws Exception {
        client = CuratorFrameworkFactory.builder()
            .connectString(config.getConnectString())
            .sessionTimeoutMs(30000)
            .connectionTimeoutMs(3000)
            .retryPolicy(new ExponentialBackoffRetry(1000, 1))
            .build();
        client.start();

        discovery = ServiceDiscoveryBuilder.builder(ServiceMetadata.class)
            .client(client)
            .basePath(BASE_PATH)
            .serializer(new JsonInstanceSerializer<>(ServiceMetadata.class))
            .build();
        discovery.start();
    }

    @Override
    public void registerService(ServiceMetadata metadata) {
        try {
            ServiceInstance<ServiceMetadata> instance = ServiceInstance.<ServiceMetadata>builder()
                .address(metadata.getHost())
                .port(metadata.getPort())
                .name(metadata.getServiceName())
                .payload(metadata)
                .build();
            discovery.registerService(instance);
        } catch (Exception e) {
            log.error("{}注册失败", metadata, e);
            throw new RuntimeException(metadata + "注册失败了");
        }
    }

    @Override
    public List<ServiceMetadata> fetchServiceList(String serviceName) throws Exception {
        return discovery.queryForInstances(serviceName).stream().map(ServiceInstance::getPayload).toList();
    }
}
