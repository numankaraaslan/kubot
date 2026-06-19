package dev.kubot.model;

import java.util.List;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1StatefulSet;

public record ResourceLookup(List<V1ConfigMap> configMaps, List<V1Secret> secrets, List<V1Service> services, List<V1ReplicaSet> replicaSets, List<V1Deployment> deployments, List<V1StatefulSet> statefulSets, List<V1DaemonSet> daemonSets, List<V1Job> jobs, List<V1CronJob> cronJobs)
{
    public static ResourceLookup empty()
    {
        return new ResourceLookup(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
