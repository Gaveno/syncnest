import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BackupRunner {
    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: BackupRunner <sourceDir> <backupDir> <logFile>");
        }
        Path sourceDir = Paths.get(args[0]);
        Path backupDir = Paths.get(args[1]);
        Path manifestPath = backupDir.resolve("manifest.json");
        Path logFile = Paths.get(args[2]);

        Files.createDirectories(backupDir);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> manifest;

        if (Files.exists(manifestPath)) {
            manifest = mapper.readValue(Files.newBufferedReader(manifestPath), Map.class);
        } else {
            manifest = new java.util.HashMap<>();
        }

        Set<String> seen = new HashSet<>();

        // Log the source directory
        log(logFile, "Starting backup from: " + sourceDir);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IOException("Source directory does not exist or is not a directory: " + sourceDir);
        }
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    if (!Files.isRegularFile(file) && !Files.isDirectory(file)) {
                        log(logFile, "Skipping non-regular file: " + file);
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
                        log(logFile, relative + " directory backed up");
                        return FileVisitResult.CONTINUE;
                    }
                    Files.createDirectories(destFile.getParent());

                    String sourceHash = sha256(file);
                    String destHash = Files.exists(destFile) ? sha256(destFile) : null;

                    if (!Files.exists(destFile) || !sourceHash.equals(destHash)) {
                        Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                        log(logFile, relative + (destHash == null ? " new backup" : " change backed up"));
                    }
                    manifest.put(relative.toString(), sourceHash);
                } catch (Exception e) {
                    e.printStackTrace();
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
                        log(logFile, tracked + " soft link created");
                    } catch (Exception e) {
                        log(logFile, "Could not create soft link for " + tracked + ": " + e.getMessage());
                    }
                }
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(Files.newBufferedWriter(manifestPath), manifest);

        System.out.println("Backup complete. Log at " + logFile);
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

    private static void log(Path logFile, String line) throws IOException {
        Files.write(logFile, Collections.singleton(line), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}