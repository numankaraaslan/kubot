# Kubot

Kubot is a small JavaFX desktop viewer for Kubernetes clusters.

It is intentionally not an admin console. It is for people who want to see what is running without memorizing `kubectl` commands.

## What It Shows

- Kubernetes contexts from your local kubeconfig
- Namespaces, sorted with normal namespaces first and system namespaces last
- Pods in the selected namespace
- Pod status, readiness, restarts, age, and what manages the pod
- Related app wiring:
  - main app workload, such as Deployment
  - Services that route traffic to the pod
  - ConfigMaps used as environment/config
  - Secrets used as environment/files
  - internal Kubernetes rollout objects, such as ReplicaSets
- Pod events when the current account is allowed to read them
- Container logs with selectable row count: `100`, `200`, `400`, `1000`

## What It Does Not Do

Kubot is read-focused.

It does not create, update, delete, scale, restart, exec, port-forward, or mutate Kubernetes resources.

## Requirements

- Windows, macOS, or Linux with Java installed
- Maven if building from source
- A kubeconfig file readable by the Kubernetes Java client

On Windows, kubeconfig is usually:

```text
C:\Users\<you>\.kube\config
```

Kubot reads the same config used by:

```powershell
kubectl config get-contexts
```

## Run From Source

```powershell
mvn javafx:run
```

## Build And Run The Fat JAR

On Windows:

```powershell
.\run.bat
```

The script runs:

```powershell
mvn clean package -Dmaven.test.skip=true
javaw -jar target\Kubot.jar
```

The generated JAR is:

```text
target\Kubot.jar
```

If Windows has `.jar` files associated with Java, the JAR can also be opened directly.

## Kubernetes Setup

If `kubectl` already works on your machine, Kubot should show the same contexts.

To inspect contexts:

```powershell
kubectl config get-contexts
```

To switch your terminal context:

```powershell
kubectl config use-context <context-name>
```

Kubot does not require the terminal context to be active. You choose the context in the app.

## Permissions

Kubot works best when the selected Kubernetes user can read:

- namespaces
- pods
- pod logs
- services
- configmaps
- secrets
- events
- deployments
- replicasets
- statefulsets
- daemonsets
- jobs
- cronjobs
- ingresses

Limited accounts are okay. Kubot tries to keep working and show friendly messages when some resources are forbidden by RBAC.

For namespace-scoped accounts that cannot list namespaces, Kubot falls back to the namespace stored in the kube context.

## Example Read-Only Viewer RBAC

This is useful for a personal K3s/VPS cluster where a service account already manages demo pods in one namespace, but Kubot should read the whole cluster.

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: kubot-cluster-viewer
rules:
- apiGroups: [""]
  resources: ["namespaces", "pods", "pods/log", "services", "configmaps", "secrets", "events"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["apps"]
  resources: ["deployments", "replicasets", "statefulsets", "daemonsets"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["batch"]
  resources: ["jobs", "cronjobs"]
  verbs: ["get", "list", "watch"]
- apiGroups: ["networking.k8s.io"]
  resources: ["ingresses"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: trial-manager-kubot-cluster-viewer
subjects:
- kind: ServiceAccount
  name: trial-manager
  namespace: trials
roleRef:
  kind: ClusterRole
  name: kubot-cluster-viewer
  apiGroup: rbac.authorization.k8s.io
```

## Mental Model

Kubot is organized around the question:

```text
What is running, what owns it, what config feeds it, and what do the logs say?
```

The main flow is:

```text
Context -> Namespace -> Pod -> Related config/secrets/network/logs/events
```

## Project Notes

- JavaFX UI is built programmatically. No FXML.
- Main launcher class: `dev.kubot.Main`
- JavaFX application class: `dev.kubot.KubotApp`
- Kubernetes client wrapper: `dev.kubot.service.KubernetesFacade`
- Related-resource explanation logic: `dev.kubot.service.RelationResolver`
- Styling: `src/main/resources/dev/kubot/kubot.css`

## License

MIT. Open source, use it, change it, ship it. No warranty.
