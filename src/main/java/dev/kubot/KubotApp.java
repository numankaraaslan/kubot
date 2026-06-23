package dev.kubot;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dev.kubot.model.RelatedResource;
import dev.kubot.model.ResourceLookup;
import dev.kubot.service.KubernetesFacade;
import dev.kubot.service.RelationResolver;
import dev.kubot.service.YamlSupport;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class KubotApp extends Application
{
    private static final int APP_FONT_SIZE = 18;
    private static final int LEFT_PANEL_MIN_WIDTH = 240;
    private static final int CENTER_PANEL_MIN_WIDTH = 420;
    private static final int RIGHT_PANEL_MIN_WIDTH = 480;
    private static final Set<String> SYSTEM_NAMESPACES = Set.of("cert-manager", "default", "kube-node-lease", "kube-public", "kube-system", "local-path-storage", "traefik");

    private final KubernetesFacade kubernetes = new KubernetesFacade();
    private final RelationResolver relationResolver = new RelationResolver();
    private final ObservableList<V1Namespace> namespaces = FXCollections.observableArrayList();
    private final ObservableList<V1Pod> pods = FXCollections.observableArrayList();
    private final ObservableList<RelatedResource> relatedResources = FXCollections.observableArrayList();
    private final ObservableList<CoreV1Event> events = FXCollections.observableArrayList();

    private SplitPane mainSplitPane;
    private VBox namespacePane;
    private VBox podPane;
    private TabPane detailsPane;
    private ComboBox<String> contextSelector;
    private Label statusLabel;
    private CheckBox showNamespacesPane;
    private CheckBox showDetailsPane;
    private ListView<V1Namespace> namespaceList;
    private TableView<V1Pod> podTable;
    private TextField podFilter;
    private TextArea overviewText;
    private ListView<RelatedResource> relatedList;
    private TextArea relatedYaml;
    private TextArea eventsText;
    private ComboBox<String> containerSelector;
    private ComboBox<Integer> logLineSelector;
    private TextArea logsText;
    private TextField logFilter;
    private Button lastLogsButton;
    private Button moreLogsButton;

    private String selectedNamespace;
    private V1Pod selectedPod;
    private int requestedLogLines = 200;
    private String rawLogs = "";
    private ResourceLookup currentLookup = new ResourceLookup();

    @Override
    public void start(Stage stage)
    {
        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildMainPane());

        Scene scene = new Scene(root, 1280, 760);
        scene.getStylesheets().add(Objects.requireNonNull(KubotApp.class.getResource("/dev/kubot/kubot.css")).toExternalForm());
        root.setStyle("-fx-font-size: " + APP_FONT_SIZE + "px;");
        stage.setTitle("Kubot");
        stage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(KubotApp.class.getResourceAsStream("/dev/kubot/kubot-icon.png"))));
        stage.setScene(scene);
        stage.show();

        loadContexts();
    }

    private HBox buildTopBar()
    {
        contextSelector = new ComboBox<>();
        contextSelector.setPrefWidth(320);
        contextSelector.setPromptText("kube context");
        contextSelector.setOnAction(event -> connectSelectedContext());

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> refreshAll());

        Button setupHelpButton = new Button("Setup Help");
        setupHelpButton.setOnAction(event -> showSetupHelp());

        showNamespacesPane = new CheckBox("Namespaces");
        showNamespacesPane.setSelected(true);
        showNamespacesPane.setOnAction(event -> updatePanelVisibility());

        showDetailsPane = new CheckBox("Details");
        showDetailsPane.setSelected(true);
        showDetailsPane.setOnAction(event -> updatePanelVisibility());

        statusLabel = new Label("Starting...");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(10, new Label("Context"), contextSelector, refreshButton, setupHelpButton, showNamespacesPane, showDetailsPane, spacer, statusLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10));
        return topBar;
    }

    private SplitPane buildMainPane()
    {
        namespacePane = buildNamespacePane();
        podPane = buildPodPane();
        detailsPane = buildDetailsPane();

        namespacePane.setMinWidth(LEFT_PANEL_MIN_WIDTH);
        podPane.setMinWidth(CENTER_PANEL_MIN_WIDTH);
        detailsPane.setMinWidth(RIGHT_PANEL_MIN_WIDTH);

        mainSplitPane = new SplitPane(namespacePane, podPane, detailsPane);
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        setPanelDividers();
        return mainSplitPane;
    }

    private void updatePanelVisibility()
    {
        boolean wantNamespaces = showNamespacesPane.isSelected();
        boolean wantDetails = showDetailsPane.isSelected();

        if (wantNamespaces && !mainSplitPane.getItems().contains(namespacePane))
        {
            mainSplitPane.getItems().add(0, namespacePane);
        }
        else if (!wantNamespaces)
        {
            mainSplitPane.getItems().remove(namespacePane);
        }

        if (wantDetails && !mainSplitPane.getItems().contains(detailsPane))
        {
            mainSplitPane.getItems().add(detailsPane);
        }
        else if (!wantDetails)
        {
            mainSplitPane.getItems().remove(detailsPane);
        }

        Platform.runLater(() -> setPanelDividers());
    }

    private void setPanelDividers()
    {
        boolean hasNamespaces = mainSplitPane.getItems().contains(namespacePane);
        boolean hasDetails = mainSplitPane.getItems().contains(detailsPane);
        if (hasNamespaces && hasDetails)
        {
            mainSplitPane.setDividerPositions(0.18, 0.62);
        }
        else if (hasNamespaces)
        {
            mainSplitPane.setDividerPositions(0.28);
        }
        else if (hasDetails)
        {
            mainSplitPane.setDividerPositions(0.58);
        }
    }

    private VBox buildNamespacePane()
    {
        namespaceList = new ListView<>();
        namespaceList.getStyleClass().add("kubot-list");
        namespaceList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        namespaceList.setCellFactory(view -> new ListCell<>()
        {
            @Override
            protected void updateItem(V1Namespace item, boolean empty)
            {
                super.updateItem(item, empty);
                getStyleClass().remove("system-namespace-cell");
                if (empty || item == null)
                {
                    setText(null);
                    return;
                }
                String namespaceName = name(item.getMetadata());
                setText(namespaceName);
                if (isSystemNamespace(namespaceName))
                {
                    getStyleClass().add("system-namespace-cell");
                }
            }
        });
        namespaceList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedNamespace = newValue == null ? null : name(newValue.getMetadata());
            clearPodDetails();
            if (selectedNamespace != null)
            {
                loadPods(selectedNamespace);
            }
        });

        VBox pane = new VBox(8, new Label("Namespaces"), namespaceList);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(namespaceList, Priority.ALWAYS);
        return pane;
    }

    private VBox buildPodPane()
    {
        podFilter = new TextField();
        podFilter.setPromptText("Filter pods");

        Button refreshPodsButton = new Button("Refresh Pods");
        refreshPodsButton.setOnAction(event -> {
            if (selectedNamespace != null)
            {
                loadPods(selectedNamespace);
            }
        });
        HBox podHeader = new HBox(10, new Label("Pods"), refreshPodsButton, statusLegend());
        podHeader.setAlignment(Pos.CENTER_LEFT);

        podTable = new TableView<>();
        podTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        podTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> selectPod(newValue));

        TableColumn<V1Pod, String> name = column("Name", pod -> name(pod.getMetadata()), 240);
        TableColumn<V1Pod, String> phase = statusColumn();
        TableColumn<V1Pod, String> ready = column("Ready", pod -> readyContainers(pod), 80);
        TableColumn<V1Pod, String> restarts = column("Restarts", pod -> Integer.toString(restarts(pod)), 80);
        TableColumn<V1Pod, String> age = column("Age", pod -> age(pod.getMetadata()), 90);
        TableColumn<V1Pod, String> owner = column("Managed By", pod -> managedByLabel(pod), 220);
        podTable.getColumns().setAll(name, phase, ready, restarts, age, owner);

        FilteredList<V1Pod> filtered = new FilteredList<>(pods);
        filtered.predicateProperty().bind(javafx.beans.binding.Bindings.createObjectBinding(() -> {
            String filter = podFilter.getText() == null ? "" : podFilter.getText().trim().toLowerCase();
            return pod -> filter.isEmpty() || name(pod.getMetadata()).toLowerCase().contains(filter);
        }, podFilter.textProperty()));
        podTable.setItems(filtered);

        VBox pane = new VBox(8, podHeader, podFilter, podTable);
        pane.setPadding(new Insets(10));
        VBox.setVgrow(podTable, Priority.ALWAYS);
        return pane;
    }

    private HBox statusLegend()
    {
        HBox legend = new HBox(8, new Label("Status:"), legendItem("Warning", "legend-warning"), legendItem("Problem", "legend-error"), legendItem("Unknown", "legend-unknown"));
        legend.getStyleClass().add("status-legend");
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setFillHeight(false);
        return legend;
    }

    private HBox legendItem(String text, String colorClass)
    {
        Region dot = new Region();
        dot.setMinSize(16, 16);
        dot.setPrefSize(16, 16);
        dot.setMaxSize(16, 16);
        dot.getStyleClass().addAll("legend-dot", colorClass);
        Label label = new Label(text);
        label.getStyleClass().add("legend-label");
        HBox item = new HBox(4, dot, label);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setFillHeight(false);
        return item;
    }

    private TabPane buildDetailsPane()
    {
        overviewText = readOnlyArea();
        relatedList = new ListView<>(relatedResources);
        relatedList.getStyleClass().add("kubot-list");
        relatedList.setCellFactory(view -> new ListCell<>()
        {
            @Override
            protected void updateItem(RelatedResource item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        relatedList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            relatedYaml.setText(newValue == null ? "" : newValue.yaml());
        });
        relatedYaml = readOnlyArea();
        SplitPane relatedPane = new SplitPane(relatedList, relatedYaml);
        relatedPane.setOrientation(Orientation.VERTICAL);
        relatedPane.setDividerPositions(0.38);

        eventsText = readOnlyArea();

        containerSelector = new ComboBox<>();
        containerSelector.setPrefWidth(170);
        logLineSelector = new ComboBox<>(FXCollections.observableArrayList(100, 200, 400, 1000));
        logLineSelector.setMinWidth(110);
        logLineSelector.setPrefWidth(110);
        logLineSelector.getSelectionModel().select(Integer.valueOf(200));
        logLineSelector.setOnAction(event -> updateLogButtonLabels());
        lastLogsButton = new Button();
        lastLogsButton.setOnAction(event -> loadLogs(selectedLogLineCount()));
        moreLogsButton = new Button();
        moreLogsButton.setOnAction(event -> loadLogs(requestedLogLines + selectedLogLineCount()));
        updateLogButtonLabels();
        logsText = readOnlyArea();
        logFilter = new TextField();
        logFilter.setPromptText("Search in logs");
        logFilter.textProperty().addListener((obs, oldValue, newValue) -> applyLogFilter());
        FlowPane logsBar = new FlowPane(8, 8, new Label("From"), containerSelector, new Label("Rows"), logLineSelector, lastLogsButton, moreLogsButton);
        logsBar.setAlignment(Pos.CENTER_LEFT);
        VBox logsPane = new VBox(8, logsBar, logFilter, logsText);
        logsPane.setPadding(new Insets(8));
        VBox.setVgrow(logsText, Priority.ALWAYS);

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("Overview", overviewText));
        tabs.getTabs().add(tab("Related", relatedPane));
        tabs.getTabs().add(tab("Events", eventsText));
        tabs.getTabs().add(tab("Logs", logsPane));
        return tabs;
    }

    private TableColumn<V1Pod, String> column(String title, java.util.function.Function<V1Pod, String> value, int width)
    {
        TableColumn<V1Pod, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(value.apply(data.getValue())));
        return column;
    }

    private TableColumn<V1Pod, String> statusColumn()
    {
        TableColumn<V1Pod, String> column = new TableColumn<>("Status");
        column.setPrefWidth(120);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(podDisplayStatus(data.getValue())));
        column.setCellFactory(view -> new TableCell<>()
        {
            @Override
            protected void updateItem(String status, boolean empty)
            {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-warning", "status-error", "status-unknown");
                if (empty || status == null)
                {
                    setText(null);
                    return;
                }
                setText(status);
                V1Pod pod = getTableRow() == null ? null : getTableRow().getItem();
                if (pod != null)
                {
                    String severity = podStatusSeverity(pod);
                    if (!severity.isBlank())
                    {
                        getStyleClass().add(severity);
                    }
                }
            }
        });
        return column;
    }

    private Tab tab(String title, javafx.scene.Node content)
    {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private TextArea readOnlyArea()
    {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'Consolas', 'Monospaced'; -fx-font-size: " + APP_FONT_SIZE + "px;");
        return area;
    }

    private void loadContexts()
    {
        run("Loading kube contexts", () -> {
            List<String> contexts = kubernetes.contexts();
            if (contexts.isEmpty())
            {
                kubernetes.connect(null);
                return List.<String>of("(default kube config)");
            }
            return contexts;
        }, contexts -> {
            if (contexts.isEmpty())
            {
                contextSelector.setItems(FXCollections.observableArrayList());
                contextSelector.setPromptText("No kubeconfig");
                setStatus("No Kubernetes config found. Click Setup Help to add a cluster.");
                return;
            }
            contextSelector.setItems(FXCollections.observableArrayList(contexts));
            contextSelector.getSelectionModel().selectFirst();
            connectSelectedContext();
        });
    }

    private void connectSelectedContext()
    {
        String context = contextSelector.getSelectionModel().getSelectedItem();
        if (context == null || context.isBlank())
        {
            setStatus("No Kubernetes context selected. Click Setup Help if this is a new machine.");
            return;
        }
        if ("(default kube config)".equals(context))
        {
            context = null;
        }
        String selectedContext = context;
        run("Connecting to kube context", () -> {
            kubernetes.connect(selectedContext);
            return null;
        }, ignored -> refreshNamespaces());
    }

    private void refreshAll()
    {
        refreshNamespaces();
        if (selectedNamespace != null)
        {
            loadPods(selectedNamespace);
        }
    }

    private void showSetupHelp()
    {
        TextArea help = readOnlyArea();
        help.setWrapText(true);
        help.setPrefColumnCount(86);
        help.setPrefRowCount(22);
        help.setText("""
                Kubot reads the same kubeconfig file as kubectl:

                  C:\\Users\\<you>\\.kube\\config

                If kubectl already works, Kubot should show the same contexts.

                To check your current contexts:

                  kubectl config get-contexts

                To add a cluster manually, you usually need three things:

                  1. API server URL
                  2. user token or certificate credentials
                  3. cluster CA certificate

                Example shape:

                  kubectl config set-cluster my-cluster --server=https://SERVER:6443 --certificate-authority=ca.crt --embed-certs=true
                  kubectl config set-credentials my-user --token=TOKEN
                  kubectl config set-context my-context --cluster=my-cluster --user=my-user --namespace=default

                Then restart Kubot or press Refresh.

                If this is AKS/EKS/GKE, use that cloud provider's login command first; it usually writes kubeconfig for you.
                """);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Kubot Setup Help");
        alert.setHeaderText("Kubot needs a Kubernetes context in kubeconfig.");
        alert.getDialogPane().setContent(help);
        alert.setResizable(true);
        alert.showAndWait();
    }

    private void refreshNamespaces()
    {
        run("Loading namespaces", () -> kubernetes.namespaces(), loaded -> {
            namespaces.setAll(loaded.stream().sorted(Comparator.comparing((V1Namespace ns) -> isSystemNamespace(name(ns.getMetadata()))).thenComparing(ns -> name(ns.getMetadata()))).toList());
            refreshNamespaceList();
            if (loaded.size() == 1 && kubernetes.currentNamespace().equals(name(loaded.getFirst().getMetadata())))
            {
                setStatus("This context can only see namespace " + kubernetes.currentNamespace() + ".");
            }
        });
    }

    private void refreshNamespaceList()
    {
        List<V1Namespace> visible = namespaces.stream().toList();
        V1Namespace previous = namespaceList.getSelectionModel().getSelectedItem();
        namespaceList.setItems(FXCollections.observableArrayList(visible));
        if (previous != null && visible.stream().anyMatch(ns -> Objects.equals(name(ns.getMetadata()), name(previous.getMetadata()))))
        {
            namespaceList.getSelectionModel().select(previous);
        }
        else
        {
            namespaceList.getSelectionModel().clearSelection();
            namespaceList.getSelectionModel().selectFirst();
        }
    }

    private void loadPods(String namespace)
    {
        run("Loading pods in " + namespace, () -> kubernetes.pods(namespace), loaded -> {
            pods.setAll(loaded.stream().sorted(Comparator.comparing(pod -> name(pod.getMetadata()))).toList());
            if (!pods.isEmpty())
            {
                podTable.getSelectionModel().selectFirst();
            }
            loadNamespaceLookup(namespace);
            setStatus("Loaded " + pods.size() + " pods from " + namespace);
        });
    }

    private void loadNamespaceLookup(String namespace)
    {
        run("Loading related resource index", () -> kubernetes.lookup(namespace), lookup -> {
            currentLookup = lookup;
            podTable.refresh();
            if (selectedPod != null)
            {
                overviewText.setText(buildOverview(selectedPod));
                relatedResources.setAll(relationResolver.resolve(selectedPod, currentLookup));
                if (!relatedResources.isEmpty())
                {
                    relatedList.getSelectionModel().selectFirst();
                }
            }
        });
    }

    private void selectPod(V1Pod pod)
    {
        selectedPod = pod;
        requestedLogLines = selectedLogLineCount();
        logsText.clear();
        relatedYaml.clear();
        relatedResources.clear();
        events.clear();
        containerSelector.getItems().clear();
        updateLogButtonLabels();
        if (pod == null || selectedNamespace == null)
        {
            clearPodDetails();
            return;
        }
        containerSelector.setItems(FXCollections.observableArrayList(containerNames(pod)));
        containerSelector.getSelectionModel().selectFirst();
        overviewText.setText(buildOverview(pod));
        loadRelated(selectedNamespace, pod);
        loadEvents(selectedNamespace, pod);
    }

    private void clearPodDetails()
    {
        selectedPod = null;
        overviewText.clear();
        relatedResources.clear();
        relatedYaml.clear();
        eventsText.clear();
        logsText.clear();
        containerSelector.getItems().clear();
    }

    private int selectedLogLineCount()
    {
        Integer selected = logLineSelector == null ? null : logLineSelector.getSelectionModel().getSelectedItem();
        return selected == null ? 200 : selected;
    }

    private void updateLogButtonLabels()
    {
        int lines = selectedLogLineCount();
        if (lastLogsButton != null)
        {
            lastLogsButton.setText("Latest");
        }
        if (moreLogsButton != null)
        {
            moreLogsButton.setText("More");
        }
    }

    private void loadRelated(String namespace, V1Pod pod)
    {
        run("Finding related resources", () -> {
            return relationResolver.resolve(pod, currentLookup);
        }, loaded -> {
            relatedResources.setAll(loaded);
            if (!loaded.isEmpty())
            {
                relatedList.getSelectionModel().selectFirst();
            }
        });
    }

    private void loadEvents(String namespace, V1Pod pod)
    {
        setStatus("Loading events...");
        Task<List<CoreV1Event>> task = new Task<>()
        {
            @Override
            protected List<CoreV1Event> call() throws Exception
            {
                return kubernetes.events(namespace);
            }
        };
        task.setOnSucceeded(done -> {
            List<CoreV1Event> loaded = task.getValue();
            String podName = name(pod.getMetadata());
            List<CoreV1Event> matching = loaded.stream().filter(podEvent -> {
                V1ObjectReference ref = podEvent.getInvolvedObject();
                return ref != null && "Pod".equals(ref.getKind()) && podName.equals(ref.getName());
            }).sorted(Comparator.comparing(podEvent -> podEvent.getLastTimestamp() == null ? OffsetDateTime.MIN : podEvent.getLastTimestamp())).toList();
            events.setAll(matching);
            eventsText.setText(matching.isEmpty() ? "No recent Kubernetes complaints for this pod." : matching.stream().map(event -> eventLine(event)).collect(Collectors.joining(System.lineSeparator())));
        });
        task.setOnFailed(failed -> {
            Throwable failure = task.getException();
            if (failure instanceof ApiException apiException && apiException.getCode() == 403)
            {
                events.clear();
                eventsText.setText("""
                        This kube account is not allowed to read Events.

                        Not a disaster. Pods, config, secrets, services, logs, and the main app view can still work.
                        Events are only Kubernetes' recent notes, like scheduling problems, image pull errors, or restart warnings.
                        """);
                setStatus("Events are not permitted for this context; pod data still loaded.");
            }
            else
            {
                events.clear();
                eventsText.setText("Could not load events: " + kubernetes.message(failure));
                setStatus("Could not load events.");
            }
        });
        Thread thread = new Thread(task, "kubot-events-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadLogs(int lines)
    {
        if (selectedNamespace == null || selectedPod == null)
        {
            return;
        }
        requestedLogLines = lines;
        String podName = name(selectedPod.getMetadata());
        String container = containerSelector.getSelectionModel().getSelectedItem();
        run("Loading logs", () -> kubernetes.logs(selectedNamespace, podName, container, requestedLogLines, null), logs -> {
            rawLogs = logs == null || logs.isBlank() ? "(no logs returned)" : logs;
            applyLogFilter();
            setStatus("Loaded last " + requestedLogLines + " lines for " + podName);
        });
    }

    private void applyLogFilter()
    {
        String filter = logFilter == null || logFilter.getText() == null ? "" : logFilter.getText().trim().toLowerCase();
        if (filter.isEmpty())
        {
            logsText.setText(rawLogs);
            return;
        }
        String filtered = rawLogs.lines().filter(line -> line.toLowerCase().contains(filter)).collect(Collectors.joining(System.lineSeparator()));
        logsText.setText(filtered.isEmpty() ? "(no matching log lines)" : filtered);
    }

    private <T> void run(String label, ThrowingSupplier<T> supplier, java.util.function.Consumer<T> onSuccess)
    {
        setStatus(label + "...");
        Task<T> task = new Task<>()
        {
            @Override
            protected T call() throws Exception
            {
                return supplier.get();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> setStatus(kubernetes.message(task.getException())));
        Thread thread = new Thread(task, "kubot-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void setStatus(String message)
    {
        if (Platform.isFxApplicationThread())
        {
            statusLabel.setText(message);
        }
        else
        {
            Platform.runLater(() -> statusLabel.setText(message));
        }
    }

    private String buildOverview(V1Pod pod)
    {
        return """
                # Pod
                name: %s
                namespace: %s
                phase: %s
                ready: %s
                restarts: %s
                age: %s
                owner: %s

                # Port Forward
                %s

                # YAML
                %s
                """.formatted(name(pod.getMetadata()), selectedNamespace, status(pod).getPhase(), readyContainers(pod), restarts(pod), age(pod.getMetadata()), managedByLabel(pod), portForwardCommand(pod), YamlSupport.dump(pod));
    }

    private String portForwardCommand(V1Pod pod)
    {
        String podName = name(pod.getMetadata());
        String port = "8080";
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null)
        {
            for (var container : pod.getSpec().getContainers())
            {
                if (container.getPorts() != null && !container.getPorts().isEmpty())
                {
                    Integer p = container.getPorts().getFirst().getContainerPort();
                    if (p != null)
                    {
                        port = p.toString();
                        break;
                    }
                }
            }
        }
        String contextFlag = "";
        String selectedContext = contextSelector.getSelectionModel().getSelectedItem();
        if (selectedContext != null && !"(default kube config)".equals(selectedContext))
        {
            contextFlag = " --context " + selectedContext;
        }
        return "kubectl" + contextFlag + " port-forward pod/" + podName + " -n " + selectedNamespace + " " + port + ":" + port;
    }

    private String managedByLabel(V1Pod pod)
    {
        if (pod.getMetadata() == null || pod.getMetadata().getOwnerReferences() == null || pod.getMetadata().getOwnerReferences().isEmpty())
        {
            return "Standalone pod";
        }
        return pod.getMetadata().getOwnerReferences().stream().map(ownerRef -> managedByLabel(ownerRef)).collect(Collectors.joining(", "));
    }

    private String managedByLabel(V1OwnerReference owner)
    {
        if (owner == null || owner.getKind() == null || owner.getName() == null)
        {
            return "Unknown owner";
        }
        if ("ReplicaSet".equals(owner.getKind()))
        {
            return deploymentForReplicaSet(owner.getName()).map(deployment -> "Deployment " + deployment).orElse("ReplicaSet " + simplifyReplicaSetName(owner.getName()));
        }
        return owner.getKind() + " " + owner.getName();
    }

    private java.util.Optional<String> deploymentForReplicaSet(String replicaSetName)
    {
        return currentLookup.replicaSets().stream().filter(replicaSet -> replicaSet.getMetadata() != null && Objects.equals(replicaSet.getMetadata().getName(), replicaSetName)).flatMap(replicaSet -> replicaSet.getMetadata().getOwnerReferences() == null ? java.util.stream.Stream.empty() : replicaSet.getMetadata().getOwnerReferences().stream()).filter(owner -> "Deployment".equals(owner.getKind()) && owner.getName() != null).map(owner -> owner.getName()).findFirst().or(() -> guessDeploymentName(replicaSetName));
    }

    private java.util.Optional<String> guessDeploymentName(String replicaSetName)
    {
        if (replicaSetName == null)
        {
            return java.util.Optional.empty();
        }
        return currentLookup.deployments().stream().map(deployment -> deployment.getMetadata()).filter(metadata -> metadata != null).map(metadata -> metadata.getName()).filter(name -> name != null).filter(name -> replicaSetName.startsWith(name + "-")).max(Comparator.comparingInt(name -> name.length()));
    }

    private String simplifyReplicaSetName(String replicaSetName)
    {
        return guessDeploymentName(replicaSetName).orElse(replicaSetName);
    }

    private String eventLine(CoreV1Event event)
    {
        OffsetDateTime time = event.getLastTimestamp() == null ? event.getEventTime() : event.getLastTimestamp();
        return "%s %-8s %-20s %s".formatted(time == null ? "" : time.toString(), event.getType() == null ? "" : event.getType(), event.getReason() == null ? "" : event.getReason(), event.getMessage() == null ? "" : event.getMessage());
    }

    private String readyContainers(V1Pod pod)
    {
        V1PodStatus status = status(pod);
        int total = pod.getSpec() == null || pod.getSpec().getContainers() == null ? 0 : pod.getSpec().getContainers().size();
        long ready = 0;
        if (status.getContainerStatuses() != null)
        {
            ready = status.getContainerStatuses().stream().filter(cs -> Boolean.TRUE.equals(cs.getReady())).count();
        }
        return ready + "/" + total;
    }

    private int restarts(V1Pod pod)
    {
        V1PodStatus status = status(pod);
        if (status.getContainerStatuses() == null)
        {
            return 0;
        }
        return status.getContainerStatuses().stream().mapToInt(cs -> cs.getRestartCount() == null ? 0 : cs.getRestartCount()).sum();
    }

    private String podDisplayStatus(V1Pod pod)
    {
        return dangerousWaitingReason(pod).orElseGet(() -> {
            String phase = status(pod).getPhase();
            if ("Running".equals(phase) && !isFullyReady(pod))
            {
                return "Not Ready";
            }
            return phase == null || phase.isBlank() ? "Unknown" : phase;
        });
    }

    private String podStatusSeverity(V1Pod pod)
    {
        String phase = status(pod).getPhase();
        if (dangerousWaitingReason(pod).isPresent() || "Failed".equals(phase))
        {
            return "status-error";
        }
        if ("Unknown".equals(phase))
        {
            return "status-unknown";
        }
        if ("Pending".equals(phase) || ("Running".equals(phase) && !isFullyReady(pod)))
        {
            return "status-warning";
        }
        return "";
    }

    private boolean isFullyReady(V1Pod pod)
    {
        V1PodStatus status = status(pod);
        int total = pod.getSpec() == null || pod.getSpec().getContainers() == null ? 0 : pod.getSpec().getContainers().size();
        long ready = status.getContainerStatuses() == null ? 0 : status.getContainerStatuses().stream().filter(containerStatus -> Boolean.TRUE.equals(containerStatus.getReady())).count();
        return total > 0 && ready == total;
    }

    private java.util.Optional<String> dangerousWaitingReason(V1Pod pod)
    {
        V1PodStatus status = status(pod);
        if (status.getContainerStatuses() == null)
        {
            return java.util.Optional.empty();
        }
        return status.getContainerStatuses().stream().map(cs -> cs.getState()).filter(state -> state != null).map(state -> state.getWaiting() == null ? null : state.getWaiting().getReason()).filter(reason -> reason != null).filter(reason -> Set.of("CrashLoopBackOff", "ImagePullBackOff", "ErrImagePull", "CreateContainerConfigError", "CreateContainerError", "RunContainerError", "InvalidImageName").contains(reason)).findFirst();
    }

    private V1PodStatus status(V1Pod pod)
    {
        return pod.getStatus() == null ? new V1PodStatus() : pod.getStatus();
    }

    private List<String> containerNames(V1Pod pod)
    {
        if (pod.getSpec() == null || pod.getSpec().getContainers() == null)
        {
            return List.of();
        }
        return pod.getSpec().getContainers().stream().map(container -> container.getName()).filter(name -> name != null).toList();
    }

    private String age(V1ObjectMeta metadata)
    {
        if (metadata == null || metadata.getCreationTimestamp() == null)
        {
            return "";
        }
        Duration duration = Duration.between(metadata.getCreationTimestamp(), OffsetDateTime.now());
        long days = duration.toDays();
        if (days > 0)
        {
            return days + "d";
        }
        long hours = duration.toHours();
        if (hours > 0)
        {
            return hours + "h";
        }
        long minutes = duration.toMinutes();
        return Math.max(0, minutes) + "m";
    }

    private String name(V1ObjectMeta metadata)
    {
        return metadata == null || metadata.getName() == null ? "" : metadata.getName();
    }

    private boolean isSystemNamespace(String namespace)
    {
        return namespace != null && (SYSTEM_NAMESPACES.contains(namespace) || namespace.startsWith("kube-") || namespace.endsWith("-system") || namespace.endsWith("-operator"));
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T>
    {
        T get() throws Exception;
    }

}
