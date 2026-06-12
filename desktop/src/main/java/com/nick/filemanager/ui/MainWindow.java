package com.nick.filemanager.ui;

import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.common.dto.TagDTO;
import com.nick.filemanager.ui.client.FileApiClient;
import com.nick.filemanager.ui.client.TagApiClient;
import com.nick.filemanager.ui.websocket.WSClient;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pac-File Arcade — a JavaFX command center for local file management.
 */
public class MainWindow {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String USER_HOME = System.getProperty("user.home");

    private final String backendUrl;
    private final FileApiClient fileClient;
    private final TagApiClient tagClient;
    private final WSClient wsClient;

    private BorderPane root;
    private TableView<FileInfo> fileTable;
    private TextField searchField;
    private TextField extensionField;
    private ComboBox<String> searchTypeCombo;
    private CheckBox regexCheck;
    private Label statusLabel;
    private Label wsStatusLabel;
    private Label currentPathLabel;
    private Label resultCountLabel;
    private Button backButton;
    private Button forwardButton;
    private Button upButton;
    private Label selectionNameLabel;
    private Label selectionMetaLabel;
    private Label selectionPathLabel;
    private Label selectionHashLabel;
    private Label totalFilesMetric;
    private Label totalSizeMetric;
    private Label duplicateMetric;
    private Label uniqueMetric;
    private ListView<String> activityList;
    private VBox largestFilesBox;
    private HBox pacmanLoader;
    private Label pacmanGlyph;
    private List<Label> pacmanDots;
    private Timeline pacmanTimeline;
    private int pacmanFrame;
    private Circle wsPulse;

    private String currentDirectory = USER_HOME;
    private String lastSearchQuery = "";
    private String lastSearchType = "NAME";
    private final Deque<String> backHistory = new ArrayDeque<>();
    private final Deque<String> forwardHistory = new ArrayDeque<>();

    public MainWindow(String backendUrl) {
        this.backendUrl = backendUrl;
        this.fileClient = new FileApiClient(backendUrl);
        this.tagClient = new TagApiClient(backendUrl);
        this.wsClient = new WSClient(backendUrl, this::onFileChangeEvent);
        wsClient.connect();
    }

    public BorderPane createContent() {
        root = new BorderPane();
        root.getStyleClass().add("app-shell");

        root.setTop(createTopSection());
        root.setLeft(createSidebar());
        root.setCenter(createCenterPanel());
        root.setRight(createInspector());
        root.setBottom(createStatusBar());

        browseDirectory(currentDirectory, false);
        refreshDashboard();
        playIntroAnimation(root);
        startPulse();
        return root;
    }

    // ---- Shell ----

    private VBox createTopSection() {
        VBox top = new VBox();
        top.getStyleClass().add("top-section");
        top.getChildren().addAll(createMenuBar(), createCommandDeck());
        return top;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.getStyleClass().add("ops-menu");

        Menu fileMenu = new Menu("文件");
        MenuItem browseItem = new MenuItem("打开目录...");
        browseItem.setOnAction(e -> chooseAndBrowse("选择工作目录"));
        MenuItem indexItem = new MenuItem("索引当前目录");
        indexItem.setOnAction(e -> indexCurrentDirectory(false));
        MenuItem exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> shutdown());
        fileMenu.getItems().addAll(browseItem, indexItem, new SeparatorMenuItem(), exitItem);

        Menu toolsMenu = new Menu("工具");
        MenuItem scanDupesItem = new MenuItem("扫描重复文件...");
        scanDupesItem.setOnAction(e -> chooseAndScanDuplicates());
        MenuItem statsItem = new MenuItem("刷新统计仪表盘");
        statsItem.setOnAction(e -> refreshDashboard());
        MenuItem tagsItem = new MenuItem("管理标签...");
        tagsItem.setOnAction(e -> showTagManager());
        toolsMenu.getItems().addAll(scanDupesItem, statsItem, tagsItem);

