package org.java.compress;

import org.java.spi.Extension;

public interface Compression extends Extension {

    byte[] compress(byte[] bytes);

    byte[] decompress(byte[] bytes);


}
