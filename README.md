# Kubot

**Kubernetes is complicated. Kubot is for people who just want to see what is going on.**

Kubot is a small JavaFX desktop viewer for Kubernetes clusters. It is intentionally **not** a full admin console. It is a friendly "please show me the pods, config, logs, and weird Kubernetes connections without making me memorize `kubectl`" app.

Think of it as a Kubernetes viewer for non-kubetronauts.

```text
Context -> Namespace -> Pod -> Config / Secrets / Network / Events / Logs
```

## 🥔 Why?

Because sometimes you do not want this:

```powershell
kubectl get pods -n something
kubectl describe pod whatever -n something
kubectl logs whatever -n something --tail=200
kubectl get configmap ...
kubectl get secret ...
```

You just want to click the thing and see what feeds it.

## 👀 What Kubot Shows

- Kubernetes contexts from your local kubeconfig
- Namespaces, with normal/user namespaces first and system-ish ones colored red
- Pods in the selected namespace
- Pod status, readiness, restarts, age, and what manages the pod
- Human-ish related resource explanations:
  - main app workload, such as Deployment
  - Services that route traffic to the pod
  - ConfigMaps used as environment/config
  - Secrets used as environment/files
  - internal Kubernetes rollout objects, such as ReplicaSets
- Pod events when the current account is allowed to read them
- Container logs with selectable row count: `100`, `200`, `400`, `1000`

## 🚫 What Kubot Does Not Do

Kubot is read-focused.

It does **not** create, update, delete, scale, restart, exec, port-forward, or mutate Kubernetes resources.

In other words: it looks. It does not poke.

## 🧰 Requirements

- Java installed
- Maven if building from source
- A kubeconfig file readable by the Kubernetes Java client

On Windows, kubeconfig is usually here:

```text
C:\Users\<you>\.kube\config
```

Kubot reads the same config used by:

```powershell
kubectl config get-contexts
```

If `kubectl` already works, Kubot should see the same worlds.

## 🍳 Cook It Yourself

There are no prebuilt releases yet. For now, build it locally.

On Windows:

```powershell
.\run.bat
```

On macOS/Linux:

```sh
chmod +x ./run.sh
./run.sh
```

The scripts build the shaded JAR and run it:

```powershell
mvn clean package -Dmaven.test.skip=true
java/javaw -jar target/Kubot.jar
```

The generated JAR is:

```text
target/Kubot.jar
```

If Windows has `.jar` files associated with Java, the JAR can also be opened directly.

Manual build command:

```powershell
mvn clean package -Dmaven.test.skip=true
```

## 🔌 Kubernetes Setup

To inspect contexts:

```powershell
kubectl config get-contexts
```

To switch your terminal context:

```powershell
kubectl config use-context <context-name>
```

Kubot does not require the terminal context to be active. You choose the context inside the app.

If there is no kubeconfig, Kubot shows a setup help popup instead of failing mysteriously.

## 🔐 Permissions

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

Limited accounts are okay. Kubot tries to keep working and show friendly messages when RBAC says "nope."

For namespace-scoped accounts that cannot list namespaces, Kubot falls back to the namespace stored in the kube context.

## 🪪 Example Read-Only Viewer RBAC

Useful for a personal K3s/VPS cluster where a service account manages demo pods in one namespace, but Kubot should read the whole cluster.

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

## 🧠 Mental Model

Kubot is organized around this question:

```text
What is running, what owns it, what config feeds it, and what do the logs say?
```

The app tries to translate Kubernetes objects into more human labels:

```text
Deployment -> "main app"
Service    -> "network entry"
ConfigMap  -> "config"
Secret     -> "secrets"
ReplicaSet -> "internal rollout copy, usually not important"
```

## 🛠️ Project Notes

- JavaFX UI is built programmatically. No FXML.
- Main launcher class: `dev.kubot.Main`
- JavaFX application class: `dev.kubot.KubotApp`
- Kubernetes client wrapper: `dev.kubot.service.KubernetesFacade`
- Related-resource explanation logic: `dev.kubot.service.RelationResolver`
- Styling: `src/main/resources/dev/kubot/kubot.css`

## 📜 License

MIT. Open source, use it, change it, ship it. No warranty.

Human translation: use freely, but if your cluster starts screaming, responsibility is not included in the package.
