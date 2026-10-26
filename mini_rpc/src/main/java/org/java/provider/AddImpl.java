package org.java.provider;

import org.java.api.Add;
import org.java.api.User;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class AddImpl implements Add {
    @Override
    public Integer add(int a, int b) {
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        return a + b;
    }

    @Override
    public Integer minus(int a, int b) {
        return a - b;
    }

    @Override
    public User mergeAge(User user1, User user2) {
        int sumAge = user1.getAge() + user2.getAge();
        User user = new User();
        user.setAge(sumAge);
        user.setName("这是provider创建的用户");
        return user;
    }

}
