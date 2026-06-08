package com.nick.filemanager.ui.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * WebSocket client for real-time file monitoring and batch progress.
 * Uses java.net.http.WebSocket (Java 11+ built-in).
 */
public class WSClient {

    private final String baseUrl;
    private final Consumer<String> onEvent;
    private WebSocket webSocket;

    public WSClient(String baseUrl, Consumer<String> onEvent) {
        // Convert http:// to ws://
        this.baseUrl = baseUrl.replace("http://", "ws://");
        this.onEvent = onEvent;
    }

    /** Connect to the file-monitor WebSocket endpoint */
    public void connect() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            WebSocket.Builder builder = client.newWebSocketBuilder();
            builder.buildAsync(
                URI.create(baseUrl + "/ws/file-monitor"),
                new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket ws) {
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        if (onEvent != null) {
                            onEvent.accept(data.toString());
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        System.err.println("[WS] Error: " + error.getMessage());
                    }
                }
            ).thenAccept(ws -> this.webSocket = ws);
        } catch (Exception e) {
            System.err.println("[WS] Connection failed: " + e.getMessage());
        }
    }

    /** Start watching a directory for changes */
    public void watchDirectory(String path) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            String msg = String.format("{\"action\":\"watch\",\"path\":\"%s\"}",
                path.replace("\\", "\\\\"));
            webSocket.sendText(msg, true);
        }
    }

    /** Disconnect WebSocket */
    public void disconnect() {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }
}
