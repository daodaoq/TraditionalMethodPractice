package org.java.register;

import lombok.Data;

@Data
public class RegistryConfig {
    private String registerType = "zookeeper";
    private String connectString;
}
