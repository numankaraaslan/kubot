package dev.kubot.model;

public record RelatedResource(
        String source,
        String kind,
        String name,
        String detail,
        String yaml
) {
    public String label() {
        return category() + " | " + kind + " " + name + " - " + humanDetail();
    }

    public String category() {
        if ("service selector".equals(source) && "Service".equals(kind)) {
            return "Network";
        }
        if ("ConfigMap".equals(kind)) {
            return "volume".equals(source) ? "Config file" : "Config";
        }
        if ("Secret".equals(kind)) {
            return "Secrets";
        }
        if ("owner".equals(source)) {
            return "ReplicaSet".equals(kind) ? "Internal" : "Main app";
        }
        return "Related";
    }

    public int sortPriority() {
        if ("Main app".equals(category())) {
            return 10;
        }
        if ("Network".equals(category())) {
            return 20;
        }
        if ("Config".equals(category())) {
            return 30;
        }
        if ("Config file".equals(category())) {
            return 35;
        }
        if ("Secrets".equals(category())) {
            return 40;
        }
        if ("Internal".equals(category())) {
            return 90;
        }
        return 70;
    }

    private String humanDetail() {
        if ("service selector".equals(source) && "Service".equals(kind)) {
            return "other apps use this to reach the pod";
        }
        if ("ConfigMap".equals(kind) && "envFrom".equals(source)) {
            return "loads all values as environment variables";
        }
        if ("ConfigMap".equals(kind) && "env".equals(source)) {
            return "provides one environment variable";
        }
        if ("ConfigMap".equals(kind) && "volume".equals(source)) {
            return "mounted into the container as files";
        }
        if ("Secret".equals(kind) && "envFrom".equals(source)) {
            return "loads all secret values as environment variables";
        }
        if ("Secret".equals(kind) && "env".equals(source)) {
            return "provides one secret environment variable";
        }
        if ("Secret".equals(kind) && "volume".equals(source)) {
            return "mounted into the container as secret files";
        }
        if ("owner".equals(source) && "Deployment".equals(kind)) {
            return "main app definition that manages this pod";
        }
        if ("owner".equals(source) && "ReplicaSet".equals(kind)) {
            return "Kubernetes rollout copy; usually not important";
        }
        if ("owner".equals(source)) {
            return "workload that manages this pod";
        }
        return detail;
    }
}
