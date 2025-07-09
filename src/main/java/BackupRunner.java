import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BackupRunner {
    public static void runBackup(String sourceDirPath, String backupDirPath, String logFilePath, Consumer<String> logCallback) throws Exception {
        Path sourceDir = Paths.get(sourceDirPath);
        Path backupDir = Paths.get(backupDirPath);
        Path manifestPath = backupDir.resolve("manifest.json");
        Path logFile = Paths.get(logFilePath);

        Files.createDirectories(backupDir);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> manifest;

        if (Files.exists(manifestPath)) {
            manifest = mapper.readValue(Files.newBufferedReader(manifestPath), Map.class);
        } else {
            manifest = new java.util.HashMap<>();
        }

        Set<String> seen = new HashSet<>();

        logCallback.accept("Starting backup from: " + sourceDir + "\n");
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IOException("Source directory does not exist or is not a directory: " + sourceDir);
        }
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    if (!Files.isRegularFile(file) && !Files.isDirectory(file)) {
                        logCallback.accept("Skipping non-regular file: " + file + "\n");
                        return FileVisitResult.CONTINUE; // Skip non-regular files
                    }
                    Path relative = sourceDir.relativize(file);
                    if (relative.toString().isEmpty()) {
                        return FileVisitResult.CONTINUE; // Skip the root directory
                    }
                    seen.add(relative.toString());
                    if (relative.toString().contains("..")) {
                        throw new IOException("Invalid path: " + relative);
                    }
                    Path destFile = backupDir.resolve(relative);
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(destFile);
                        logCallback.accept(relative + " directory backed up\n");
                        return FileVisitResult.CONTINUE;
                    }
                    Files.createDirectories(destFile.getParent());

                    String sourceHash = sha256(file);
                    String destHash = Files.exists(destFile) ? sha256(destFile) : null;

                    if (!Files.exists(destFile) || !sourceHash.equals(destHash)) {
                        Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                        logCallback.accept(relative + (destHash == null ? " new backup\n" : " change backed up\n"));
                    }
                    manifest.put(relative.toString(), sourceHash);
                } catch (Exception e) {
                    e.printStackTrace();
                    logCallback.accept("Error processing file: " + file + " - " + e.getMessage() + "\n");
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (String tracked : manifest.keySet()) {
            if (!seen.contains(tracked)) {
                Path sourcePath = sourceDir.resolve(tracked);
                Path destPath = backupDir.resolve(tracked);

                if (Files.exists(destPath) && !Files.exists(sourcePath)) {
                    Files.createDirectories(sourcePath.getParent());
                    try {
                        Files.createSymbolicLink(sourcePath, destPath);
                        logCallback.accept(tracked + " soft link created\n");
                    } catch (Exception e) {
                        logCallback.accept("Could not create soft link for " + tracked + ": " + e.getMessage() + "\n");
                    }
                }
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(Files.newBufferedWriter(manifestPath), manifest);

        logCallback.accept("Backup complete. Log at " + logFile + "\n");
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}