package com.nick.filemanager.websocket;

import com.nick.filemanager.common.constant.AppConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSocket endpoint for real-time file-system change notifications.
 * Uses Java WatchService to monitor directories.
 *
 * Connect: ws://localhost:8080/ws/file-monitor
 * Send: {"action":"watch","path":"/your/directory"}
 */
@ServerEndpoint(AppConstants.WS_FILE_MONITOR)
@ApplicationScoped
public class FileMonitorSocket {

    private final Map<String, WatchService> watchers = new ConcurrentHashMap<>();
    private final Map<String, Path> watchRoots = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        send(session, "{\"type\":\"connected\",\"message\":\"File monitor active. Send watch/unwatch commands.\"}");
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            var json = mapper.readValue(message, Map.class);
            String action = (String) json.get("action");
            String path = (String) json.get("path");

            if ("watch".equals(action) && path != null) {
                Path root = Path.of(path);
                if (!Files.isDirectory(root)) {
                    send(session, "{\"type\":\"error\",\"message\":\"Not a directory: " + path + "\"}");
                    return;
                }
                startWatching(session, root);
            } else if ("unwatch".equals(action)) {
                stopWatching(session);
            }
        } catch (Exception e) {
            send(session, "{\"type\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @OnClose
    public void onClose(Session session) {
        stopWatching(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        stopWatching(session);
    }

    private void startWatching(Session session, Path root) {
        String sid = session.getId();
        stopWatching(session);

        try {
            WatchService ws = FileSystems.getDefault().newWatchService();
            root.register(ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

            watchers.put(sid, ws);
            watchRoots.put(sid, root);

            send(session, "{\"type\":\"watching\",\"path\":\"" + root.toString().replace("\\", "\\\\") + "\"}");

            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "file-watcher-" + sid);
                t.setDaemon(true);
                return t;
            });
            executors.put(sid, executor);
            executor.submit(() -> {
                try {
                    while (session.isOpen()) {
                        WatchKey key = ws.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                        if (key == null) continue;
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                            @SuppressWarnings("unchecked")
                            Path changed = root.resolve((Path) event.context());
                            String type = kind.name().replace("ENTRY_", "").toLowerCase();
                            String msg = String.format(
                                "{\"type\":\"%s\",\"path\":\"%s\",\"name\":\"%s\"}",
                                type,
                                changed.toString().replace("\\", "\\\\"),
                                changed.getFileName()
                            );
                            send(session, msg);
                        }
                        if (!key.reset()) break;
                    }
                } catch (InterruptedException ignored) {
                } catch (Exception ignored) {
                }
            });

        } catch (IOException e) {
            send(session, "{\"type\":\"error\",\"message\":\"Failed to start watcher: " + e.getMessage() + "\"}");
        }
    }

    private void stopWatching(Session session) {
        String sid = session.getId();
        WatchService ws = watchers.remove(sid);
        watchRoots.remove(sid);
        ExecutorService executor = executors.remove(sid);
        if (ws != null) {
            try { ws.close(); } catch (IOException ignored) {}
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void send(Session session, String text) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendText(text);
            } catch (IOException ignored) {}
        }
    }
}
