package dev.kubot.model;

import java.util.List;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1StatefulSet;

public class ResourceLookup
{
    private List<V1ConfigMap> configMaps = List.of();
    private List<V1Secret> secrets = List.of();
    private List<V1Service> services = List.of();
    private List<V1Ingress> ingresses = List.of();
    private List<V1ReplicaSet> replicaSets = List.of();
    private List<V1Deployment> deployments = List.of();
    private List<V1StatefulSet> statefulSets = List.of();
    private List<V1DaemonSet> daemonSets = List.of();
    private List<V1Job> jobs = List.of();
    private List<V1CronJob> cronJobs = List.of();

    public List<V1ConfigMap> configMaps() { return configMaps; }
    public void setConfigMaps(List<V1ConfigMap> configMaps) { this.configMaps = configMaps; }

    public List<V1Secret> secrets() { return secrets; }
    public void setSecrets(List<V1Secret> secrets) { this.secrets = secrets; }

    public List<V1Service> services() { return services; }
    public void setServices(List<V1Service> services) { this.services = services; }

    public List<V1Ingress> ingresses() { return ingresses; }
    public void setIngresses(List<V1Ingress> ingresses) { this.ingresses = ingresses; }

    public List<V1ReplicaSet> replicaSets() { return replicaSets; }
    public void setReplicaSets(List<V1ReplicaSet> replicaSets) { this.replicaSets = replicaSets; }

    public List<V1Deployment> deployments() { return deployments; }
    public void setDeployments(List<V1Deployment> deployments) { this.deployments = deployments; }

    public List<V1StatefulSet> statefulSets() { return statefulSets; }
    public void setStatefulSets(List<V1StatefulSet> statefulSets) { this.statefulSets = statefulSets; }

    public List<V1DaemonSet> daemonSets() { return daemonSets; }
    public void setDaemonSets(List<V1DaemonSet> daemonSets) { this.daemonSets = daemonSets; }

    public List<V1Job> jobs() { return jobs; }
    public void setJobs(List<V1Job> jobs) { this.jobs = jobs; }

    public List<V1CronJob> cronJobs() { return cronJobs; }
    public void setCronJobs(List<V1CronJob> cronJobs) { this.cronJobs = cronJobs; }
}
