package com.nick.filemanager.service;

import com.nick.filemanager.model.entity.FileRule;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CRUD + execution for custom file operation rules.
 */
@ApplicationScoped
public class FileRuleService {

    @Inject
    PathGuard guard;

    @WithTransaction
    public Uni<List<FileRule>> listAll() {
        return FileRule.listAll().map(l -> l.stream().map(r -> (FileRule) r).toList());
    }

    @WithTransaction
    public Uni<FileRule> create(Map<String, Object> body) {
        FileRule rule = new FileRule();
        rule.name = (String) body.get("name");
        rule.pattern = (String) body.get("pattern");
        rule.actionType = (String) body.get("actionType");
        rule.rootPath = (String) body.getOrDefault("rootPath", null);
        rule.actionTarget = (String) body.getOrDefault("actionTarget", null);
        rule.enabled = (boolean) body.getOrDefault("enabled", true);
        if (rule.name == null || rule.pattern == null || rule.actionType == null) {
            return Uni.createFrom().failure(
                new IllegalArgumentException("name, pattern, and actionType are required"));
        }
        return rule.<FileRule>persist();
    }

    @WithTransaction
    public Uni<FileRule> update(Long id, Map<String, Object> body) {
        return FileRule.<FileRule>findById(id)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Rule not found: " + id))
            .flatMap(rule -> {
                if (body.containsKey("name")) rule.name = (String) body.get("name");
                if (body.containsKey("pattern")) rule.pattern = (String) body.get("pattern");
                if (body.containsKey("rootPath")) rule.rootPath = (String) body.get("rootPath");
                if (body.containsKey("actionType")) rule.actionType = (String) body.get("actionType");
                if (body.containsKey("actionTarget")) rule.actionTarget = (String) body.get("actionTarget");
                if (body.containsKey("enabled")) rule.enabled = (boolean) body.get("enabled");
                return rule.<FileRule>persist();
            });
    }

    @WithTransaction
    public Uni<Boolean> delete(Long id) {
        return FileRule.deleteById(id);
    }

    /** Execute a single rule — walks matching files (worker thread) + persists (reactive) */
    @WithTransaction
    public Uni<Map<String, Object>> execute(Long ruleId) {
        return FileRule.<FileRule>findById(ruleId)
            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Rule not found: " + ruleId))
            .flatMap(rule -> {
                Path root = rule.rootPath != null
                    ? Path.of(rule.rootPath)
                    : Path.of(System.getProperty("user.home"));
                // Path safety check
                guard.checkSafe(root.toString(), rule.actionType.toLowerCase());

                // Step 1: walk files on worker thread (I/O only, no DB)
                return Uni.createFrom().item(() -> {
                    AtomicInteger matched = new AtomicInteger(0);
                    AtomicInteger affected = new AtomicInteger(0);
                    PathMatcher matcher = FileSystems.getDefault()
                        .getPathMatcher("glob:" + rule.pattern);
                    try {
                        Files.walkFileTree(root, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (matcher.matches(file.getFileName())) {
                                    matched.incrementAndGet();
                                    try { applyAction(rule, file); affected.incrementAndGet(); }
                                    catch (IOException ignored) {}
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } catch (IOException ignored) {}
                    return new int[]{matched.get(), affected.get()};
                // Step 2: persist result via reactive chain (back on event loop)
                }).flatMap(counts -> {
                    rule.lastRunAt = LocalDateTime.now();
                    rule.filesAffected = counts[1];
                    return rule.<FileRule>persist()
                        .map(r -> Map.of("matched", counts[0], "affected", (Object) counts[1]));
                });
            });
    }

    /** Execute all enabled rules */
    public Uni<List<Map<String, Object>>> executeAll() {
        return FileRule.<FileRule>listAll()
            .flatMap(rules -> {
                List<Uni<Map<String, Object>>> executions = new ArrayList<>();
                for (FileRule r : rules) {
                    if (r.enabled) {
                        executions.add(execute(r.id));
                    }
                }
                if (executions.isEmpty()) return Uni.createFrom().item(List.of());
                return Uni.join().all(executions).andFailFast();
            });
    }

    private void applyAction(FileRule rule, Path file) throws IOException {
        switch (rule.actionType.toUpperCase()) {
            case "DELETE" -> Files.deleteIfExists(file);
            case "COPY" -> {
                if (rule.actionTarget != null) {
                    Path dst = Path.of(rule.actionTarget).resolve(file.getFileName());
                    Files.createDirectories(dst.getParent());
                    Files.copy(file, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            case "MOVE" -> {
                if (rule.actionTarget != null) {
                    Path dst = Path.of(rule.actionTarget).resolve(file.getFileName());
                    Files.createDirectories(dst.getParent());
                    Files.move(file, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
