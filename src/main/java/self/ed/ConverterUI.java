package self.ed;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import self.ed.javafx.CustomFormatCellFactory;
import self.ed.javafx.MultiPropertyValueFactory;
import self.ed.util.FormatUtils;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;
import static javafx.collections.FXCollections.observableArrayList;
import static self.ed.util.FileUtils.buildOutDir;
import static self.ed.util.FileUtils.listFiles;

public class ConverterUI extends Application {

    private final ObservableList<VideoRecord> files = observableArrayList();
    private File inDir;
    private File outDir;

    private Label info = new Label();

    public static void main(String[] args) {
        launch(args);
    }

    public void start(Stage stage) {
        // See https://docs.oracle.com/javase/8/javafx/layout-tutorial/builtin_layouts.htm#JFXLY102
        // https://docs.oracle.com/javase/8/javafx/interoperability-tutorial/concurrency.htm
        stage.setTitle("Bulk Video Converter");

        BorderPane layout = new BorderPane();
        layout.setTop(buildInputPane(stage));
        layout.setCenter(buildRecordTable());

        stage.setScene(new Scene(layout, 1000, 500));
        stage.show();
    }

    private Pane buildInputPane(Stage stage) {
        Label sourcePath = new Label();
        Label targetPath = new Label();

        DirectoryChooser directoryChooser = new DirectoryChooser();

        Button sourceButton = buildButton("Input...");
        sourceButton.setOnAction(e -> ofNullable(directoryChooser.showDialog(stage)).ifPresent(file -> {
            inDir = file;
            outDir = buildOutDir(inDir);
            // TODO: try to bind in/out path properties to corresponding files
            sourcePath.setText(inDir.getAbsolutePath());
            targetPath.setText(outDir.getAbsolutePath());
            loadFiles();
        }));

        Button targetButton = buildButton("Output...");
        targetButton.setOnAction(e -> ofNullable(directoryChooser.showDialog(stage)).ifPresent(file -> {
            outDir = file;
            targetPath.setText(outDir.getAbsolutePath());
            loadFiles();
        }));

        Button startButton = new Button("Start");
        startButton.setOnAction(e -> startAll());

        Button stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopAll());

        inDir = new File("/dummy");
        outDir = buildOutDir(inDir);
        sourcePath.setText(inDir.getAbsolutePath());
        targetPath.setText(outDir.getAbsolutePath());
        loadFiles();

        return new VBox(5,
                new HBox(10, sourceButton, sourcePath),
                new HBox(10, targetButton, targetPath),
                new HBox(10, startButton, stopButton, info)
        );
    }

    private TableView buildRecordTable() {
        TableColumn<VideoRecord, String> path = new TableColumn<>("Path");
        path.setMinWidth(500);
        path.setCellValueFactory(new PropertyValueFactory<>("path"));

        TableColumn<VideoRecord, Long> duration = new TableColumn<>("Duration");
        duration.setMinWidth(100);
        duration.setCellValueFactory(new PropertyValueFactory<>("duration"));
        duration.setCellFactory((CustomFormatCellFactory<VideoRecord, Long>) FormatUtils::formatTimeSeconds);

        TableColumn<VideoRecord, Long> size = new TableColumn<>("Size");
        size.setMinWidth(100);
        size.setCellValueFactory(new PropertyValueFactory<>("size"));
        size.setCellFactory((CustomFormatCellFactory<VideoRecord, Long>) FormatUtils::formatFileSize);

        TableColumn<VideoRecord, List<Long>> resolution = new TableColumn<>("Resolution");
        resolution.setMinWidth(100);
        resolution.setCellValueFactory(new MultiPropertyValueFactory<>("width", "height"));
        resolution.setCellFactory((CustomFormatCellFactory<VideoRecord, List<Long>>) FormatUtils::formatDimensions);

        TableColumn<VideoRecord, Double> progress = new TableColumn<>("Progress");
        progress.setMinWidth(100);
        progress.setCellValueFactory(new PropertyValueFactory<>("progress"));
        progress.setCellFactory(ProgressBarTableCell.forTableColumn());

        TableView<VideoRecord> table = new TableView<>(files);
        table.getColumns().addAll(path, duration, size, resolution, progress);
        return table;
    }

    private Button buildButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(80);
        return button;
    }

    private void loadFiles() {
        info("Collecting files...");
        files.clear();
        listFiles(inDir).stream()
                .map(path -> VideoRecord.newInstance(inDir, path, outDir))
                .collect(toCollection(() -> files));
    }

    private void info(String message) {
        System.out.println(new Date() + ": " + message);
        info.setText(message);
    }


    private void startAll() {
        info("Processing...");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        files.stream().map(this::createTask).forEach(executor::execute);
    }

    private void stopAll() {
        info("Stopping...");
        files.stream().map(VideoRecord::getTask).filter(Objects::nonNull).forEach(task -> task.cancel(false));
    }

    private Task createTask(VideoRecord record) {
        Task task = new Task<Void>() {
            {
                resetProgress();
            }

            @Override
            public Void call() {
                System.out.println("Converting: " + record.getInFile().getAbsolutePath() + " -> " + record.getOutFile().getAbsolutePath());
                Converter.convert(
                        record.getInFile().getAbsolutePath(),
                        record.getOutFile().getAbsolutePath(),
                        this::updateProgress
                );
//                int max = 50;
//                for (int i = 1; i <= max; i++) {
//                    if (isCancelled()) {
//                        resetProgress();
//                        System.out.println("Breaking...");
//                        break;
//                    }
//                    randomSleep();
//                    updateProgress(i, max);
//                }
                return null;
            }

            private void resetProgress() {
                updateProgress(0, 0);
            }
        };
        record.progressProperty().bind(task.progressProperty());
        record.setTask(task);
        return task;
    }
}
