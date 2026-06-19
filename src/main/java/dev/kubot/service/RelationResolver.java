package dev.kubot.service;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.kubot.model.RelatedResource;
import dev.kubot.model.ResourceLookup;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1Volume;

public class RelationResolver
{
    public List<RelatedResource> resolve(V1Pod pod, ResourceLookup lookup)
    {
        if (pod == null)
        {
            return List.of();
        }
        List<RelatedResource> related = new ArrayList<>();
        addOwners(pod, lookup, related);
        addConfigReferences(pod, lookup, related);
        addMatchingServices(pod, lookup, related);
        addMatchingIngresses(pod, lookup, related);
        return related.stream().sorted(Comparator.comparingInt((RelatedResource r) -> r.sortPriority()).thenComparing(r -> r.kind()).thenComparing(r -> r.name())).toList();
    }

    public String decodedSecretData(V1Secret secret)
    {
        if (secret == null || secret.getData() == null || secret.getData().isEmpty())
        {
            return "(no data)";
        }
        return secret.getData().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + " = " + decode(entry.getValue())).collect(Collectors.joining(System.lineSeparator()));
    }

    private void addOwners(V1Pod pod, ResourceLookup lookup, List<RelatedResource> related)
    {
        List<V1OwnerReference> owners = ownerReferences(pod.getMetadata());
        for (V1OwnerReference owner : owners)
        {
            addOwner(owner, lookup, related);
            if ("ReplicaSet".equals(owner.getKind()))
            {
                findReplicaSet(owner.getName(), lookup).flatMap(rs -> ownerReferences(rs.getMetadata()).stream().filter(ref -> "Deployment".equals(ref.getKind())).findFirst()).ifPresent(deploymentOwner -> addOwner(deploymentOwner, lookup, related));
            }
        }
    }

    private void addOwner(V1OwnerReference owner, ResourceLookup lookup, List<RelatedResource> related)
    {
        if (owner == null || owner.getKind() == null || owner.getName() == null)
        {
            return;
        }
        Object resource = switch (owner.getKind())
        {
        case "ReplicaSet" -> findReplicaSet(owner.getName(), lookup).orElse(null);
        case "Deployment" -> lookup.deployments().stream().filter(v -> named(v.getMetadata(), owner.getName())).findFirst().orElse(null);
        case "StatefulSet" -> lookup.statefulSets().stream().filter(v -> named(v.getMetadata(), owner.getName())).findFirst().orElse(null);
        case "DaemonSet" -> lookup.daemonSets().stream().filter(v -> named(v.getMetadata(), owner.getName())).findFirst().orElse(null);
        case "Job" -> lookup.jobs().stream().filter(v -> named(v.getMetadata(), owner.getName())).findFirst().orElse(null);
        case "CronJob" -> lookup.cronJobs().stream().filter(v -> named(v.getMetadata(), owner.getName())).findFirst().orElse(null);
        default -> null;
        };
        related.add(new RelatedResource("owner", owner.getKind(), owner.getName(), resource == null ? "Owner reference found; resource was not available." : "Pod is owned by this workload.", resource == null ? "" : YamlSupport.dump(resource)));
    }

    private Optional<io.kubernetes.client.openapi.models.V1ReplicaSet> findReplicaSet(String name, ResourceLookup lookup)
    {
        return lookup.replicaSets().stream().filter(v -> named(v.getMetadata(), name)).findFirst();
    }

    private void addConfigReferences(V1Pod pod, ResourceLookup lookup, List<RelatedResource> related)
    {
        V1PodSpec spec = pod.getSpec();
        if (spec == null)
        {
            return;
        }
        if (spec.getVolumes() != null)
        {
            for (V1Volume volume : spec.getVolumes())
            {
                if (volume.getConfigMap() != null && volume.getConfigMap().getName() != null)
                {
                    addConfigMap("volume", volume.getConfigMap().getName(), "Mounted as volume " + volume.getName(), lookup, related);
                }
                V1SecretVolumeSource secret = volume.getSecret();
                if (secret != null && secret.getSecretName() != null)
                {
                    String detail = "Mounted as volume " + volume.getName();
                    if (secret.getItems() != null && !secret.getItems().isEmpty())
                    {
                        detail += "; keys: " + secret.getItems().stream().map(item -> item.getKey()).filter(key -> key != null).collect(Collectors.joining(", "));
                    }
                    addSecret("volume", secret.getSecretName(), detail, lookup, related);
                }
            }
        }
        if (spec.getContainers() != null)
        {
            for (V1Container container : spec.getContainers())
            {
                addContainerConfigReferences(container, lookup, related);
            }
        }
        if (spec.getInitContainers() != null)
        {
            for (V1Container container : spec.getInitContainers())
            {
                addContainerConfigReferences(container, lookup, related);
            }
        }
    }

    private void addContainerConfigReferences(V1Container container, ResourceLookup lookup, List<RelatedResource> related)
    {
        String containerName = container.getName() == null ? "(unnamed container)" : container.getName();
        if (container.getEnv() != null)
        {
            for (V1EnvVar env : container.getEnv())
            {
                V1EnvVarSource source = env.getValueFrom();
                if (source == null)
                {
                    continue;
                }
                if (source.getConfigMapKeyRef() != null && source.getConfigMapKeyRef().getName() != null)
                {
                    addConfigMap("env", source.getConfigMapKeyRef().getName(), containerName + " env " + env.getName() + " from key " + source.getConfigMapKeyRef().getKey(), lookup, related);
                }
                if (source.getSecretKeyRef() != null && source.getSecretKeyRef().getName() != null)
                {
                    addSecret("env", source.getSecretKeyRef().getName(), containerName + " env " + env.getName() + " from key " + source.getSecretKeyRef().getKey(), lookup, related);
                }
            }
        }
        if (container.getEnvFrom() != null)
        {
            for (V1EnvFromSource envFrom : container.getEnvFrom())
            {
                if (envFrom.getConfigMapRef() != null && envFrom.getConfigMapRef().getName() != null)
                {
                    addConfigMap("envFrom", envFrom.getConfigMapRef().getName(), containerName + " imports all keys", lookup, related);
                }
                if (envFrom.getSecretRef() != null && envFrom.getSecretRef().getName() != null)
                {
                    addSecret("envFrom", envFrom.getSecretRef().getName(), containerName + " imports all keys", lookup, related);
                }
            }
        }
    }

    private void addMatchingServices(V1Pod pod, ResourceLookup lookup, List<RelatedResource> related)
    {
        Map<String, String> labels = pod.getMetadata() == null ? null : pod.getMetadata().getLabels();
        if (labels == null || labels.isEmpty())
        {
            return;
        }
        for (V1Service service : lookup.services())
        {
            Map<String, String> selector = service.getSpec() == null ? null : service.getSpec().getSelector();
            if (selector != null && !selector.isEmpty() && labels.entrySet().containsAll(selector.entrySet()))
            {
                related.add(new RelatedResource("service selector", "Service", name(service.getMetadata()), "Service selector matches this pod's labels: " + selector, YamlSupport.dump(service)));
            }
        }
    }

    private void addMatchingIngresses(V1Pod pod, ResourceLookup lookup, List<RelatedResource> related)
    {
        Map<String, String> labels = pod.getMetadata() == null ? null : pod.getMetadata().getLabels();
        if (labels == null || labels.isEmpty())
        {
            return;
        }
        List<String> matchingServiceNames = new ArrayList<>();
        for (V1Service service : lookup.services())
        {
            Map<String, String> selector = service.getSpec() == null ? null : service.getSpec().getSelector();
            if (selector != null && !selector.isEmpty() && labels.entrySet().containsAll(selector.entrySet()))
            {
                matchingServiceNames.add(name(service.getMetadata()));
            }
        }
        if (matchingServiceNames.isEmpty())
        {
            return;
        }
        for (V1Ingress ingress : lookup.ingresses())
        {
            if (ingress.getSpec() == null || ingress.getSpec().getRules() == null)
            {
                continue;
            }
            for (V1IngressRule rule : ingress.getSpec().getRules())
            {
                if (rule.getHttp() == null || rule.getHttp().getPaths() == null)
                {
                    continue;
                }
                for (V1HTTPIngressPath path : rule.getHttp().getPaths())
                {
                    if (path.getBackend() == null || path.getBackend().getService() == null)
                    {
                        continue;
                    }
                    String backendServiceName = path.getBackend().getService().getName();
                    if (backendServiceName != null && matchingServiceNames.contains(backendServiceName))
                    {
                        String host = rule.getHost() == null ? "(no host)" : rule.getHost();
                        String ingressPath = path.getPath() == null ? "/" : path.getPath();
                        String detail = "Routes " + host + ingressPath + " to Service " + backendServiceName;
                        related.add(new RelatedResource("ingress", "Ingress", name(ingress.getMetadata()), detail, YamlSupport.dump(ingress)));
                        return;
                    }
                }
            }
        }
    }

    private void addConfigMap(String source, String name, String detail, ResourceLookup lookup, List<RelatedResource> related)
    {
        V1ConfigMap configMap = lookup.configMaps().stream().filter(v -> named(v.getMetadata(), name)).findFirst().orElse(null);
        related.add(new RelatedResource(source, "ConfigMap", name, configMap == null ? detail + "; ConfigMap was not available." : detail, configMap == null ? "" : YamlSupport.dump(configMap)));
    }

    private void addSecret(String source, String name, String detail, ResourceLookup lookup, List<RelatedResource> related)
    {
        V1Secret secret = lookup.secrets().stream().filter(v -> named(v.getMetadata(), name)).findFirst().orElse(null);
        String decoded = secret == null ? "" : System.lineSeparator() + System.lineSeparator() + "# decoded secret data" + System.lineSeparator() + decodedSecretData(secret);
        related.add(new RelatedResource(source, "Secret", name, secret == null ? detail + "; Secret was not available." : detail, secret == null ? "" : YamlSupport.dump(secret) + decoded));
    }

    private String decode(byte[] value)
    {
        if (value == null)
        {
            return "";
        }
        try
        {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(value)).toString();
        }
        catch (CharacterCodingException ex)
        {
            return Base64.getEncoder().encodeToString(value);
        }
    }

    private List<V1OwnerReference> ownerReferences(V1ObjectMeta metadata)
    {
        return metadata == null || metadata.getOwnerReferences() == null ? List.of() : metadata.getOwnerReferences();
    }

    private boolean named(V1ObjectMeta metadata, String expected)
    {
        return metadata != null && Objects.equals(metadata.getName(), expected);
    }

    private String name(V1ObjectMeta metadata)
    {
        return metadata == null || metadata.getName() == null ? "(unnamed)" : metadata.getName();
    }
}
