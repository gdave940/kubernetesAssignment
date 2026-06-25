# NAGP Kubernetes Assignment 

A Spring Boot REST microservice that manages a simple **Inventory** backed by a **MySQL** database, fully containerized with **Docker** and deployed to **Kubernetes** using a Deployment (with rolling updates and horizontal autoscaling), a MySQL StatefulSet with persistent storage, ConfigMaps, Secrets, Services and an Ingress.

| Resource | Link |
| --- | --- |
| 📦 GitHub Repository | https://github.com/gdave940/kubernetesAssignment |
| 🐳 Docker Image | https://hub.docker.com/r/gdave940/nagp-kubernetes |

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [REST API](#rest-api)
- [Configuration](#configuration)
- [Prerequisites](#prerequisites)
- [Run Locally](#run-locally)
- [Build & Push the Docker Image](#build--push-the-docker-image)
- [Provision the GKE Cluster](#provision-the-gke-cluster)
- [Deploy to Kubernetes](#deploy-to-kubernetes)
- [Test the Deployment](#test-the-deployment)
- [Kubernetes Resources Explained](#kubernetes-resources-explained)
- [Cleanup](#cleanup)

---

## Overview

This project exposes a small Inventory API that lets you **add**, **list**, and **delete** inventory items. Every item is identified by its name (the primary key). The application returns a consistent JSON envelope (`ApiResponse`) for both success and error cases, with centralized exception handling.

It is designed to demonstrate a production-style Kubernetes deployment:

- Stateless application tier (`Deployment`) scaled horizontally via an `HorizontalPodAutoscaler`.
- Stateful database tier (`StatefulSet`) with a `PersistentVolumeClaim` for durable storage.
- Externalized configuration through a `ConfigMap` and sensitive data through a `Secret`.
- Internal service discovery via `ClusterIP` and headless Services, with external access via an `Ingress`.

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 (Web MVC, Data JPA, Validation, Actuator) |
| Build Tool | Maven (wrapper included — `mvnw`) |
| Persistence | MySQL 8.0 + Hibernate/JPA |
| Boilerplate | Lombok |
| Container | Docker (`eclipse-temurin:17-jre`) |
| Orchestration | Kubernetes |

---

## Architecture

```
                              ┌─────────────────────────────┐
        Internet  ───────────▶│          Ingress            │
                              │      (nagp-app-ingress)     │
                              └──────────────┬──────────────┘
                                             │  /
                                             ▼
                              ┌─────────────────────────────┐
                              │     Service (ClusterIP)      │
                              │     nagp-app-service :80     │
                              └──────────────┬──────────────┘
                                             │  ─▶ 8080
                  ┌──────────────────────────┼──────────────────────────┐
                  ▼                          ▼                          ▼
          ┌──────────────┐          ┌──────────────┐          ┌──────────────┐
          │  nagp-app    │          │  nagp-app    │   ...    │  nagp-app    │
          │   Pod (1)    │          │   Pod (2)    │          │  Pod (4..8)  │   ◀── HPA (CPU 50%)
          └──────┬───────┘          └──────┬───────┘          └──────┬───────┘
                 └──────────────────────────┼──────────────────────────┘
                                            │  jdbc:mysql://mysql-service:3306
                                            ▼
                              ┌─────────────────────────────┐
                              │     Service (ClusterIP)      │
                              │      mysql-service :3306     │
                              └──────────────┬──────────────┘
                                             ▼
                              ┌─────────────────────────────┐
                              │     StatefulSet: mysql       │
                              │     (mysql:8.0) + PVC 2Gi    │
                              └─────────────────────────────┘

   Config:  mysql-config (ConfigMap)  ──▶ DB_URL, DB_USER, MYSQL_DATABASE
   Secret:  mysql-secret (Secret)     ──▶ DB_PASSWORD / MYSQL_ROOT_PASSWORD
```

**Application layering:** `Controller → Service → Repository (Spring Data JPA) → MySQL`

---

## Project Structure

```
kubernetesAssignment/
├── Dockerfile                          # Builds the runtime image from the packaged JAR
├── pom.xml                             # Maven build & dependencies
├── mvnw / mvnw.cmd                     # Maven wrapper
├── src/main/java/NAGP/kubernetesAssignment/
│   ├── KubernetesAssignmentApplication.java   # Spring Boot entry point
│   ├── controller/
│   │   ├── HomeController.java                 # GET / health-style landing endpoint
│   │   └── InventoryController.java            # /v1/inventory REST endpoints
│   ├── service/InventoryService.java           # Business logic
│   ├── repository/InventoryRepository.java     # Spring Data JPA repository
│   ├── entity/Inventory.java                   # JPA entity (table: inventory)
│   ├── model/ApiResponse.java                  # Generic JSON response envelope
│   └── exception/
│       ├── ItemNotFoundException.java          # Thrown when an item is missing
│       └── CustomExceptionHandler.java         # @ControllerAdvice handler
├── src/main/resources/application.properties   # DB & JPA configuration (env-driven)
└── k8s/
    ├── config/configMap.yaml           # mysql-config (DB name, user, JDBC URL)
    ├── secret/mysql-secret.yaml        # mysql-secret (root password, base64)
    ├── mysql/
    │   ├── mysql-statefulset.yaml      # MySQL StatefulSet + PVC template
    │   ├── mysql-service.yaml          # MySQL ClusterIP Service
    │   └── mysql-headless-service.yaml # MySQL headless Service (stable network ID)
    └── app/
        ├── app-deployment.yaml         # App Deployment (4 replicas, RollingUpdate)
        ├── app-service.yaml            # App ClusterIP Service
        ├── app-hpa.yaml                # HorizontalPodAutoscaler (4–8 pods, CPU 50%)
        └── app-ingress.yaml            # Ingress routing / → app service
```

---

## REST API

Base path: `/v1/inventory`

| Method | Endpoint | Description | Success Status |
| --- | --- | --- | --- |
| `GET` | `/` | Landing/health check — returns a running message | `200 OK` |
| `POST` | `/v1/inventory/{itemName}` | Add a new inventory item | `201 Created` |
| `GET` | `/v1/inventory` | List all inventory items (sorted by name) | `200 OK` |
| `DELETE` | `/v1/inventory/{itemName}` | Delete an inventory item by name | `200 OK` |

> `itemName` must contain at least one letter or number (validated with the pattern `.*[a-zA-Z0-9].*`).

### Response envelope (`ApiResponse`)

All inventory endpoints return a consistent JSON structure:

```json
{
  "message": "Created",
  "status": true,
  "timestamp": "2026-06-25 10:15:30.123",
  "data": [
    { "item": "apple" },
    { "item": "banana" }
  ]
}
```

### Example requests

```bash
# Landing endpoint
curl http://localhost:8080/

# Add an item
curl -X POST http://localhost:8080/v1/inventory/apple

# List all items
curl http://localhost:8080/v1/inventory

# Delete an item
curl -X DELETE http://localhost:8080/v1/inventory/apple
```

Deleting a non-existent item returns `404 Not Found`:

```json
{
  "message": "Item with name 'apple' not found.",
  "status": false,
  "timestamp": "2026-06-25 10:16:00.000",
  "data": null
}
```

---

## Configuration

The application reads its database settings from environment variables (see `src/main/resources/application.properties`):

| Property | Environment Variable | Source in Kubernetes |
| --- | --- | --- |
| `spring.datasource.url` | `DB_URL` | `mysql-config` ConfigMap → `MYSQL_DATABASE_URL` |
| `spring.datasource.username` | `DB_USER` | `mysql-config` ConfigMap → `MYSQL_DATABASE_USER` |
| `spring.datasource.password` | `DB_PASSWORD` | `mysql-secret` Secret → `MYSQL_ROOT_PASSWORD` |
| `spring.jpa.hibernate.ddl-auto` | — | `update` (schema auto-managed) |

**ConfigMap (`k8s/config/configMap.yaml`)**

| Key | Value |
| --- | --- |
| `MYSQL_DATABASE` | `nagp_kubernetes` |
| `MYSQL_DATABASE_USER` | `root` |
| `MYSQL_DATABASE_URL` | `jdbc:mysql://mysql-service:3306/nagp_kubernetes?allowPublicKeyRetrieval=true&useSSL=false` |

**Secret (`k8s/secret/mysql-secret.yaml`)**

| Key | Value (base64) | Decoded |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | `cm9vdDEyMw==` | `root123` |

> ⚠️ The committed secret value is for demonstration only. Use a real secrets manager (or `kubectl create secret`) for production.

---

## Prerequisites

- **JDK 17**
- **Maven** (or use the bundled `./mvnw` wrapper)
- **Docker**
- A running **Kubernetes** cluster (this assignment uses **Google Kubernetes Engine (GKE)**; Minikube, Kind or Docker Desktop also work)
- **kubectl** configured to talk to your cluster
- For GKE: the **Google Cloud SDK** (`gcloud`) authenticated to your GCP project with the **Kubernetes Engine API** enabled
- For autoscaling: the **metrics-server** add-on must be enabled (enabled by default on GKE)
- For ingress: an **Ingress controller** (e.g. NGINX) must be installed (GKE provides a built-in ingress via `HttpLoadBalancing`)

---

## Run Locally

1. Start a local MySQL instance and create the database `nagp_kubernetes` (or update the values below).
2. Provide the database environment variables and run the app:

```bash
export DB_URL="jdbc:mysql://localhost:3306/nagp_kubernetes?allowPublicKeyRetrieval=true&useSSL=false"
export DB_USER="root"
export DB_PASSWORD="Admin@123"

./mvnw spring-boot:run
```

The service starts on **http://localhost:8080**.

To build a runnable JAR:

```bash
./mvnw clean package
java -jar target/*.jar
```

---

## Build & Push the Docker Image

The `Dockerfile` packages the pre-built JAR onto a lightweight JRE base image:

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

Build the JAR first, then build and push the image:

```bash
# 1. Build the application JAR
./mvnw clean package

# 2. Build the Docker image
docker build -t gdave940/nagp-kubernetes:latest .

# 3. (optional) Run it locally
docker run -p 8080:8080 \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/nagp_kubernetes?allowPublicKeyRetrieval=true&useSSL=false" \
  -e DB_USER="root" \
  -e DB_PASSWORD="Admin@123" \
  gdave940/nagp-kubernetes:latest

# 4. Push to Docker Hub
docker login
docker push gdave940/nagp-kubernetes:latest
```

Image on Docker Hub: **https://hub.docker.com/r/gdave940/nagp-kubernetes**

---

## Provision the GKE Cluster

This assignment was deployed on **Google Kubernetes Engine (GKE)**. The cluster was created with the following `gcloud` command:

```bash
gcloud container clusters create nagp-k8-cluster \
  --zone asia-south1-a \
  --machine-type=e2-standard-2 \
  --num-nodes=2 \
  --logging=NONE \
  --monitoring=NONE \
  --addons=HttpLoadBalancing
```

**Flag breakdown:**

| Flag | Value | Purpose |
| --- | --- | --- |
| *cluster name* | `nagp-k8-cluster` | Name of the GKE cluster. |
| `--zone` | `asia-south1-a` | Zonal cluster located in the Mumbai (`asia-south1`) region. |
| `--machine-type` | `e2-standard-2` | Each node has 2 vCPUs and 8 GB RAM. |
| `--num-nodes` | `2` | Provisions 2 worker nodes in the node pool. |
| `--logging` | `NONE` | Disables Cloud Logging to reduce cost. |
| `--monitoring` | `NONE` | Disables Cloud Monitoring to reduce cost. |
| `--addons` | `HttpLoadBalancing` | Enables the HTTP(S) Load Balancing add-on that backs the GKE Ingress. |

After the cluster is created, fetch its credentials so `kubectl` targets it:

```bash
# Point kubectl at the new GKE cluster
gcloud container clusters get-credentials nagp-k8-cluster --zone asia-south1-a

# Verify connectivity
kubectl get nodes
```

> Make sure `gcloud` is authenticated (`gcloud auth login`) and the active project is set (`gcloud config set project <PROJECT_ID>`) with the **Kubernetes Engine API** enabled before running the command above.

---

## Deploy to Kubernetes

Apply the manifests in order so that configuration and the database exist before the app starts.

```bash
# 1. Configuration & secrets
kubectl apply -f k8s/config/configMap.yaml
kubectl apply -f k8s/secret/mysql-secret.yaml

# 2. Database tier (StatefulSet + Services)
kubectl apply -f k8s/mysql/mysql-headless-service.yaml
kubectl apply -f k8s/mysql/mysql-service.yaml
kubectl apply -f k8s/mysql/mysql-statefulset.yaml

# 3. Application tier (Deployment + Service + HPA + Ingress)
kubectl apply -f k8s/app/app-deployment.yaml
kubectl apply -f k8s/app/app-service.yaml
kubectl apply -f k8s/app/app-hpa.yaml
kubectl apply -f k8s/app/app-ingress.yaml
```

Or apply everything at once (Kubernetes resolves dependencies as resources become ready):

```bash
kubectl apply -R -f k8s/
```

Verify the rollout:

```bash
kubectl get pods
kubectl get svc
kubectl get statefulset
kubectl get hpa
kubectl get ingress
```

---

## Test the Deployment

**Via Ingress** (replace with your Ingress host/IP):

```bash
curl http://<INGRESS_HOST>/
curl -X POST http://<INGRESS_HOST>/v1/inventory/apple
curl http://<INGRESS_HOST>/v1/inventory
```

**Via port-forward** (no Ingress controller needed):

```bash
kubectl port-forward svc/nagp-app-service 8080:80
curl http://localhost:8080/v1/inventory
```

**On Minikube:**

```bash
minikube addons enable ingress
minikube addons enable metrics-server
minikube tunnel   # if needed for ingress/LoadBalancer access
```

---

## Kubernetes Resources Explained

| File | Kind | Purpose |
| --- | --- | --- |
| `k8s/config/configMap.yaml` | `ConfigMap` | Non-sensitive DB config (name, user, JDBC URL) consumed by both tiers. |
| `k8s/secret/mysql-secret.yaml` | `Secret` | MySQL root password (base64), injected as `DB_PASSWORD` / `MYSQL_ROOT_PASSWORD`. |
| `k8s/mysql/mysql-statefulset.yaml` | `StatefulSet` | Single MySQL 8.0 replica with a `volumeClaimTemplate` (2Gi PVC) for durable storage at `/var/lib/mysql`. |
| `k8s/mysql/mysql-service.yaml` | `Service` (ClusterIP) | Stable in-cluster endpoint `mysql-service:3306` used by the app. |
| `k8s/mysql/mysql-headless-service.yaml` | `Service` (Headless) | `clusterIP: None` — provides stable network identity for the StatefulSet. |
| `k8s/app/app-deployment.yaml` | `Deployment` | 4 replicas of `gdave940/nagp-kubernetes:latest`; `RollingUpdate` (maxSurge 1, maxUnavailable 1); CPU/memory requests & limits; env wired from ConfigMap & Secret. |
| `k8s/app/app-service.yaml` | `Service` (ClusterIP) | Exposes the app internally on port `80` → container `8080`. |
| `k8s/app/app-hpa.yaml` | `HorizontalPodAutoscaler` | Scales the Deployment between **4 and 8** pods targeting **50% CPU** utilization. |
| `k8s/app/app-ingress.yaml` | `Ingress` | Routes external HTTP traffic on `/` to `nagp-app-service:80`. |

### Highlights

- **Rolling updates** keep the service available during deployments.
- **Horizontal autoscaling** reacts to CPU load (requires metrics-server).
- **Persistent storage** ensures MySQL data survives pod restarts/rescheduling.
- **12-factor configuration** — all environment-specific values come from ConfigMaps/Secrets, not from the image.

---

## Cleanup

```bash
# Remove everything created from the k8s manifests
kubectl delete -R -f k8s/

# Note: deleting the StatefulSet does not delete its PVC.
# To remove persisted MySQL data as well:
kubectl delete pvc -l app=mysql
```

---

## Author

**gdave940** — NAGP Kubernetes Assignment

- GitHub: https://github.com/gdave940/kubernetesAssignment
- Docker Hub: https://hub.docker.com/r/gdave940/nagp-kubernetes
