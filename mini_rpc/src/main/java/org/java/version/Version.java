package org.java.version;

public enum Version {
    V1(0);

    private final int versionNum;

    Version(int versionNum) {
        this.versionNum = versionNum;
    }

    public int getVersionNum() {
        return versionNum;
    }
}
