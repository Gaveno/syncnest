import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.geometry.Insets;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;

public class SyncNestApp extends Application {
    private TextField sourceField, backupField, logField;
    private CheckBox autoStartBox, autoBackupBox;
    private TextField scheduleField;
    private TextArea logWindow; // Log window for displaying log messages
    private static final Path CONFIG_PATH = Paths.get("syncnest_config.json");
    private final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("SyncNest - Smart Backup");

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        sourceField = new TextField();
        Button browseSource = new Button("Browse");
        browseSource.setOnAction(e -> chooseDir(sourceField));

        backupField = new TextField();
        Button browseBackup = new Button("Browse");
        browseBackup.setOnAction(e -> chooseDir(backupField));

        logField = new TextField();
        Button browseLog = new Button("Browse");
        browseLog.setOnAction(e -> chooseDir(logField));

        autoStartBox = new CheckBox("Run at Startup");
        autoBackupBox = new CheckBox("Enable Auto Backup");
        scheduleField = new TextField();

        Button save = new Button("Save Settings");
        save.setOnAction(e -> saveConfig());

        Button runNow = new Button("Run Backup Now");
        runNow.setOnAction(e -> {
            try {
                logWindow.clear(); // Clear the log window before running the backup
                BackupRunner.runBackup(
                    sourceField.getText(),
                    backupField.getText(),
                    logField.getText(),
                    logWindow::appendText // Pass a callback to update the log window
                );
            } catch (Exception ex) {
                ex.printStackTrace();
                logWindow.appendText("Backup failed: " + ex.getMessage());
            }
        });

        logWindow = new TextArea(); // Initialize the log window
        logWindow.setEditable(false); // Make the log window read-only
        logWindow.setPrefHeight(200); // Set a preferred height for the log window

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Source Dir:"), 0, 0);
        grid.add(sourceField, 1, 0);
        grid.add(browseSource, 2, 0);

        grid.add(new Label("Backup Dir:"), 0, 1);
        grid.add(backupField, 1, 1);
        grid.add(browseBackup, 2, 1);

        grid.add(new Label("Log Dir:"), 0, 2);
        grid.add(logField, 1, 2);
        grid.add(browseLog, 2, 2);

        grid.add(autoStartBox, 1, 3);
        grid.add(autoBackupBox, 1, 4);
        grid.add(new Label("Schedule (cron or time):"), 0, 5);
        grid.add(scheduleField, 1, 5);

        root.getChildren().addAll(grid, save, runNow, logWindow);

        loadConfig();

        stage.setScene(new Scene(root, 600, 450));
        stage.show();
    }

    private void chooseDir(TextField field) {
        DirectoryChooser chooser = new DirectoryChooser();
        File selected = chooser.showDialog(null);
        if (selected != null) {
            field.setText(selected.getAbsolutePath());
        }
    }

    private void saveConfig() {
        try {
            Config cfg = new Config();
            cfg.sourceDir = sourceField.getText();
            cfg.backupDir = backupField.getText();
            cfg.logDir = logField.getText();
            cfg.runAtStartup = autoStartBox.isSelected();
            cfg.autoBackup = autoBackupBox.isSelected();
            cfg.schedule = scheduleField.getText();
            mapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_PATH.toFile(), cfg);
            logWindow.appendText("Settings saved successfully!\n");
        } catch (Exception e) {
            e.printStackTrace();
            logWindow.appendText("Error saving settings: " + e.getMessage() + "\n");
        }
    }

    private void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                Config cfg = mapper.readValue(CONFIG_PATH.toFile(), Config.class);
                sourceField.setText(cfg.sourceDir);
                backupField.setText(cfg.backupDir);
                logField.setText(cfg.logDir);
                autoStartBox.setSelected(cfg.runAtStartup);
                autoBackupBox.setSelected(cfg.autoBackup);
                scheduleField.setText(cfg.schedule);
                logWindow.appendText("Configuration loaded successfully.\n");
            } else {
                logWindow.appendText("No configuration file found. Using default settings.\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
            logWindow.appendText("Error loading configuration: " + e.getMessage() + "\n");
        }
    }

    public static class Config {
        public String sourceDir;
        public String backupDir;
        public String logDir;
        public boolean runAtStartup;
        public boolean autoBackup;
        public String schedule;
    }
}