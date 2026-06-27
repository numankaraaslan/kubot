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
kubectl the heck is this pod --help
kubectl logs whatever somehwere stuff
kubectl get configthingy ...
kubectl get national secrets ...
```

You just want to click the thing and see what feeds it.

## 👀 What Kubot Shows

- Kubernetes contexts from your local kubeconfig
- Namespaces, with normal/user namespaces first and system-ish ones colored red
- Pods in the selected namespace
- Pod status, readiness, restarts, age, and what manages the pod
- **Live CPU and memory usage** per pod, color-coded by load (green / orange / red), with a timestamp so you know how fresh the data is — requires `metrics-server` in the cluster
- Human-ish related resource explanations:
  - main app workload, such as Deployment
  - Services that route traffic to the pod
  - Ingresses that expose the pod to external URLs
  - ConfigMaps used as environment/config
  - Secrets used as environment/files
  - internal Kubernetes rollout objects, such as ReplicaSets
- Pod events when the current account is allowed to read them
- Container logs with selectable row count: `100`, `200`, `400`, `1000`
- Copy-pasteable `kubectl port-forward` commands for quick local access
- Node name and pod IP in the pod overview

## 🚫 What Kubot Does Not Do

Kubot is read-focused.

It does **not** create, update, delete, scale, restart, exec, port-forward, or mutate Kubernetes resources.

In other words: it looks. It does not poke.

## 🧰 Requirements

- **JDK 25 or newer**
  - Recommended download: [Eclipse Temurin JDK](https://adoptium.net/temurin/releases/)
  - Check it with: `java --version`
- **Apache Maven**
  - Install guide: [maven.apache.org/install.html](https://maven.apache.org/install.html)
  - Check it with: `mvn --version`
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

That means this repo currently expects you to bring Java + Maven, then run the script for your OS.

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

## 📦 Releases Later

Professional-ish open source projects usually grow through these stages:

1. **Source only**: clone the repo, install the tools, build it yourself. That is Kubot today.
2. **GitHub Releases**: each version gets a release page with downloadable files.
3. **Prebuilt app files**: Windows `.exe` / `.msi`, macOS `.dmg`, Linux `.deb` / `.rpm` / `.AppImage`, or at least a ready-made `.jar`.
4. **Automated builds**: GitHub Actions builds the app for every release tag so the files are reproducible and not handmade on one laptop.

For Kubot, the sensible next step is probably simple:

```text
v0.1.0 release -> attach Kubot.jar -> users still need Java installed
```

Later, if this becomes less potato and more serious, we can package a real desktop installer that bundles Java too.

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
- `metrics.k8s.io` (optional — needed for live CPU/RAM usage; Kubot shows "not available" gracefully if metrics-server is not installed)

Limited accounts are okay. Kubot tries to keep working and show friendly messages when RBAC says "nope."

For namespace-scoped accounts that cannot list namespaces, Kubot falls back to the namespace stored in the kube context.

## 🧠 Mental Model

Kubot is organized around this question:

```text
What is running, what owns it, what config feeds it, and what do the logs say?
```

The app tries to translate Kubernetes objects into more human labels:

```text
Deployment -> "main app"
Service    -> "network entry"
Ingress    -> "external URL that routes traffic to the pod"
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
- Pod metrics model: `dev.kubot.model.PodMetrics`
- Styling: `src/main/resources/dev/kubot/kubot.css`

## 📜 License

MIT. Open source, use it, change it, ship it. No warranty.

Human translation: use freely, but if your cluster starts screaming, or your secrets leaked, responsibility is not included in the package.
