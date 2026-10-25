package org.java.provider;

import org.java.api.Add;

public class AddImpl implements Add {

    @Override
    public Integer add(int a, int b) {
        return a + b;
    }
}
