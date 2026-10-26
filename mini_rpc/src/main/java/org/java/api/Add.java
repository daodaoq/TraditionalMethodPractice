package org.java.api;

import org.java.fallback.RpcFallback;

@RpcFallback(ConsumerAddImpl.class)
public interface Add {

    Integer add(int a, int b);

    Integer minus(int a, int b);
    
    User mergeAge(User user1,User user2);
}
