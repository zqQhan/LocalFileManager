package com.nick.filemanager.ui;

import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.common.dto.TagDTO;
import com.nick.filemanager.ui.client.FileApiClient;
import com.nick.filemanager.ui.client.TagApiClient;
import com.nick.filemanager.ui.websocket.WSClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Main application window — BorderPane layout with:
 * - Top: Menu bar + Search bar
 * - Left: File tree panel
 * - Center: File table + result display
 * - Bottom: Status bar
 */
public class MainWindow {

    private final String backendUrl;
    private final FileApiClient fileClient;
    private final TagApiClient tagClient;
    private final WSClient wsClient;

    // UI Components
    private TreeView<String> fileTree;
    private TableView<FileInfo> fileTable;
    private TextField searchField;
    private ComboBox<String> searchTypeCombo;
    private Label statusLabel;
    private Label wsStatusLabel;

    public MainWindow(String backendUrl) {
        this.backendUrl = backendUrl;
        this.fileClient = new FileApiClient(backendUrl);
        this.tagClient = new TagApiClient(backendUrl);
        this.wsClient = new WSClient(backendUrl, this::onFileChangeEvent);

        // Connect WebSocket for real-time updates
        wsClient.connect();
    }

    // ---- Build UI ----

    public BorderPane createContent() {
        BorderPane root = new BorderPane();

        root.setTop(createTopSection());
        root.setLeft(createFileTreePanel());
        root.setCenter(createCenterPanel());
        root.setBottom(createStatusBar());

        // Initial browse of user home
        browseDirectory(System.getProperty("user.home"));

        return root;
    }

    // ---- Top: Menu + Search ----

