package com.nick.filemanager.websocket;

import com.nick.filemanager.common.constant.AppConstants;
import com.nick.filemanager.model.entity.BatchTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket endpoint for real-time batch task progress updates.
 * Clients subscribe to a specific taskId to get progress pushed every 2 seconds.
 *
 * Connect: ws://localhost:8080/ws/batch-progress/{taskId}
 */
@ServerEndpoint(AppConstants.WS_BATCH_PROGRESS + "/{taskId}")
@ApplicationScoped
public class BatchProgressSocket {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("taskId") String taskId) {
        send(session, "{\"type\":\"subscribed\",\"taskId\":" + taskId + "}");

        var future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!session.isOpen()) {
                    cancel(session.getId());
                    return;
                }
                long tid = Long.parseLong(taskId);
                BatchTask.<BatchTask>findById(tid)
                    .subscribe().with(task -> {
                        if (task == null) return;
                        String json = String.format(
                            "{\"type\":\"progress\",\"taskId\":%d,\"status\":\"%s\",\"total\":%d,\"processed\":%d,\"failed\":%d}",
                            task.id, task.status, task.totalCount, task.processedCount, task.failedCount);
                        send(session, json);

                        if ("COMPLETED".equals(task.status) || "FAILED".equals(task.status)) {
                            send(session, "{\"type\":\"completed\",\"taskId\":" + task.id + "}");
                            cancel(session.getId());
                        }
                    });
            } catch (Exception e) {
                cancel(session.getId());
            }
        }, 0, 2, TimeUnit.SECONDS);

        futures.put(session.getId(), future);
    }

    @OnClose
    public void onClose(Session session) {
        cancel(session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        cancel(session.getId());
    }

    private void cancel(String sessionId) {
        var future = futures.remove(sessionId);
        if (future != null) {
            future.cancel(false);
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
