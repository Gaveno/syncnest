import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

public class BackupRunner {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void runBackup(String source, String backup, String logDir) throws Exception {
        Path sourceDir = Paths.get(source).toRealPath();
        Path backupDir = Paths.get(backup).toRealPath();
        Path logDirectory = Paths.get(logDir);
        Files.createDirectories(logDirectory);

        Path logFile = logDirectory.resolve("backup_log_" + System.currentTimeMillis() + ".txt");

        Map<String, String> manifest = new HashMap<>();
        Path manifestPath = backupDir.resolve("backup_manifest.json");
        if (Files.exists(manifestPath)) {
            manifest = mapper.readValue(Files.newBufferedReader(manifestPath), Map.class);
        }

        Set<String> seen = new HashSet<>();

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    Path relative = sourceDir.relativize(file);
                    seen.add(relative.toString());
                    Path destFile = backupDir.resolve(relative);
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