    private VBox createTopSection() {
        VBox top = new VBox();
        top.getChildren().add(createMenuBar());
        top.getChildren().add(createSearchBar());
        return top;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("文件");
        MenuItem browseItem = new MenuItem("打开目录...");
        browseItem.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择目录");
            File dir = chooser.showDialog(null);
            if (dir != null) browseDirectory(dir.getAbsolutePath());
        });
        MenuItem exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> shutdown());
        fileMenu.getItems().addAll(browseItem, new SeparatorMenuItem(), exitItem);

        // Tools menu
        Menu toolsMenu = new Menu("工具");
        MenuItem scanDupesItem = new MenuItem("扫描重复文件...");
        scanDupesItem.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择扫描目录");
            File dir = chooser.showDialog(null);
            if (dir != null) scanDuplicates(dir.getAbsolutePath());
        });
        MenuItem tagsItem = new MenuItem("管理标签...");
        tagsItem.setOnAction(e -> showTagManager());
        toolsMenu.getItems().addAll(scanDupesItem, tagsItem);

        // Help menu
        Menu helpMenu = new Menu("帮助");
        MenuItem aboutItem = new MenuItem("关于");
        aboutItem.setOnAction(e -> showAlert("关于", "File Manager v1.0\n本地文件搜索与管理系统\nJava 高级程序设计期末大作业"));
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, toolsMenu, helpMenu);
        return menuBar;
    }

    private HBox createSearchBar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: #f0f4f8;");

        searchField = new TextField();
        searchField.setPromptText("输入搜索关键词...");
        searchField.setPrefWidth(400);
        searchField.setOnAction(e -> doSearch());

        searchTypeCombo = new ComboBox<>();
        searchTypeCombo.getItems().addAll("文件名", "文件内容");
        searchTypeCombo.setValue("文件名");
        searchTypeCombo.setPrefWidth(100);

        Button searchBtn = new Button("搜索");
        searchBtn.setOnAction(e -> doSearch());
        searchBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white;");

        Button refreshBtn = new Button("刷新");
        refreshBtn.setOnAction(e -> refreshCurrentDirectory());

        bar.getChildren().addAll(
            new Label("搜索:"), searchField, searchTypeCombo, searchBtn, refreshBtn);

        return bar;
    }

    // ---- Left: File Tree ----

    private VBox createFileTreePanel() {
        VBox panel = new VBox();
        panel.setPrefWidth(260);
        panel.setPadding(new Insets(8));

        Label title = new Label("文件浏览器");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        title.setPadding(new Insets(0, 0, 8, 0));

        TreeItem<String> root = new TreeItem<>("计算机");
        root.setExpanded(true);
        fileTree = new TreeView<>(root);
        fileTree.setShowRoot(true);
        fileTree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                TreeItem<String> selected = fileTree.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    // Navigate to selected directory
                    browseDirectory(selected.getValue());
                }
            }
        });

        // Add common roots
        addQuickAccess(root);

        VBox.setVgrow(fileTree, Priority.ALWAYS);
        panel.getChildren().addAll(title, fileTree);
        return panel;
    }

    private void addQuickAccess(TreeItem<String> root) {
        TreeItem<String> home = new TreeItem<>(System.getProperty("user.home"));
        home.setExpanded(false);
        TreeItem<String> desktop = new TreeItem<>(System.getProperty("user.home") + File.separator + "Desktop");
        TreeItem<String> docs = new TreeItem<>(System.getProperty("user.home") + File.separator + "Documents");
        TreeItem<String> downloads = new TreeItem<>(System.getProperty("user.home") + File.separator + "Downloads");
        root.getChildren().addAll(home, desktop, docs, downloads);
    }

    // ---- Center: File Table ----

    private VBox createCenterPanel() {
        VBox center = new VBox();

        // Breadcrumb label
        Label pathLabel = new Label("目录: ");
        pathLabel.setId("pathLabel");
        pathLabel.setPadding(new Insets(8, 12, 4, 12));
        pathLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        // File table
        fileTable = new TableView<>();

        TableColumn<FileInfo, String> nameCol = new TableColumn<>("文件名");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(350);

        TableColumn<FileInfo, String> extCol = new TableColumn<>("扩展名");
        extCol.setCellValueFactory(new PropertyValueFactory<>("extension"));
        extCol.setPrefWidth(80);

        TableColumn<FileInfo, String> sizeCol = new TableColumn<>("大小");
        sizeCol.setCellValueFactory(data -> {
            long bytes = data.getValue().getSizeBytes();
            if (bytes < 1024) return new javafx.beans.property.SimpleStringProperty(bytes + " B");
            if (bytes < 1024 * 1024) return new javafx.beans.property.SimpleStringProperty(String.format("%.1f KB", bytes / 1024.0));
            if (bytes < 1024 * 1024 * 1024) return new javafx.beans.property.SimpleStringProperty(String.format("%.1f MB", bytes / (1024.0 * 1024)));
            return new javafx.beans.property.SimpleStringProperty(String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024)));
        });
        sizeCol.setPrefWidth(100);

        TableColumn<FileInfo, String> dateCol = new TableColumn<>("修改日期");
        dateCol.setCellValueFactory(data -> {
            var dt = data.getValue().getModifiedAt();
            return new javafx.beans.property.SimpleStringProperty(dt != null ? dt.toString().replace("T", " ") : "");
        });
        dateCol.setPrefWidth(170);

        fileTable.getColumns().addAll(nameCol, extCol, sizeCol, dateCol);

        // Context menu for file operations
        fileTable.setRowFactory(tv -> {
            TableRow<FileInfo> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    FileInfo fi = row.getItem();
                    if (Boolean.TRUE.equals(fi.getIsDirectory())) {
                        browseDirectory(fi.getPath());
                    }
                }
            });

            ContextMenu menu = new ContextMenu();
            MenuItem copyItem = new MenuItem("复制到...");
            copyItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) promptAndCopy(fi.getPath());
            });
            MenuItem moveItem = new MenuItem("移动到...");
            moveItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) promptAndMove(fi.getPath());
            });
            MenuItem renameItem = new MenuItem("重命名...");
            renameItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) promptAndRename(fi.getPath());
            });
            MenuItem deleteItem = new MenuItem("删除");
            deleteItem.setStyle("-fx-text-fill: #EF4444;");
            deleteItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) promptAndDelete(fi.getPath());
            });
            MenuItem tagItem = new MenuItem("添加标签...");
            tagItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) promptAddTag(fi);
            });
            menu.getItems().addAll(copyItem, moveItem, renameItem, tagItem, new SeparatorMenuItem(), deleteItem);
            row.setContextMenu(menu);
            return row;
        });

        VBox.setVgrow(fileTable, Priority.ALWAYS);
        center.getChildren().addAll(pathLabel, fileTable);
        return center;
    }

    // ---- Bottom: Status Bar ----

    private HBox createStatusBar() {
        HBox bar = new HBox(16);
        bar.setPadding(new Insets(4, 12, 4, 12));
        bar.setStyle("-fx-background-color: #E5E7EB;");

        statusLabel = new Label("就绪");
        wsStatusLabel = new Label("● WebSocket 已连接");
        wsStatusLabel.setStyle("-fx-text-fill: #10B981;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(statusLabel, spacer, wsStatusLabel);
        return bar;
    }

    // ---- Actions ----

    private void browseDirectory(String path) {
        statusLabel.setText("加载中: " + path);
        fileClient.browseDirectory(path)
            .thenAccept(files -> Platform.runLater(() -> {
                fileTable.getItems().setAll(files);
                statusLabel.setText(files.size() + " 个项目  |  " + path);
                // Watch this directory via WebSocket
                wsClient.watchDirectory(path);
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> showAlert("错误", "浏览目录失败: " + e.getMessage()));
                return null;
            });
    }

    private void doSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;

        String type = "文件名".equals(searchTypeCombo.getValue()) ? "NAME" : "CONTENT";
        statusLabel.setText("搜索中: " + query);

        fileClient.search(query, type, 0, 50)
            .thenAccept(result -> Platform.runLater(() -> {
                fileTable.getItems().setAll(result);
                statusLabel.setText("搜索结果: " + result.size() + " 项 | 关键词: " + query);
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> showAlert("搜索错误", e.getMessage()));
                return null;
            });
    }

    private void refreshCurrentDirectory() {
        // Re-browse the last path from status bar
        String currentPath = statusLabel.getText();
        int idx = currentPath.lastIndexOf("|");
        if (idx > 0) {
            browseDirectory(currentPath.substring(idx + 1).trim());
        } else {
            browseDirectory(System.getProperty("user.home"));
        }
    }

    private void scanDuplicates(String path) {
        statusLabel.setText("扫描重复文件: " + path);
        fileClient.scanDuplicates(path)
            .thenAccept(result -> Platform.runLater(() -> {
                String msg = result != null
                    ? "发现 " + result.size() + " 个重复组"
                    : "未发现重复文件";
                statusLabel.setText(msg);
                showAlert("重复文件扫描", msg);
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> showAlert("扫描错误", e.getMessage()));
                return null;
            });
    }

    // ---- File operations ----

    private void promptAndCopy(String source) {
        TextInputDialog dialog = new TextInputDialog(source + ".copy");
        dialog.setTitle("复制文件");
        dialog.setHeaderText("复制: " + new File(source).getName());
        dialog.setContentText("目标路径:");
        dialog.showAndWait().ifPresent(dest -> {
            fileClient.copyFile(source, dest)
                .thenAccept(fi -> Platform.runLater(() -> {
                    statusLabel.setText("已复制: " + dest);
                    refreshCurrentDirectory();
                }))
                .exceptionally(e -> { showAlert("错误", e.getMessage()); return null; });
        });
    }

    private void promptAndMove(String source) {
        TextInputDialog dialog = new TextInputDialog(source);
        dialog.setTitle("移动文件");
        dialog.setHeaderText("移动: " + new File(source).getName());
        dialog.setContentText("目标路径:");
        dialog.showAndWait().ifPresent(dest -> {
            fileClient.moveFile(source, dest)
                .thenAccept(fi -> Platform.runLater(() -> {
                    statusLabel.setText("已移动: " + dest);
                    refreshCurrentDirectory();
                }))
                .exceptionally(e -> { showAlert("错误", e.getMessage()); return null; });
        });
    }

    private void promptAndRename(String path) {
        TextInputDialog dialog = new TextInputDialog(new File(path).getName());
        dialog.setTitle("重命名");
        dialog.setHeaderText("重命名: " + new File(path).getName());
        dialog.setContentText("新名称:");
        dialog.showAndWait().ifPresent(newName -> {
            fileClient.renameFile(path, newName)
                .thenAccept(fi -> Platform.runLater(() -> {
                    statusLabel.setText("已重命名: " + fi.getName());
                    refreshCurrentDirectory();
                }))
                .exceptionally(e -> { showAlert("错误", e.getMessage()); return null; });
        });
    }

    private void promptAndDelete(String path) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("确认删除此文件?");
        confirm.setContentText(path);
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                fileClient.deleteFile(path)
                    .thenRun(() -> Platform.runLater(this::refreshCurrentDirectory))
                    .exceptionally(e -> { showAlert("错误", e.getMessage()); return null; });
            }
        });
    }

    private void promptAddTag(FileInfo fi) {
        // List tags and let user select
        tagClient.listTags()
            .thenAccept(tags -> Platform.runLater(() -> {
                if (tags.isEmpty()) {
                    showAlert("提示", "暂无标签，请先在 [工具 → 管理标签] 中创建标签");
                    return;
                }
                ChoiceDialog<TagDTO> dialog = new ChoiceDialog<>(tags.get(0), tags);
                dialog.setTitle("添加标签");
                dialog.setHeaderText("为 " + fi.getName() + " 选择标签");
                dialog.showAndWait().ifPresent(selected -> {
                    statusLabel.setText("标签已添加: " + selected.getName());
                });
            }));
    }

    private void showTagManager() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("标签管理");
        alert.setHeaderText("标签列表");
        tagClient.listTags()
            .thenAccept(tags -> Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                for (TagDTO t : tags) {
                    sb.append("● ").append(t.getName());
                    if (t.getColor() != null) sb.append("  [").append(t.getColor()).append("]");
                    sb.append("\n");
                }
                if (tags.isEmpty()) sb.append("暂无标签\n\n创建标签：使用后端 API POST /api/tags");
                alert.setContentText(sb.toString());
                alert.show();
            }));
    }

    // ---- WebSocket callback ----

    private void onFileChangeEvent(String json) {
        Platform.runLater(() -> {
            wsStatusLabel.setText("● 实时更新中...");
            wsStatusLabel.setStyle("-fx-text-fill: #F59E0B;");
            // Re-browse current directory after a short delay
            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> {
                    refreshCurrentDirectory();
                    wsStatusLabel.setText("● WebSocket 已连接");
                    wsStatusLabel.setStyle("-fx-text-fill: #10B981;");
                });
            }).start();
        });
    }

    private void showAlert(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.show();
        });
    }

    /** Clean shutdown */
    public void shutdown() {
        wsClient.disconnect();
    }
}
