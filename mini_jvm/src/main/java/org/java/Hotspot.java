package org.java;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Hotspot {

    private String mainClass;

    private List<String> classPath;

    public Hotspot(String mainClass, String classPathString) {
        this.mainClass = mainClass;
        this.classPath = Arrays.asList(classPathString.split(File.pathSeparator));
    }

    public void start() {

    }
}