        Menu helpMenu = new Menu("帮助");
        MenuItem aboutItem = new MenuItem("关于");
        aboutItem.setOnAction(e -> showAlert(
            "关于",
            "File Manager v1.0\n本地文件搜索与管理系统\nPac-File Arcade JavaFX Client"));
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, toolsMenu, helpMenu);
        return menuBar;
    }

    private VBox createCommandDeck() {
        VBox deck = new VBox(14);
        deck.getStyleClass().add("command-deck");

        HBox titleRow = new HBox(16);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label mark = new Label("PAC");
        mark.getStyleClass().add("brand-mark");

        VBox titleBlock = new VBox(4);
        Label title = new Label("Pac-File Arcade");
        title.getStyleClass().add("screen-title");
        Label subtitle = new Label("用街机仪表盘浏览、搜索、索引和管理本地文件");
        subtitle.getStyleClass().add("screen-subtitle");
        titleBlock.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox healthPill = new HBox(8);
        healthPill.setAlignment(Pos.CENTER);
        healthPill.getStyleClass().add("health-pill");
        wsPulse = new Circle(5, Color.web("#f7d51d"));
        wsStatusLabel = new Label("实时更新已连接");
        wsStatusLabel.getStyleClass().add("health-text");
        healthPill.getChildren().addAll(wsPulse, wsStatusLabel);

        titleRow.getChildren().addAll(mark, titleBlock, spacer, healthPill);

        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        ImageView arcadeArt = new ImageView(new Image(getClass().getResource("/images/pac-file-arcade.png").toExternalForm()));
        arcadeArt.getStyleClass().add("arcade-art");
        arcadeArt.setFitWidth(198);
        arcadeArt.setFitHeight(92);
        arcadeArt.setPreserveRatio(false);

        searchField = new TextField();
        searchField.setPromptText("输入文件名或内容关键词，例如 Resource、*.java");
        searchField.getStyleClass().add("command-input");
        searchField.setOnAction(e -> doSearch());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchTypeCombo = new ComboBox<>();
        searchTypeCombo.getItems().addAll("文件名", "文件内容", "全部");
        searchTypeCombo.setValue("文件名");
        searchTypeCombo.getStyleClass().add("type-combo");
        searchTypeCombo.setPrefWidth(118);

        regexCheck = new CheckBox("正则");
        regexCheck.getStyleClass().add("regex-toggle");
        Tooltip.install(regexCheck, new Tooltip("启用后通过后端 regex=true 搜索文件名"));

        extensionField = new TextField();
        extensionField.setPromptText("扩展名");
        extensionField.getStyleClass().add("extension-input");
        extensionField.setPrefWidth(92);
        extensionField.setOnAction(e -> doSearch());

        Button searchBtn = createPrimaryButton("搜索", "按当前条件搜索文件");
        searchBtn.setOnAction(e -> doSearch());

        Button indexBtn = createGhostButton("索引当前目录", "同步索引当前目录，供搜索使用");
        indexBtn.setOnAction(e -> indexCurrentDirectory(false));

        Button exportBtn = createGhostButton("导出 CSV", "导出最近一次搜索结果");
        exportBtn.setOnAction(e -> exportLastSearch());

        Button refreshBtn = createIconButton("刷新", "刷新当前目录");
        refreshBtn.setOnAction(e -> refreshCurrentDirectory());

        searchRow.getChildren().addAll(
            arcadeArt, searchField, searchTypeCombo, regexCheck, extensionField, searchBtn, indexBtn, exportBtn, refreshBtn);

        HBox navRow = new HBox(8);
        navRow.setAlignment(Pos.CENTER_LEFT);
        backButton = createIconButton("后退", "返回上一个目录");
        backButton.setOnAction(e -> goBack());
        forwardButton = createIconButton("前进", "前往刚才后退前的目录");
        forwardButton.setOnAction(e -> goForward());
        upButton = createIconButton("上一级", "进入父目录");
        upButton.setOnAction(e -> goUp());
        Button openDirButton = createGhostButton("打开目录", "选择一个目录");
        openDirButton.setOnAction(e -> chooseAndBrowse("选择目录"));
        navRow.getChildren().addAll(backButton, forwardButton, upButton, openDirButton);

        currentPathLabel = new Label("当前目录: " + currentDirectory);
        currentPathLabel.getStyleClass().add("path-readout");

        deck.getChildren().addAll(titleRow, searchRow, navRow, currentPathLabel);
        return deck;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(16);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(284);

        Label label = new Label("快速访问");
        label.getStyleClass().add("section-label");

        VBox shortcuts = new VBox(8);
        shortcuts.getChildren().addAll(
            createNavButton("Home", USER_HOME),
            createNavButton("Documents", USER_HOME + File.separator + "Documents")
        );

        Separator separator = new Separator();
        separator.getStyleClass().add("soft-separator");

        Label opsLabel = new Label("常用操作");
        opsLabel.getStyleClass().add("section-label");

        VBox ops = new VBox(8);
        Button open = createSideAction("打开目录", "选择一个目录作为当前目录");
        open.setOnAction(e -> chooseAndBrowse("选择工作目录"));
        Button scan = createSideAction("扫描重复文件", "选择一个目录后扫描重复文件");
        scan.setOnAction(e -> chooseAndScanDuplicates());
        Button asyncIndex = createSideAction("异步索引状态", "说明后端 Kafka 异步索引当前问题");
        asyncIndex.setOnAction(e -> showAsyncIndexNotice());
        Button tags = createSideAction("查看标签", "查看后端已有标签");
        tags.setOnAction(e -> showTagManager());
        ops.getChildren().addAll(open, scan, asyncIndex, tags);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox backendCard = new VBox(8);
        backendCard.getStyleClass().add("backend-card");
        Label backendTitle = new Label("Backend");
        backendTitle.getStyleClass().add("mini-title");
        Label backend = new Label(backendUrl);
        backend.getStyleClass().add("mono-muted");
        Label hint = new Label("当前前端连接的后端地址");
        hint.getStyleClass().add("hint-text");
        hint.setWrapText(true);
        backendCard.getChildren().addAll(backendTitle, backend, hint);

        sidebar.getChildren().addAll(label, shortcuts, separator, opsLabel, ops, spacer, backendCard);
        return sidebar;
    }

    private VBox createCenterPanel() {
        VBox center = new VBox(14);
        center.getStyleClass().add("center-panel");

        HBox tableHeader = new HBox(12);
        tableHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBlock = new VBox(3);
        Label title = new Label("文件列表");
        title.getStyleClass().add("panel-title");
        resultCountLabel = new Label("等待加载");
        resultCountLabel.getStyleClass().add("panel-subtitle");
        titleBlock.getChildren().addAll(title, resultCountLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        FlowPane chips = new FlowPane(8, 8);
        chips.getChildren().addAll(createChip("REST"), createChip("Reactive"), createChip("Redis"), createChip("WebSocket"));

        tableHeader.getChildren().addAll(titleBlock, spacer, chips);

        fileTable = new TableView<>();
        fileTable.getStyleClass().add("file-table");
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        fileTable.setPlaceholder(new Label("暂无文件。请确认后端已启动，或选择一个可访问目录。"));
        configureTableColumns();
        configureTableInteractions();

        VBox.setVgrow(fileTable, Priority.ALWAYS);
        center.getChildren().addAll(tableHeader, fileTable);
        return center;
    }

    private VBox createInspector() {
        VBox inspector = new VBox(14);
        inspector.getStyleClass().add("inspector");
        inspector.setPrefWidth(330);

        Label detailTitle = new Label("文件详情");
        detailTitle.getStyleClass().add("section-label");

        VBox detailCard = new VBox(8);
        detailCard.getStyleClass().add("detail-card");
        selectionNameLabel = new Label("选择一个文件");
        selectionNameLabel.getStyleClass().add("detail-name");
        selectionMetaLabel = new Label("双击目录进入，右键执行文件操作");
        selectionMetaLabel.getStyleClass().add("detail-meta");
        selectionPathLabel = new Label("Path: -");
        selectionPathLabel.getStyleClass().add("detail-path");
        selectionPathLabel.setWrapText(true);
        selectionHashLabel = new Label("Hash: -");
        selectionHashLabel.getStyleClass().add("mono-muted");
        selectionHashLabel.setWrapText(true);
        detailCard.getChildren().addAll(selectionNameLabel, selectionMetaLabel, selectionPathLabel, selectionHashLabel);

        Label statsTitle = new Label("索引统计");
        statsTitle.getStyleClass().add("section-label");

        GridPane metrics = new GridPane();
        metrics.getStyleClass().add("metric-grid");
        metrics.setHgap(10);
        metrics.setVgap(10);
        totalFilesMetric = createMetricValue("—");
        totalSizeMetric = createMetricValue("—");
        duplicateMetric = createMetricValue("—");
        uniqueMetric = createMetricValue("—");
        metrics.add(createMetric("Files", totalFilesMetric), 0, 0);
        metrics.add(createMetric("Size", totalSizeMetric), 1, 0);
        metrics.add(createMetric("Dupes", duplicateMetric), 0, 1);
        metrics.add(createMetric("Hashes", uniqueMetric), 1, 1);

        VBox largestCard = new VBox(8);
        largestCard.getStyleClass().add("detail-card");
        Label largestTitle = new Label("最大文件");
        largestTitle.getStyleClass().add("mini-title");
        largestFilesBox = new VBox(6);
        largestFilesBox.getChildren().add(new Label("等待统计数据"));
        largestCard.getChildren().addAll(largestTitle, largestFilesBox);

        Label feedTitle = new Label("操作记录");
        feedTitle.getStyleClass().add("section-label");
        activityList = new ListView<>();
        activityList.getStyleClass().add("activity-list");
        activityList.setPrefHeight(150);
        activityList.getItems().add("前端已启动。");

        inspector.getChildren().addAll(detailTitle, detailCard, statsTitle, metrics, largestCard, feedTitle, activityList);
        return inspector;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        pacmanLoader = createPacmanLoader();
        pacmanLoader.setVisible(false);
        pacmanLoader.setManaged(false);

        statusLabel = new Label("就绪");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tips = new Label("Enter 搜索 · 双击目录进入 · 右键打开更多操作");
        tips.getStyleClass().add("status-tip");

        bar.getChildren().addAll(pacmanLoader, statusLabel, spacer, tips);
        return bar;
    }

    // ---- Table ----

    private void configureTableColumns() {
        TableColumn<FileInfo, String> signalCol = new TableColumn<>("信号");
        signalCol.setCellValueFactory(data -> new SimpleStringProperty(Boolean.TRUE.equals(data.getValue().getIsDirectory()) ? "DIR" : "FILE"));
        signalCol.setMaxWidth(72);
        signalCol.setMinWidth(72);
        signalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label badge = new Label(item);
                FileInfo fi = getTableRow() == null ? null : getTableRow().getItem();
                badge.getStyleClass().add(fi != null && Boolean.TRUE.equals(fi.getIsDirectory())
                    ? "dir-badge"
                    : "file-badge");
                setGraphic(badge);
            }
        });

        TableColumn<FileInfo, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(nullToDash(data.getValue().getName())));
        nameCol.setMinWidth(230);
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(item);
                name.getStyleClass().add("file-name-cell");
                FileInfo fi = getTableRow() == null ? null : getTableRow().getItem();
                if (fi != null && Boolean.TRUE.equals(fi.getIsDirectory())) {
                    name.getStyleClass().add("directory-name-cell");
                }
                setGraphic(name);
            }
        });

        TableColumn<FileInfo, String> extCol = new TableColumn<>("扩展");
        extCol.setCellValueFactory(data -> new SimpleStringProperty(formatExtension(data.getValue())));
        extCol.setMaxWidth(90);
        extCol.setMinWidth(78);

        TableColumn<FileInfo, String> sizeCol = new TableColumn<>("大小");
        sizeCol.setCellValueFactory(data -> new SimpleStringProperty(formatFileSize(data.getValue().getSizeBytes())));
        sizeCol.setMaxWidth(110);
        sizeCol.setMinWidth(92);

        TableColumn<FileInfo, String> dateCol = new TableColumn<>("修改时间");
        dateCol.setCellValueFactory(data -> {
            var dt = data.getValue().getModifiedAt();
            return new SimpleStringProperty(dt != null ? dt.format(DATE_FORMAT) : "—");
        });
        dateCol.setMinWidth(150);

        TableColumn<FileInfo, String> pathCol = new TableColumn<>("路径");
        pathCol.setCellValueFactory(data -> new SimpleStringProperty(nullToDash(data.getValue().getPath())));
        pathCol.setMinWidth(260);

        fileTable.getColumns().add(signalCol);
        fileTable.getColumns().add(nameCol);
        fileTable.getColumns().add(extCol);
        fileTable.getColumns().add(sizeCol);
        fileTable.getColumns().add(dateCol);
        fileTable.getColumns().add(pathCol);
    }

    private void configureTableInteractions() {
        fileTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updateSelection(selected));
        fileTable.setRowFactory(tv -> {
            TableRow<FileInfo> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY && !row.isEmpty()) {
                    FileInfo fi = row.getItem();
                    if (Boolean.TRUE.equals(fi.getIsDirectory())) {
                        browseDirectory(fi.getPath());
                    } else {
                        openInSystem(fi.getPath());
                    }
                }
            });

            ContextMenu menu = new ContextMenu();
            MenuItem openItem = new MenuItem("系统打开");
            openItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) openInSystem(fi.getPath());
            });
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
            MenuItem tagItem = new MenuItem("添加标签...");
            tagItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) promptAddTag(fi);
            });
            MenuItem deleteItem = new MenuItem("删除");
            deleteItem.getStyleClass().add("danger-menu-item");
            deleteItem.setOnAction(e -> {
                FileInfo fi = row.getItem();
                if (fi != null) promptAndDelete(fi.getPath());
            });
            menu.getItems().addAll(openItem, copyItem, moveItem, renameItem, tagItem, new SeparatorMenuItem(), deleteItem);
            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> row.setContextMenu(isEmpty ? null : menu));
            return row;
        });
    }

    // ---- Actions ----

    private void browseDirectory(String path) {
        browseDirectory(path, true);
    }

    private void browseDirectory(String path, boolean addToHistory) {
        if (path == null || path.isBlank()) return;
        String previousDirectory = currentDirectory;
        if (addToHistory && previousDirectory != null && !previousDirectory.equals(path)) {
            backHistory.push(previousDirectory);
            forwardHistory.clear();
        }
        currentDirectory = path;
        setBusy(true, "加载目录: " + path);
        currentPathLabel.setText("当前目录: " + path);
        updateNavigationButtons();
        fileClient.browseDirectory(path)
            .thenAccept(files -> Platform.runLater(() -> {
                List<FileInfo> sorted = files.stream()
                    .sorted(Comparator
                        .comparing((FileInfo f) -> !Boolean.TRUE.equals(f.getIsDirectory()))
                        .thenComparing(f -> nullToDash(f.getName()).toLowerCase(Locale.ROOT)))
                    .toList();
                fileTable.getItems().setAll(sorted);
                resultCountLabel.setText(sorted.size() + " 个项目 · " + summarizeDirectory(sorted));
                setBusy(false, sorted.size() + " 个项目 | " + path);
                wsClient.watchDirectory(path);
                addActivity("打开目录: " + path);
                playTableRefresh();
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    if (addToHistory && previousDirectory != null && !previousDirectory.equals(path)) {
                        currentDirectory = previousDirectory;
                        if (!backHistory.isEmpty() && backHistory.peek().equals(previousDirectory)) {
                            backHistory.pop();
                        }
                        currentPathLabel.setText("当前目录: " + currentDirectory);
                    }
                    updateNavigationButtons();
                    setBusy(false, "目录加载失败");
                    addActivity("目录加载失败: " + cleanError(e));
                    showAlert("浏览目录失败", cleanError(e));
                });
                return null;
            });
    }

    private void goBack() {
        if (backHistory.isEmpty()) return;
        forwardHistory.push(currentDirectory);
        browseDirectory(backHistory.pop(), false);
    }

    private void goForward() {
        if (forwardHistory.isEmpty()) return;
        backHistory.push(currentDirectory);
        browseDirectory(forwardHistory.pop(), false);
    }

    private void goUp() {
        File parent = new File(currentDirectory).getParentFile();
        if (parent != null) {
            browseDirectory(parent.getAbsolutePath());
        }
    }

    private void doSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            pulseNode(searchField);
            return;
        }

        String type = selectedSearchType();
        boolean regex = regexCheck.isSelected();
        String extension = extensionField.getText();
        lastSearchQuery = query;
        lastSearchType = type;

        setBusy(true, "正在搜索: " + query);
        fileClient.search(query, type, 0, 80, regex, extension)
            .thenAccept(result -> Platform.runLater(() -> {
                fileTable.getItems().setAll(result);
                resultCountLabel.setText("找到 " + result.size() + " 个结果 · " + (regex ? "正则搜索" : "普通搜索"));
                setBusy(false, "搜索完成: " + result.size() + " 项 | " + query);
                addActivity("搜索: " + query + " -> " + result.size());
                playTableRefresh();
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    setBusy(false, "搜索失败");
                    addActivity("Search failed: " + cleanError(e));
                    showAlert("搜索错误", cleanError(e));
                });
                return null;
            });
    }

    private void indexCurrentDirectory(boolean async) {
        setBusy(true, (async ? "正在异步索引: " : "正在索引: ") + currentDirectory);
        var request = async
            ? fileClient.indexDirectoryAsync(currentDirectory)
            : fileClient.indexDirectory(currentDirectory);
        request.thenAccept(result -> Platform.runLater(() -> {
                setBusy(false, "索引完成");
                addActivity((async ? "异步索引: " : "索引: ") + compactMap(result));
                refreshDashboard();
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    setBusy(false, "索引失败");
                    String reason = explainIndexError(cleanError(e));
                    addActivity("索引失败: " + reason);
                    showAlert("索引失败", reason);
                });
                return null;
            });
    }

    private void exportLastSearch() {
        String query = lastSearchQuery.isBlank() ? searchField.getText().trim() : lastSearchQuery;
        if (query.isBlank()) {
            showAlert("导出搜索结果", "请先执行一次搜索，再导出结果。");
            return;
        }

        setBusy(true, "正在导出 CSV: " + query);
        fileClient.exportSearch(query, lastSearchType, regexCheck.isSelected(), extensionField.getText(), "csv")
            .thenAccept(csv -> Platform.runLater(() -> {
                setBusy(false, "导出完成");
                showExportPreview(csv);
                addActivity("导出 CSV: " + query);
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    setBusy(false, "导出失败");
                    showAlert("导出错误", cleanError(e));
                });
                return null;
            });
    }

    private void refreshCurrentDirectory() {
        browseDirectory(currentDirectory);
        refreshDashboard();
    }

    private void refreshDashboard() {
        fileClient.getDashboard()
            .thenAccept(stats -> Platform.runLater(() -> updateDashboard(stats)))
            .exceptionally(e -> {
                Platform.runLater(() -> addActivity("Stats unavailable: " + cleanError(e)));
                return null;
            });
    }

    private void chooseAndBrowse(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        File dir = chooser.showDialog(null);
        if (dir != null) browseDirectory(dir.getAbsolutePath());
    }

    private void chooseAndScanDuplicates() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择扫描目录");
        File dir = chooser.showDialog(null);
        if (dir != null) scanDuplicates(dir.getAbsolutePath());
    }

    private void scanDuplicates(String path) {
        setBusy(true, "扫描重复文件: " + path);
        fileClient.scanDuplicates(path)
            .thenAccept(result -> Platform.runLater(() -> {
                String msg = summarizeDuplicateResult(result);
                setBusy(false, msg);
                addActivity("Duplicate scan: " + msg);
                showAlert("重复文件扫描", msg);
                refreshDashboard();
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    setBusy(false, "重复扫描失败");
                    showAlert("重复扫描失败", explainDuplicateScanError(cleanError(e)));
                });
                return null;
            });
    }

    private void showAsyncIndexNotice() {
        showAlert(
            "异步索引暂不调用",
            "后端 Kafka 异步索引接口存在消费者线程问题。\n\n触发现象：调用 /api/files/index/async 后，后端健康检查会出现 FileIndexConsumer#processIndexTask 的 HR000068 错误，服务状态变为 DOWN。\n\n这个属于后端实现问题，本次前端只做说明，不主动调用该接口。");
    }

    // ---- File operations ----

    private void promptAndCopy(String source) {
        TextInputDialog dialog = new TextInputDialog(source + ".copy");
        styleDialog(dialog.getDialogPane());
        dialog.setTitle("复制文件");
        dialog.setHeaderText("复制: " + new File(source).getName());
        dialog.setContentText("目标路径:");
        dialog.showAndWait().ifPresent(dest -> {
            setBusy(true, "复制中...");
            fileClient.copyFile(source, dest)
                .thenAccept(fi -> Platform.runLater(() -> {
                    setBusy(false, "已复制: " + dest);
                    addActivity("Copy: " + new File(source).getName());
                    refreshCurrentDirectory();
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        setBusy(false, "复制失败");
                        showAlert("错误", cleanError(e));
                    });
                    return null;
                });
        });
    }

    private void promptAndMove(String source) {
        TextInputDialog dialog = new TextInputDialog(source);
        styleDialog(dialog.getDialogPane());
        dialog.setTitle("移动文件");
        dialog.setHeaderText("移动: " + new File(source).getName());
        dialog.setContentText("目标路径:");
        dialog.showAndWait().ifPresent(dest -> {
            setBusy(true, "移动中...");
            fileClient.moveFile(source, dest)
                .thenAccept(fi -> Platform.runLater(() -> {
                    setBusy(false, "已移动: " + dest);
                    addActivity("Move: " + new File(source).getName());
                    refreshCurrentDirectory();
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        setBusy(false, "移动失败");
                        showAlert("错误", cleanError(e));
                    });
                    return null;
                });
        });
    }

    private void promptAndRename(String path) {
        TextInputDialog dialog = new TextInputDialog(new File(path).getName());
        styleDialog(dialog.getDialogPane());
        dialog.setTitle("重命名");
        dialog.setHeaderText("重命名: " + new File(path).getName());
        dialog.setContentText("新名称:");
        dialog.showAndWait().ifPresent(newName -> {
            setBusy(true, "重命名中...");
            fileClient.renameFile(path, newName)
                .thenAccept(fi -> Platform.runLater(() -> {
                    setBusy(false, "已重命名: " + fi.getName());
                    addActivity("Rename: " + fi.getName());
                    refreshCurrentDirectory();
                }))
                .exceptionally(e -> {
                    Platform.runLater(() -> {
                        setBusy(false, "重命名失败");
                        showAlert("错误", cleanError(e));
                    });
                    return null;
                });
        });
    }

    private void promptAndDelete(String path) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        styleDialog(confirm.getDialogPane());
        confirm.setTitle("确认删除");
        confirm.setHeaderText("确认删除此文件?");
        confirm.setContentText(path);
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                setBusy(true, "删除中...");
                fileClient.deleteFile(path)
                    .thenRun(() -> Platform.runLater(() -> {
                        setBusy(false, "已删除: " + new File(path).getName());
                        addActivity("Delete: " + new File(path).getName());
                        refreshCurrentDirectory();
                    }))
                    .exceptionally(e -> {
                        Platform.runLater(() -> {
                            setBusy(false, "删除失败");
                            showAlert("错误", cleanError(e));
                        });
                        return null;
                    });
            }
        });
    }

    private void promptAddTag(FileInfo fi) {
        tagClient.listTags()
            .thenAccept(tags -> Platform.runLater(() -> {
                if (tags.isEmpty()) {
                    showAlert("标签功能不可用", "后端目前只提供标签列表/创建/修改/删除接口，没有提供“把标签绑定到文件”的 API。\n\n因此前端不能真正给文件打标签。这个属于后端功能缺口，本次不改后端。");
                    return;
                }
                List<String> tagNames = tags.stream().map(TagDTO::getName).toList();
                ChoiceDialog<String> dialog = new ChoiceDialog<>(tagNames.get(0), tagNames);
                styleDialog(dialog.getDialogPane());
                dialog.setTitle("选择标签");
                dialog.setHeaderText("后端暂未提供文件-标签绑定接口");
                dialog.setContentText("只能查看已有标签，不能真正保存到文件:");
                dialog.showAndWait().ifPresent(selected -> {
                    setBusy(false, "标签未保存: 后端缺少绑定接口");
                    addActivity("标签未保存: 后端缺少绑定接口");
                    showAlert("标签未保存", "已选择标签 “" + selected + "”，但后端没有文件-标签绑定 API，所以无法保存。");
                });
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> showAlert("标签加载失败", cleanError(e)));
                return null;
            });
    }

    private void showTagManager() {
        tagClient.listTags()
            .thenAccept(tags -> Platform.runLater(() -> {
                StringBuilder sb = new StringBuilder();
                for (TagDTO t : tags) {
                    sb.append(t.getName());
                    if (t.getColor() != null) sb.append("  [").append(t.getColor()).append("]");
                    if (t.getFileCount() != null) sb.append("  ").append(t.getFileCount()).append(" files");
                    sb.append("\n");
                }
                if (tags.isEmpty()) sb.append("暂无标签。\n\n后端支持标签 CRUD，但当前没有文件-标签绑定接口。");
                else sb.append("\n说明：后端当前没有提供把标签绑定到文件的 API。");
                showAlert("标签列表", sb.toString());
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> showAlert("标签加载失败", cleanError(e)));
                return null;
            });
    }

    // ---- Updates ----

    private void updateSelection(FileInfo fi) {
        if (fi == null) {
            selectionNameLabel.setText("选择一个文件");
            selectionMetaLabel.setText("双击目录进入，右键执行文件操作");
            selectionPathLabel.setText("Path: -");
            selectionHashLabel.setText("Hash: -");
            return;
        }
        selectionNameLabel.setText(nullToDash(fi.getName()));
        selectionMetaLabel.setText((Boolean.TRUE.equals(fi.getIsDirectory()) ? "Directory" : "File")
            + " · " + formatExtension(fi)
            + " · " + formatFileSize(fi.getSizeBytes()));
        selectionPathLabel.setText("Path: " + nullToDash(fi.getPath()));
        selectionHashLabel.setText("Hash: " + nullToDash(shortHash(fi.getContentHash())));
        pulseNode(selectionNameLabel);
    }

    private void updateDashboard(Map<String, Object> stats) {
        totalFilesMetric.setText(stringValue(stats.get("totalFiles")));
        totalSizeMetric.setText(stringValue(stats.getOrDefault("totalSizeFormatted", "—")));
        duplicateMetric.setText(stringValue(stats.get("potentialDuplicates")));
        uniqueMetric.setText(stringValue(stats.get("uniqueContentHashes")));

        largestFilesBox.getChildren().clear();
        Object largest = stats.get("largestFiles");
        if (largest instanceof List<?> list && !list.isEmpty()) {
            list.stream().limit(5).forEach(item -> {
                if (item instanceof Map<?, ?> map) {
                    String name = stringValue(map.get("name"));
                    Object sizeValue = map.get("size");
                    if (sizeValue == null) sizeValue = map.get("sizeBytes");
                    String size = stringValue(sizeValue);
                    largestFilesBox.getChildren().add(createFileRank(name, size));
                }
            });
        }
        if (largestFilesBox.getChildren().isEmpty()) {
            Label empty = new Label("暂无统计数据，先索引目录。");
            empty.getStyleClass().add("hint-text");
            largestFilesBox.getChildren().add(empty);
        }
        addActivity("Stats refreshed.");
    }

    private void onFileChangeEvent(String json) {
        Platform.runLater(() -> {
            wsStatusLabel.setText("收到实时更新");
            wsPulse.setFill(Color.web("#ffd166"));
            addActivity("WS: " + compact(json));
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(650), e -> {
                    refreshCurrentDirectory();
                    wsStatusLabel.setText("实时更新已连接");
                    wsPulse.setFill(Color.web("#f7d51d"));
                })
            );
            timeline.play();
        });
    }

    // ---- UI Helpers ----

    private Button createPrimaryButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        Tooltip.install(button, new Tooltip(tooltip));
        return button;
    }

    private Button createGhostButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("ghost-button");
        Tooltip.install(button, new Tooltip(tooltip));
        return button;
    }

    private Button createIconButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("icon-button");
        Tooltip.install(button, new Tooltip(tooltip));
        return button;
    }

    private Button createNavButton(String label, String path) {
        Button button = new Button(label);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> browseDirectory(path));
        Tooltip.install(button, new Tooltip(path));
        return button;
    }

    private Button createSideAction(String label, String tooltip) {
        Button button = new Button(label);
        button.getStyleClass().add("side-action");
        button.setMaxWidth(Double.MAX_VALUE);
        Tooltip.install(button, new Tooltip(tooltip));
        return button;
    }

    private Label createChip(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("tech-chip");
        return label;
    }

    private VBox createMetric(String label, Label value) {
        VBox box = new VBox(5);
        box.getStyleClass().add("metric-card");
        Label caption = new Label(label);
        caption.getStyleClass().add("metric-caption");
        box.getChildren().addAll(value, caption);
        return box;
    }

    private Label createMetricValue(String text) {
        Label value = new Label(text);
        value.getStyleClass().add("metric-value");
        return value;
    }

    private HBox createFileRank(String name, String size) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label(">");
        dot.getStyleClass().add("rank-dot");
        Label file = new Label(compact(name));
        file.getStyleClass().add("rank-name");
        file.setMaxWidth(190);
        Label bytes = new Label(size);
        bytes.getStyleClass().add("rank-size");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(dot, file, spacer, bytes);
        return row;
    }

    private void setBusy(boolean busy, String message) {
        pacmanLoader.setVisible(busy);
        pacmanLoader.setManaged(busy);
        statusLabel.setText(message);
        if (busy) {
            startPacmanLoader();
        } else {
            stopPacmanLoader();
        }
    }

    private HBox createPacmanLoader() {
        HBox loader = new HBox(4);
        loader.setAlignment(Pos.CENTER_LEFT);
        loader.getStyleClass().add("pacman-loader");
        pacmanGlyph = new Label("C");
        pacmanGlyph.getStyleClass().add("pacman-glyph");
        pacmanDots = List.of(new Label("•"), new Label("•"), new Label("•"), new Label("•"));
        for (Label dot : pacmanDots) {
            dot.getStyleClass().add("pacman-dot");
        }
        loader.getChildren().add(pacmanGlyph);
        loader.getChildren().addAll(pacmanDots);
        return loader;
    }

    private void startPacmanLoader() {
        if (pacmanTimeline != null) return;
        pacmanFrame = 0;
        pacmanTimeline = new Timeline(new KeyFrame(Duration.millis(150), e -> advancePacmanFrame()));
        pacmanTimeline.setCycleCount(Timeline.INDEFINITE);
        pacmanTimeline.play();
    }

    private void stopPacmanLoader() {
        if (pacmanTimeline != null) {
            pacmanTimeline.stop();
            pacmanTimeline = null;
        }
        if (pacmanGlyph != null) pacmanGlyph.setText("C");
        if (pacmanDots != null) {
            for (Label dot : pacmanDots) dot.setVisible(true);
        }
    }

    private void advancePacmanFrame() {
        if (pacmanGlyph == null || pacmanDots == null) return;
        pacmanGlyph.setText(pacmanFrame % 2 == 0 ? "C" : "●");
        for (int i = 0; i < pacmanDots.size(); i++) {
            pacmanDots.get(i).setVisible(i >= pacmanFrame % (pacmanDots.size() + 1));
        }
        pacmanFrame++;
    }

    private void updateNavigationButtons() {
        if (backButton != null) backButton.setDisable(backHistory.isEmpty());
        if (forwardButton != null) forwardButton.setDisable(forwardHistory.isEmpty());
        if (upButton != null) upButton.setDisable(new File(currentDirectory).getParentFile() == null);
    }

    private void addActivity(String text) {
        if (activityList == null) return;
        activityList.getItems().add(0, compact(text));
        while (activityList.getItems().size() > 8) {
            activityList.getItems().remove(activityList.getItems().size() - 1);
        }
    }

    private void showExportPreview(String csv) {
        TextArea area = new TextArea(csv == null ? "" : csv);
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefSize(760, 360);
        area.getStyleClass().add("export-preview");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        styleDialog(alert.getDialogPane());
        alert.setTitle("CSV 导出预览");
        alert.setHeaderText("搜索结果已从后端导出。可从这里复制 CSV 内容。");
        alert.getDialogPane().setContent(area);
        alert.show();
    }

    private void showAlert(String title, String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            styleDialog(alert.getDialogPane());
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.show();
        });
    }

    private void styleDialog(javafx.scene.control.DialogPane pane) {
        if (root != null && root.getScene() != null) {
            pane.getStylesheets().addAll(root.getScene().getStylesheets());
        }
        pane.getStyleClass().add("ops-dialog");
    }

    private void openInSystem(String path) {
        if (path == null || path.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(new File(path));
                addActivity("Open: " + new File(path).getName());
            }
        } catch (IOException | IllegalArgumentException e) {
            showAlert("打开失败", e.getMessage());
        }
    }

    // ---- Animation ----

    private void playIntroAnimation(BorderPane shell) {
        List<Node> nodes = List.of(shell.getLeft(), shell.getTop(), shell.getCenter(), shell.getRight(), shell.getBottom());
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node == null) continue;
            node.setOpacity(0);
            node.setTranslateY(i == 0 ? 0 : 10);
            FadeTransition fade = new FadeTransition(Duration.millis(420), node);
            fade.setToValue(1);
            fade.setDelay(Duration.millis(70L * i));
            TranslateTransition slide = new TranslateTransition(Duration.millis(420), node);
            slide.setToY(0);
            slide.setDelay(Duration.millis(70L * i));
            fade.play();
            slide.play();
        }
    }

    private void playTableRefresh() {
        fileTable.setOpacity(0.45);
        fileTable.setTranslateY(8);
        FadeTransition fade = new FadeTransition(Duration.millis(260), fileTable);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(260), fileTable);
        slide.setToY(0);
        fade.play();
        slide.play();
    }

    private void pulseNode(Node node) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(140), node);
        scale.setFromX(1);
        scale.setFromY(1);
        scale.setToX(1.025);
        scale.setToY(1.025);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }

    private void startPulse() {
        if (wsPulse == null) return;
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(wsPulse.opacityProperty(), 0.45),
                new KeyValue(wsPulse.radiusProperty(), 4.5)),
            new KeyFrame(Duration.seconds(1.2),
                new KeyValue(wsPulse.opacityProperty(), 1.0),
                new KeyValue(wsPulse.radiusProperty(), 6.5))
        );
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    // ---- Formatting ----

    private String selectedSearchType() {
        return switch (searchTypeCombo.getValue()) {
            case "文件内容" -> "CONTENT";
            case "全部" -> "ALL";
            default -> "NAME";
        };
    }

    private String summarizeDirectory(List<FileInfo> files) {
        long dirs = files.stream().filter(f -> Boolean.TRUE.equals(f.getIsDirectory())).count();
        long regular = files.size() - dirs;
        return dirs + " dirs / " + regular + " files";
    }

    private String summarizeDuplicateResult(Object result) {
        if (result instanceof List<?> list) {
            return list.isEmpty() ? "未发现重复文件" : "发现 " + list.size() + " 个重复组";
        }
        if (result instanceof Map<?, ?> map && map.get("message") != null) {
            return stringValue(map.get("message"));
        }
        return result == null ? "未发现重复文件" : compact(result.toString());
    }

    private String compactMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "ok";
        return compact(map.toString());
    }

    private String formatExtension(FileInfo fi) {
        if (Boolean.TRUE.equals(fi.getIsDirectory())) return "folder";
        String ext = fi.getExtension();
        return ext == null || ext.isBlank() ? "file" : ext;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String cleanError(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private String explainIndexError(String message) {
        String detail = message == null ? "" : message;
        if (detail.contains("AccessDeniedException") || detail.contains("拒绝访问") || detail.contains("My Music")) {
            return "后端索引目录时遇到了无权限访问的系统目录，当前后端会直接返回失败。\n\n这属于后端 IndexService 遍历策略问题：它应该跳过不可访问目录，而不是让整个索引失败。\n\n原始错误: " + detail;
        }
        if (detail.contains("HTTP 415")) {
            return "后端要求 POST 请求带 JSON Content-Type。前端已经按 application/json 发送；如果仍出现 415，属于后端请求声明或网关转发问题。\n\n原始错误: " + detail;
        }
        return detail;
    }

    private String explainDuplicateScanError(String message) {
        String detail = message == null ? "" : message;
        if (detail.contains("Timeout") || detail.contains("timed out") || detail.contains("AccessDeniedException")
                || detail.contains("拒绝访问") || detail.contains("BlockedThreadChecker")) {
            return "后端重复文件扫描在大目录或受保护目录下容易超时/阻塞。\n\n这是后端 DuplicateService 的实现问题：它会同步遍历并读取文件，遇到大目录或无权限目录时容易卡住。建议演示时选择一个文件数量较少、权限明确的测试目录。\n\n原始错误: " + detail;
        }
        return detail;
    }

    private String shortHash(String hash) {
        if (hash == null || hash.isBlank()) return "—";
        return hash.length() <= 16 ? hash : hash.substring(0, 16) + "...";
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String stringValue(Object value) {
        return value == null ? "—" : value.toString();
    }

    private String compact(String text) {
        if (text == null) return "";
        String normalized = text.replace("\n", " ").replace("\r", " ").trim();
        return normalized.length() <= 72 ? normalized : normalized.substring(0, 69) + "...";
    }

    /** Clean shutdown */
    public void shutdown() {
        wsClient.disconnect();
    }
}
