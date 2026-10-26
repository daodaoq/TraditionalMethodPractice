package org.java.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class HeartbeatResponse implements Serializable {
    private final long requestTime;
}
