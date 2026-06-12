package com.nick.filemanager.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * File Manager Desktop — JavaFX entry point.
 * Launches the main window that connects to the Quarkus backend.
 */
public class App extends Application {

    private static final String DEFAULT_BACKEND_URL = "http://localhost:8080";

    @Override
    public void start(Stage primaryStage) {
        MainWindow mainWindow = new MainWindow(resolveBackendUrl());

        Scene scene = new Scene(mainWindow.createContent(), 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setTitle("File Manager — 本地文件搜索与管理");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1180);
        primaryStage.setMinHeight(720);

        // Connect WebSocket on close
        primaryStage.setOnCloseRequest(e -> mainWindow.shutdown());

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static String resolveBackendUrl() {
        String systemProperty = System.getProperty("filemanager.backend.url");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        String environment = System.getenv("FILEMANAGER_BACKEND_URL");
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        return DEFAULT_BACKEND_URL;
    }
}
