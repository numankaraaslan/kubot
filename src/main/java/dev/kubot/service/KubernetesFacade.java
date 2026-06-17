package dev.kubot.service;

import dev.kubot.model.ResourceLookup;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KubernetesFacade {
    private ApiClient client;
    private CoreV1Api core;
    private AppsV1Api apps;
    private BatchV1Api batch;
    private String currentNamespace = "default";

    public List<String> contexts() throws IOException {
        KubeConfig kubeConfig = loadKubeConfig();
        if (kubeConfig == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        String current = kubeConfig.getCurrentContext();
        if (current != null && !current.isBlank()) {
            names.add(current);
        }
        for (Object item : kubeConfig.getContexts()) {
            if (item instanceof Map<?, ?> map) {
                Object name = map.get("name");
                if (name instanceof String value && !value.isBlank()) {
                    names.add(value);
                }
            }
        }
        return new ArrayList<>(names);
    }

    public void connect(String context) throws IOException {
        KubeConfig kubeConfig = loadKubeConfig();
        if (kubeConfig == null) {
            client = Config.defaultClient();
            currentNamespace = "default";
        } else {
            if (context != null && !context.isBlank()) {
                kubeConfig.setContext(context);
            }
            currentNamespace = namespaceForCurrentContext(kubeConfig);
            client = Config.fromConfig(kubeConfig);
        }
        client.setReadTimeout(0);
        Configuration.setDefaultApiClient(client);
        core = new CoreV1Api(client);
        apps = new AppsV1Api(client);
        batch = new BatchV1Api(client);
    }

    public List<V1Namespace> namespaces() throws ApiException {
        ensureConnected();
        try {
            return safeList(() -> core.listNamespace().execute().getItems());
        } catch (ApiException ex) {
            if (ex.getCode() == 403) {
                return List.of(new V1Namespace().metadata(new V1ObjectMeta().name(currentNamespace)));
            }
            throw ex;
        }
    }

    public String currentNamespace() {
        return currentNamespace;
    }

    public List<V1Pod> pods(String namespace) throws ApiException {
        ensureConnected();
        return safeList(() -> core.listNamespacedPod(namespace).execute().getItems());
    }

    public List<CoreV1Event> events(String namespace) throws ApiException {
        ensureConnected();
        return safeList(() -> core.listNamespacedEvent(namespace).execute().getItems());
    }

    public ResourceLookup lookup(String namespace) {
        ensureConnectedUnchecked();
        return new ResourceLookup(
                quiet(() -> core.listNamespacedConfigMap(namespace).execute().getItems()),
                quiet(() -> core.listNamespacedSecret(namespace).execute().getItems()),
                quiet(() -> core.listNamespacedService(namespace).execute().getItems()),
                quiet(() -> apps.listNamespacedReplicaSet(namespace).execute().getItems()),
                quiet(() -> apps.listNamespacedDeployment(namespace).execute().getItems()),
                quiet(() -> apps.listNamespacedStatefulSet(namespace).execute().getItems()),
                quiet(() -> apps.listNamespacedDaemonSet(namespace).execute().getItems()),
                quiet(() -> batch.listNamespacedJob(namespace).execute().getItems()),
                quiet(() -> batch.listNamespacedCronJob(namespace).execute().getItems())
        );
    }

    public String logs(String namespace, String pod, String container, int lines, OffsetDateTime since) throws ApiException {
        ensureConnected();
        var call = core.readNamespacedPodLog(pod, namespace)
                .tailLines(lines)
                .timestamps(true);
        if (container != null && !container.isBlank()) {
            call.container(container);
        }
        if (since != null) {
            long seconds = Math.max(1, Duration.between(since, OffsetDateTime.now()).toSeconds());
            call.sinceSeconds((int) seconds);
        }
        return call.execute();
    }

    public String message(Throwable throwable) {
        if (throwable instanceof ApiException apiException) {
            String body = apiException.getResponseBody();
            if (apiException.getCode() == 403) {
                return "RBAC denied this request: " + firstUseful(body, apiException.getMessage());
            }
            return "Kubernetes API error " + apiException.getCode() + ": " + firstUseful(body, apiException.getMessage());
        }
        return throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
    }

    private KubeConfig loadKubeConfig() throws IOException {
        for (File file : kubeConfigFiles()) {
            if (file.isFile()) {
                try (FileReader reader = new FileReader(file)) {
                    return KubeConfig.loadKubeConfig(reader);
                }
            }
        }
        return null;
    }

    private List<File> kubeConfigFiles() {
        String configured = System.getenv("KUBECONFIG");
        List<File> files = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            for (String part : configured.split(File.pathSeparator)) {
                if (!part.isBlank()) {
                    files.add(new File(part));
                }
            }
        }
        files.add(new File(System.getProperty("user.home"), ".kube" + File.separator + "config"));
        return files;
    }

    private String namespaceForCurrentContext(KubeConfig kubeConfig) {
        String currentContext = kubeConfig.getCurrentContext();
        for (Object item : kubeConfig.getContexts()) {
            if (item instanceof Map<?, ?> map && currentContext.equals(map.get("name"))) {
                Object context = map.get("context");
                if (context instanceof Map<?, ?> contextMap) {
                    Object namespace = contextMap.get("namespace");
                    if (namespace instanceof String value && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return "default";
    }

    private <T> List<T> safeList(ApiListCall<T> call) throws ApiException {
        List<T> items = call.execute();
        return items == null ? List.of() : items;
    }

    private <T> List<T> quiet(ApiListCall<T> call) {
        try {
            List<T> items = call.execute();
            return items == null ? List.of() : items;
        } catch (ApiException ex) {
            return Collections.emptyList();
        }
    }

    private void ensureConnected() {
        if (core == null) {
            throw new IllegalStateException("Not connected to a kube context yet.");
        }
    }

    private void ensureConnectedUnchecked() {
        ensureConnected();
    }

    private String firstUseful(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.length() > 240 ? first.substring(0, 240) + "..." : first;
        }
        return fallback == null ? "" : fallback;
    }

    @FunctionalInterface
    private interface ApiListCall<T> {
        List<T> execute() throws ApiException;
    }
}
