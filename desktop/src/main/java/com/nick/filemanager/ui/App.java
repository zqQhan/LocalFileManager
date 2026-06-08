package com.nick.filemanager.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * File Manager Desktop — JavaFX entry point.
 * Launches the main window that connects to the Quarkus backend.
 */
public class App extends Application {

    private static final String BACKEND_URL = "http://localhost:8080";

    @Override
    public void start(Stage primaryStage) {
        MainWindow mainWindow = new MainWindow(BACKEND_URL);

        Scene scene = new Scene(mainWindow.createContent(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setTitle("File Manager — 本地文件搜索与管理");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        // Connect WebSocket on close
        primaryStage.setOnCloseRequest(e -> mainWindow.shutdown());

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
