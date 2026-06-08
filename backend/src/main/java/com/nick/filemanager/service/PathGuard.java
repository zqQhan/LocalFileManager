package com.nick.filemanager.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Path safety guard — prevents accidental operations on protected directories.
 *
 * Rules (checked in order):
 * 1. BLACKLIST — paths under these prefixes are always rejected (system dirs, etc.)
 * 2. WHITELIST — if configured, ONLY paths under these prefixes are allowed
 * 3. If neither list is configured, all paths are allowed (dev default)
 */
@ApplicationScoped
public class PathGuard {

    // Directories that are NEVER allowed for modification
    private static final Set<String> DEFAULT_BLACKLIST = Set.of(
        "C:\\Windows", "C:\\Windows\\System32",
        "C:\\Program Files", "C:\\Program Files (x86)",
        "/usr", "/bin", "/sbin", "/etc", "/boot", "/lib", "/lib64",
        "/System", "/Library"
    );

    @ConfigProperty(name = "filemanager.guard.whitelist")
    java.util.Optional<List<String>> whitelistProp;

    @ConfigProperty(name = "filemanager.guard.blacklist")
    java.util.Optional<List<String>> blacklistProp;

    /**
     * Check if a path is safe for the given operation.
     * Throws SecurityException if the path is protected.
     *
     * @param path      absolute normalized path to check
     * @param operation "read", "write", or "delete" (controls strictness)
     */
    public void checkSafe(String path, String operation) {
        Path p = Path.of(path).toAbsolutePath().normalize();
        String normalized = p.toString();

        // 1. Check blacklist — these paths are NEVER allowed for write/delete
        if (!"read".equals(operation)) {
            List<String> blacklist = blacklistProp.orElse(List.of());
            for (String prefix : DEFAULT_BLACKLIST) {
                if (matchesPrefix(normalized, prefix)) {
                    throw new SecurityException(
                        "Operation '" + operation + "' blocked: " + normalized
                        + " is in a protected system directory");
                }
            }
            for (String prefix : blacklist) {
                if (matchesPrefix(normalized, prefix)) {
                    throw new SecurityException(
                        "Operation '" + operation + "' blocked: " + normalized
                        + " matches blocked prefix '" + prefix + "'");
                }
            }
        }

        // 2. Check whitelist — if configured, ONLY whitelisted paths are allowed
        if (whitelistProp.isPresent()) {
            List<String> whitelist = whitelistProp.get();
            boolean allowed = whitelist.stream().anyMatch(w -> matchesPrefix(normalized, w));
            if (!allowed) {
                throw new SecurityException(
                    "Operation '" + operation + "' blocked: " + normalized
                    + " is not in the allowed path whitelist");
            }
        }
    }

    /** Match path prefix at directory boundary (e.g. C:\Windows matches C:\Windows\System32 but not C:\WindowsApp) */
    private boolean matchesPrefix(String path, String prefix) {
        if (!path.startsWith(prefix)) return false;
        if (path.length() == prefix.length()) return true;                    // exact match
        char next = path.charAt(prefix.length());
        return next == '\\' || next == '/';                                   // boundary character
    }
}
