package com.nick.filemanager;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * File Manager backend — Quarkus entry point.
 * Start with: {@code mvn quarkus:dev}  (from the backend/ directory)
 */
@QuarkusMain
public class Application {

    public static void main(String... args) {
        Quarkus.run(args);
    }
}
