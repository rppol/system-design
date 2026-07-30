# Deploy & cloud — technology bank

<!-- tech-bank tier: platform-delivery -->

The 212 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Deploy & cloud** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### ACR
**Short:** Azure Container Registry: managed OCI registry with IAM-backed access, geo-replication and scan-on-push.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @3, platform-delivery/cloud-platform-and-cost @3

### act
**Short:** CLI that runs GitHub Actions workflows locally in Docker, so pipeline changes can be tested without pushing.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, devtools/testing-and-mocking @3

### adk deploy cloud_run
**Short:** Google ADK command that containerizes an agent and deploys it to Cloud Run in a single step.
**Kind:** api
**Lang:** python
**Roles:** platform-delivery/cloud-platform-and-cost @1, llm-apps/agent-framework @2, platform-delivery/container-and-image @3

### AKS
**Short:** Azure Kubernetes Service: a managed Kubernetes control plane with cloud-integrated nodes, identity and networking.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

Azure runs and upgrades the Kubernetes control plane — API server, etcd, scheduler — and you manage only node pools, so what you get is conformant upstream Kubernetes with the control-plane operations removed. Nodes are VM scale sets that autoscale, and the cluster wires into the surrounding Azure services: Entra ID as the identity source for RBAC subjects, Azure CNI or kubenet for pod networking, managed disks and Azure Files for volumes.

Reach for it when you are already on Azure and want real Kubernetes rather than a proprietary container service. It removes the control-plane ops, not the need to understand Kubernetes itself.

### Amazon ECS/Fargate
**Short:** AWS container orchestrator, with Fargate running tasks serverlessly so there are no nodes to manage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @2, platform-delivery/cloud-platform-and-cost @2

### Ansible
**Short:** Agentless configuration management and orchestration driven by YAML playbooks over SSH.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

### Ansible AWX
**Short:** Web UI and API over Ansible adding job scheduling, inventories, credential storage, RBAC and run history.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/ci-cd-and-release @3

### Anthropic Message Batches
**Short:** Anthropic API mode that submits many messages for asynchronous processing at roughly half the price of live calls.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, data-movement/batch-and-distributed-compute @2, llm-apps/llm-gateway-and-routing @3

### ApplicationSets
**Short:** Argo CD controller and CRD that templates one Application definition across many clusters, environments or repo paths.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

### ARC
**Short:** Actions Runner Controller: a Kubernetes operator that autoscales self-hosted GitHub Actions runners as pods.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

### Argo CD
**Short:** GitOps controller that continuously reconciles a Kubernetes cluster to the manifests declared in Git.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

### Argo Rollouts
**Short:** Kubernetes controller for progressive delivery: canary and blue-green rollouts gated by automated metric analysis.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

It replaces the `Deployment` with a `Rollout` resource that owns the same pod template but walks a declared strategy: shift five percent of traffic, pause, run an `AnalysisTemplate` that queries Prometheus, Datadog, CloudWatch or a job, and abort automatically if error rate or latency crosses the threshold. That automated gate is the whole difference from a rolling update, which only knows whether pods became ready, not whether they are serving correctly. Weighted traffic shifting needs an ingress or mesh that can do it, such as NGINX, Istio, Gateway API or an ALB, so confirm that before planning a canary. It pairs naturally with Argo CD, since a Rollout is just another manifest in Git.

### ArgoCD
**Short:** Pull-based GitOps controller that reconciles Kubernetes clusters against manifests in git.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

An `Application` resource names a git repo, a path and a revision; the controller renders whatever it finds there (plain YAML, Helm, Kustomize), diffs it against live cluster state, and either reports the drift or reconciles it away. Because it pulls from inside the cluster, CI never holds cluster credentials — it only pushes a commit — and git history becomes the deploy audit trail.

Use it when desired state should be reviewable and revertible like code, with the app-of-apps pattern when one root Application bootstraps many. It fits badly anything git cannot describe: imperative one-off jobs, and secrets, which need a sealed-secret or external-secret layer since the manifests live in a repository.

### ArgoCD Image Updater
**Short:** Argo CD companion that watches a registry and writes new image tags back to Git so GitOps reconciles the deploy.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/container-and-image @3

### Artifact Hub
**Short:** CNCF catalogue for discovering and verifying public Helm charts, operators and other cloud-native packages.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @3

### Athena billing export
**Short:** Querying AWS cost and usage report files with Athena SQL for line-item level spend analysis and chargeback.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, data-stores/warehouse-and-olap @3

### Atlantis
**Short:** Runs Terraform plan and apply from pull-request comments, with state locking and an audit trail.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/ci-cd-and-release @2

Atlantis is a webhook server wired to your VCS: opening a pull request that touches Terraform makes it run `terraform plan` and post the output as a PR comment, and a reviewer types `atlantis apply` to run the apply from the server and get the result in the same thread. It locks the workspace while a PR is open, so two concurrent PRs cannot plan against the same state and surprise each other.

The value is that credentials live on the Atlantis host, not on engineers' laptops — nobody needs production cloud keys locally, the plan everyone reviewed is exactly the plan that runs, and every apply has an audit trail attached to a reviewed change. The corollary is that Atlantis holds the keys to your infrastructure and must be treated as production-grade infrastructure itself: locked down, patched, and with its VCS webhook authenticated.

### Auto Scaling
**Short:** AWS service that adds and removes compute instances against target metrics or schedules to track demand.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/kubernetes-and-orchestration @3

### AWS Budgets
**Short:** AWS service setting per-team spend budgets and firing alerts or actions when forecast or actual cost breaches them.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

### AWS CDK
**Short:** AWS Cloud Development Kit: define infrastructure in TypeScript, Python, Java or Go and synthesize CloudFormation.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/compiler-toolchain-and-codegen @3

### AWS CloudFormation
**Short:** AWS-native infrastructure as code: declarative YAML/JSON stacks with managed change sets, drift detection and rollback.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

### AWS Compute Optimizer
**Short:** AWS service that recommends rightsizing and waste reduction from observed resource utilization.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### AWS Cost Explorer
**Short:** AWS console and API for spend visibility: cost breakdowns, trends, forecasts and anomaly alerts.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

### AWS ECR
**Short:** AWS-managed OCI container registry with IAM-based access control, image scanning on push and lifecycle rules.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/cloud-platform-and-cost @2

### AWS ECS
**Short:** AWS container orchestrator that schedules tasks and services onto EC2 or Fargate without running Kubernetes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @2, platform-delivery/cloud-platform-and-cost @3

### AWS IPAM
**Short:** AWS IP Address Manager: plans, allocates and audits VPC CIDR blocks across accounts so ranges never overlap.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

### AWS Lambda
**Short:** AWS function-as-a-service: event-triggered, per-request-billed compute with no server management.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### AWS Lambda SnapStart
**Short:** Lambda feature that snapshots an initialized runtime and restores it per invocation, cutting JVM-style cold starts.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/runtime-internals-and-types @3, observability/profiling-and-performance @3

### AWS Parameter Store
**Short:** SSM Parameter Store: hierarchical application config and SecureString secrets, cheaper than Secrets Manager.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, security/secrets-and-cryptography @2, apis-frameworks/dependency-injection-and-config @3

### AWS SAM
**Short:** Serverless Application Model: shorthand CloudFormation plus a CLI for building, testing and deploying Lambda apps.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @2, platform-delivery/ci-cd-and-release @3

### AWS SDK
**Short:** AWS client libraries for every major language, with credential resolution, adaptive retries and backoff built in.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, traffic-edge/rate-limiting-and-resilience @2

### AWS VPC
**Short:** AWS virtual private cloud: private IP ranges, subnets, route tables, security groups and NAT.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

### AWS VPC CNI
**Short:** EKS CNI plugin giving pods real VPC IPs and enforcing security-group and network policy.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, runtime-systems/io-networking-and-syscalls @3

### AWS Well-Architected Tool
**Short:** AWS console workflow for reviewing a workload against the Well-Architected pillars and tracking remediation items.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/infrastructure-as-code-and-config @3

### az CLIs
**Short:** The Azure command-line interface for creating, inspecting and scripting cloud resources from a shell.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/infrastructure-as-code-and-config @3

### Azure Budgets
**Short:** Azure Cost Management feature setting per-team or per-subscription spend budgets with threshold alerts and actions.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

### Azure Container Apps
**Short:** Azure's serverless container platform: run containers with scale-to-zero and KEDA autoscaling, no cluster to manage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/container-and-image @2, platform-delivery/kubernetes-and-orchestration @3

### Azure Files CSI
**Short:** Kubernetes CSI driver mounting Azure Files shares as ReadWriteMany volumes for pods needing a shared filesystem.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-stores/object-and-file-storage @2

### Azure Functions
**Short:** Microsoft Azure serverless function-as-a-service runtime with event triggers and consumption-based billing.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, apis-frameworks/web-framework-and-http-client @3

Azure Functions is Azure's function-as-a-service runtime. A function declares a trigger — HTTP request, timer, queue or Service Bus or Event Hub message, blob creation — plus input and output bindings, and the platform handles connecting to those services, deserializing the payload and scaling instances out as the backlog grows. The Consumption plan bills per execution and gigabyte-second and scales to zero, which is why cold starts appear; Premium and Dedicated plans keep instances warm and add virtual network integration.

Durable Functions layer stateful orchestration on top of the stateless model, so fan-out/fan-in, retries with backoff and waits for human approval survive process restarts by replaying an event history. Reach for it for event-driven glue and spiky workloads. Steady high-throughput or latency-sensitive services are usually cheaper and more predictable on a container platform.
### Azure SDKs
**Short:** Azure's per-language client libraries, with built-in retry, backoff and throttling behaviour for its managed services.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, traffic-edge/rate-limiting-and-resilience @2

### Backstage
**Short:** Open-source developer portal: a plugin-based service catalog, software templates and docs for an internal platform.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/ci-cd-and-release @3

### boto3
**Short:** The AWS SDK for Python: typed clients and resources for S3, DynamoDB, SQS and every other AWS service.
**Kind:** tech
**Lang:** python
**Roles:** platform-delivery/cloud-platform-and-cost @1, data-stores/object-and-file-storage @2

### Buildah
**Short:** Daemonless, rootless OCI image builder that can build from a Containerfile or by scripting layers directly.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

### BuildKit
**Short:** Docker's concurrent image builder with cache mounts and daemonless in-cluster builds for CI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/ci-cd-and-release @3

### Buildkite
**Short:** Hosted CI/CD control plane that orchestrates pipelines while the build agents run on your own infrastructure.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

### buildx
**Short:** Docker's BuildKit-backed builder: cache mounts, multi-stage and multi-architecture image builds.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

### Calico
**Short:** Kubernetes CNI plugin providing Pod networking (BGP or overlay) and enforcing NetworkPolicy at the host.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, runtime-systems/io-networking-and-syscalls @2, security/authorization-and-policy @3, traffic-edge/service-mesh-and-discovery @3

### CDK
**Short:** AWS Cloud Development Kit: define infrastructure in a real programming language, synthesized to CloudFormation.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @2

### CDKTF
**Short:** Cloud Development Kit for Terraform: write infrastructure in TypeScript, Python or Go and synthesize Terraform JSON.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/compiler-toolchain-and-codegen @3

### Chainguard images
**Short:** Minimal, distroless-style container base images rebuilt continuously to keep CVE counts near zero, signed with SBOMs.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @1

### chart-testing
**Short:** CLI (ct) that lints and install-tests changed Helm charts in CI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, devtools/testing-and-mocking @2, devtools/static-analysis-and-linting @2

### Chef
**Short:** Agent-based, pull-model configuration management with procedural Ruby cookbooks.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

### Cilium
**Short:** eBPF-based Kubernetes CNI delivering Pod networking, L3-L7 network policy, load balancing and a sidecar-free mesh.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/service-mesh-and-discovery @2, runtime-systems/io-networking-and-syscalls @2, security/authorization-and-policy @3

### Cilium eBPF
**Short:** eBPF-based Kubernetes CNI and service dataplane, replacing kube-proxy with in-kernel load balancing and policy.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/service-mesh-and-discovery @2, runtime-systems/io-networking-and-syscalls @3

### CircleCI
**Short:** Hosted CI/CD service running build, test and deploy pipelines defined in a YAML config inside the repository.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

### client-go
**Short:** Official Kubernetes Go client with informers, listers and workqueues for building controllers and operators.
**Kind:** tech
**Lang:** go
**Roles:** platform-delivery/kubernetes-and-orchestration @1

### Cloud Functions
**Short:** Google Cloud's function-as-a-service: run event-driven or HTTP-triggered code with no server to manage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

You deploy a single function with a trigger, either an HTTP endpoint or an event source such as a Pub/Sub message or an object written to a bucket, and the platform builds the container, routes invocations to it, adds instances as concurrency rises and removes them when traffic stops. Billing follows invocations and the memory-seconds consumed, so an idle service costs nothing. Newer generations run on the same serverless container infrastructure as Cloud Run, which is why the two converged in configuration and limits.

Reach for it for event-driven glue: reacting to a storage event, handling a webhook, running a small scheduled job. What pushes work off it is a cold start budget you cannot meet, a request duration or a memory ceiling you exceed, or a service with steady high traffic, where a long-running container is both cheaper and more predictable.

### Cloud Native Buildpacks
**Short:** Standard for turning source into an OCI image without a Dockerfile: detect, build and rebase reproducible layers.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, devtools/build-and-dependency-management @2, platform-delivery/ci-cd-and-release @3

### Cloud Run
**Short:** Google Cloud's serverless container runtime: request-driven autoscaling from zero with no cluster to manage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/container-and-image @2

### cloud-init
**Short:** First-boot instance bootstrap: applies user-data to configure users, packages, disks and network on cloud VMs.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

### Cloudability
**Short:** Third-party FinOps platform for multi-cloud cost allocation, rightsizing recommendations and IaC cost estimates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/infrastructure-as-code-and-config @3

### CloudFormation
**Short:** AWS-native infrastructure as code: declare stacks of resources in YAML or JSON and let AWS reconcile and roll back.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

### CloudHealth
**Short:** Third-party FinOps platform for multi-cloud cost visibility, allocation, and pre-deploy IaC cost estimates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### CloudNativePG
**Short:** Kubernetes operator that runs PostgreSQL clusters with declarative failover, backups and rolling upgrades.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-access/replication-ha-and-backup @2, data-stores/relational @3

### Cluster Autoscaler
**Short:** Kubernetes add-on that grows and shrinks node groups when pods cannot be scheduled or nodes sit idle.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @3

It watches for pods stuck Pending because no node can fit them and asks the cloud provider to add an instance to a node group whose shape would satisfy them; nodes that stay underutilized for a configured period get drained and removed. Because it works within pre-declared groups, the instance shapes have to be modeled up front, and a heterogeneous fleet - GPU types, spot versus on-demand, architectures - means one group per shape with matching labels and taints.

Scale-down is where it disappoints in practice: a node is not removed if its pods have no controller, use local storage, are blocked by a PodDisruptionBudget, or are kube-system pods without one, so a single stray pod pins an expensive instance indefinitely. Karpenter is the alternative that provisions instance shapes directly from pod requirements instead of choosing among predefined groups.

### Cluster Operator
**Short:** Strimzi's Kubernetes operator that provisions and reconciles Kafka clusters and exports their JMX metrics.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/event-streaming-and-processing @2, observability/metrics-and-monitoring @3

### containerd
**Short:** OCI container runtime handling image pull, snapshots and container lifecycle; the default Kubernetes CRI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/kubernetes-and-orchestration @2

### containers
**Short:** Running untrusted code (such as an MCP server) inside an isolated container as a sandbox boundary.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2, llm-apps/tool-use-and-mcp @2

### controller-runtime
**Short:** Go library for writing Kubernetes controllers and operators: reconcile loops, caches, CRD scaffolding.
**Kind:** tech
**Lang:** go
**Roles:** platform-delivery/kubernetes-and-orchestration @1

It is the library underneath Kubebuilder and the Operator SDK. A Manager owns the shared informer cache, clients and leader election; you register a Reconciler for a resource and it delivers reconcile requests, with rate-limited retries and backoff, whenever that object or an owned or watched object changes. The cached client is what keeps a controller from hammering the API server.

The discipline it forces is level-triggered reconciliation: read the desired state, compare it with the actual state, converge, and be safe to call again -- your function will be invoked repeatedly for the same object and must never assume it is seeing an event exactly once. Reach for it for any Go operator; the alternative is writing informers and workqueues yourself.

### Cost allocation tags
**Short:** Cloud resource tags activated for billing so spend can be attributed to a team, service or environment.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### Cost Anomaly Detection
**Short:** AWS service that models normal spend per account or service and alerts on statistically unusual cost spikes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @2

### crane
**Short:** go-containerregistry CLI to copy, inspect and mutate OCI images in a registry without a Docker daemon.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

### CRD
**Short:** CustomResourceDefinition: registers a new typed object in the Kubernetes API for an operator to reconcile.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

### CRI-O
**Short:** Lightweight OCI container runtime built purely to implement the Kubernetes CRI; the default on OpenShift.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/kubernetes-and-orchestration @2

### crictl
**Short:** CRI-level CLI for inspecting and debugging containerd/CRI-O pods and containers directly on a node.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/kubernetes-and-orchestration @2

### Crossplane
**Short:** Kubernetes control plane that provisions and reconciles cloud infrastructure through CRDs instead of a CLI apply.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/kubernetes-and-orchestration @2, platform-delivery/cloud-platform-and-cost @3

Crossplane installs into a Kubernetes cluster and adds providers whose custom resources represent real cloud infrastructure — a bucket, a database instance, a VPC, an IAM role. You create infrastructure by applying YAML, and a controller then reconciles continuously against that spec, so drift is corrected rather than merely reported at the next plan. The state of the world lives in the cluster's API objects instead of a state file you have to store and lock.

Compositions are what platform teams actually use it for: define one claim such as a managed Postgres instance, and let it expand into the database, subnet group, parameter group, secret and network rules with your organisation's defaults already applied, so a product team requests a database without learning the provider's surface. The tradeoff against Terraform is real in both directions — you gain a live control loop and the Kubernetes RBAC and GitOps ecosystem, and you accept that your infrastructure now depends on a healthy cluster and on provider CRDs keeping up with cloud APIs.
### crun
**Short:** C-based OCI container runtime; a faster, lower-memory drop-in replacement for runc used by Podman and CRI-O.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

### ctr
**Short:** containerd's low-level debugging CLI for inspecting images, containers and namespaces beneath Kubernetes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/kubernetes-and-orchestration @3

### CUDs
**Short:** Google Cloud committed use discounts: lower rates in exchange for a one- or three-year usage commitment.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### Direct Connect
**Short:** AWS dedicated private network link between on-premises data centres and a VPC, bypassing the public internet.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

### distroless
**Short:** Google base images containing only the app and its runtime deps, with no shell or package manager, cutting CVE surface.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2

### dive
**Short:** TUI that explores a container image layer by layer and reports wasted space.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

### Docker
**Short:** Container build and runtime; packages an app plus its dependencies into an image and isolates it at run time.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, llm-apps/agentic-environments @2, platform-delivery/ci-cd-and-release @3

Each Dockerfile instruction produces a cached layer, so instruction order decides rebuild time: copy the dependency manifest and install before copying source, and a code change no longer reinstalls the world. At run time there is no virtual machine, only kernel namespaces for isolation and cgroups for limits, which is why containers start in milliseconds and share the host kernel. Multi-stage builds keep compilers and build caches out of the final image, and running as a non-root user with a read-only filesystem shrinks what a compromise can reach.

Beyond packaging, it is the usual sandbox for executing model-generated or otherwise untrusted code, and that use deserves care: a shared kernel makes a container a weaker boundary than a virtual machine, so untrusted workloads need the network disabled, capabilities dropped, a resource ceiling and ideally a stronger runtime underneath.

### Docker buildx
**Short:** Docker's BuildKit-backed builder adding multi-platform images, remote cache and parallel build stages.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/ci-cd-and-release @3

### Docker Swarm
**Short:** Docker's built-in container orchestrator: simpler than Kubernetes, with services, overlay networks and rolling updates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @3

### docker-slim
**Short:** Watches a container at runtime and rebuilds a minified image of only what was used, shrinking attack surface.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @3

### EC2
**Short:** AWS elastic compute: virtual machines, instance families, auto scaling groups and spot capacity.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### EKS
**Short:** Amazon Elastic Kubernetes Service: AWS-managed Kubernetes control plane with managed and Fargate node groups.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

AWS runs and patches the control plane — API server and etcd, replicated across availability zones — while you supply capacity as managed node groups, Karpenter-provisioned instances, or serverless Fargate pods. The integrations are the reason to choose it: the VPC CNI gives each pod a routable VPC address so security groups apply to pods directly, and pod identity maps a Kubernetes service account onto an IAM role, so workloads get scoped AWS credentials with no static keys.

It fits when you want conformant Kubernetes without owning etcd backups and control-plane upgrades. You still own node upgrades, add-on version skew and a per-cluster control-plane charge, so a single small service is usually better served by ECS, Fargate or Lambda.

### envsubst
**Short:** GNU gettext CLI substituting environment variables into a template file; the simplest config templating step.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/version-control-and-workbench @3

### etcdctl
**Short:** CLI for etcd: inspect keys, check cluster health, and take or restore the snapshot that backs up a control plane.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-access/replication-ha-and-backup @2, data-access/transactions-and-consistency @3

### Feature flags
**Short:** Runtime switches that decouple deploy from release, enabling dark launches, gradual rollout and instant rollback.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

### Firecracker
**Short:** AWS microVM monitor giving VM-grade isolation with ~125ms boot; the substrate under Lambda, Fargate and agent sandboxes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2, llm-apps/agentic-environments @3, platform-delivery/cloud-platform-and-cost @3

### Flagger
**Short:** Kubernetes progressive-delivery operator that shifts traffic for canaries and gates promotion on metrics.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @3, traffic-edge/service-mesh-and-discovery @3

Flagger watches a `Canary` custom resource pointing at a Deployment and a traffic provider -- a service mesh, an ingress controller, or Gateway API -- and drives the rollout itself: it creates the canary and primary services, shifts a small weight of traffic, waits an analysis interval, queries your metrics provider, then either steps the weight up or rolls back. Because promotion is gated on metrics (success rate, latency percentiles, or any custom query you supply) and on webhooks that can run load or conformance tests, a bad release is reverted without a human watching a dashboard at 2am. It also handles blue/green and A/B routing by header or cookie, not only weighted canaries. Reach for it when deployments are already declarative and you want the rollout policy in git beside them; Argo Rollouts is the direct alternative, and a single-replica or stateful workload gains little from either.

### Flagsmith
**Short:** Feature-flag platform for decoupling release from deploy: targeted rollouts, segments and kill switches.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

### Flannel
**Short:** Simple Kubernetes CNI plugin giving every pod a routable IP via a VXLAN or host-gw overlay network.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, runtime-systems/io-networking-and-syscalls @2

### Flux
**Short:** Name collision: Flux CD, the pull-based GitOps controller set for Kubernetes, and FLUX, the text-to-image model.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, applied-ml/vision-speech-and-multimodal @3

Flux CD is a set of Kubernetes controllers that continuously reconcile a cluster toward manifests in a Git or OCI repository, with separate source, kustomize, helm and image-automation controllers, so drift or a manual `kubectl edit` is reverted on the next reconcile. Being pull-based means no CI system needs cluster credentials, since the cluster reaches out instead, which is the main security argument for GitOps. It renders Helm charts and Kustomize overlays natively, and Argo CD is the usual alternative, offering a first-class UI where Flux is more composable and CLI-driven.

FLUX, unrelated except by name, is a family of open-weight text-to-image diffusion transformer models from Black Forest Labs, published in several variants under different licenses. Check which one a document means before reading anything into the word.

### Flux image automation
**Short:** Flux CD controllers that scan a registry for new image tags and commit the bump back to Git.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/container-and-image @3

### Fly.io Machines
**Short:** Fly.io's API-driven microVMs that boot in under a second, used for per-request sandboxes and agent workloads.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, llm-apps/agentic-environments @2

### Gateway API
**Short:** Kubernetes successor to Ingress: role-oriented CRDs for L7 routing, TLS termination and traffic splitting.
**Kind:** spec
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/api-gateway @2, traffic-edge/proxy-and-load-balancer @3

### gcloud
**Short:** Google Cloud's CLI for creating, inspecting and scripting every GCP resource and service.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, devtools/version-control-and-workbench @3

### GCP Budgets
**Short:** Google Cloud budget objects that track per-team spend against a threshold and fire alerts or automation.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

### GCP client libraries
**Short:** Google Cloud's official SDKs, shipping default retry, backoff and deadline behaviour you tune rather than rewrite.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, traffic-edge/rate-limiting-and-resilience @2

### GHCR
**Short:** GitHub Container Registry: OCI registry wired to repo permissions and Actions tokens.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/ci-cd-and-release @3

### GitHub Actions
**Short:** GitHub's hosted CI/CD service: YAML workflows on repo events, hosted or autoscaling self-hosted runners.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

A workflow is YAML committed under `.github/workflows`, triggered by repository events - push, pull request, release, schedule, manual dispatch, or another workflow - and made of jobs that run on GitHub-hosted runners or on self-hosted runners you scale yourself. Reuse comes from marketplace actions, composite actions and reusable workflows; OIDC federation lets a job assume a cloud role for a few minutes instead of storing long-lived access keys as secrets.

The security model is where teams get hurt. A third-party action runs with your workflow token, so pin it to a commit SHA rather than a moving tag; `pull_request_target` and workflows triggered by forks execute in a privileged context and are the classic escalation path; and concurrency groups are what stop two deploys of the same environment overlapping.

### GitLab CI
**Short:** All-in-one platform bundling Git hosting, YAML-defined CI/CD pipelines and a container/package registry.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, devtools/version-control-and-workbench @2, platform-delivery/container-and-image @3

Pipelines are declared in a `.gitlab-ci.yml` in the repository, so the build changes in the same merge request as the code; jobs are grouped into stages, run on runners you host or GitLab hosts, and pass artifacts and caches between each other. Because the same project also holds issues, merge requests, the container registry and environment/deployment tracking, a release is traceable end to end without integrating separate tools.

Reach for it when you want one platform rather than stitching source control, CI and a registry together. Self-managing GitLab is a real operational commitment; the SaaS tier avoids that but ties you to their runners and quotas.

### GitLab Registry
**Short:** Container and package registry built into GitLab projects, sharing their permissions and CI credentials.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/ci-cd-and-release @2

### GKE
**Short:** Google Kubernetes Engine: managed Kubernetes control plane and node pools on GCP.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

Google runs and upgrades the control plane and etcd; you choose between Standard mode, where you manage node pools, machine types, and autoscaling, and Autopilot, where Google manages nodes and bills per pod resource request. Release channels decide how aggressively your cluster tracks new Kubernetes versions, and node auto-upgrade and auto-repair keep the fleet current.

The integration is the real reason to use it over self-managed Kubernetes on GCP: Workload Identity maps a Kubernetes service account to a Google service account so pods get cloud credentials without static keys, Ingress provisions Cloud Load Balancing, and logs and metrics land in Cloud Operations by default. Reach for it whenever you are on GCP and want Kubernetes without owning control-plane operations.

### Goldilocks
**Short:** Fairwinds tool that runs VPA in recommendation mode and dashboards suggested CPU/memory requests per workload.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @3

### Google Artifact Registry
**Short:** Google Cloud OCI and language-package registry with IAM controls and scan-on-push.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, devtools/build-and-dependency-management @2

### GPU instances
**Short:** Cloud accelerator VM families (AWS p4d/p5/g5, GCP a2/a3/g2) that rent A100/H100/L4 capacity for training and inference.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, ml-lifecycle/ml-platform-and-pipelines @3, gpu/multi-gpu-and-collectives @3

### Harbor
**Short:** Self-hosted OCI registry adding vulnerability scanning, signing policy, RBAC and cross-registry replication.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2

Harbor is a registry you run yourself, storing OCI images and other OCI artifacts such as Helm charts, with projects as the unit of RBAC and quota. On top of plain storage it adds the controls a regulated pipeline needs: vulnerability scanning on push with a policy that can block pulling an image with a critical CVE, signature verification so unsigned images are refused, tag immutability and retention rules with garbage collection, and replication to or from another registry.

That replication is what makes it practical in air-gapped or multi-region setups — mirror upstream images inward so builds do not depend on a public registry's availability or rate limits, and push outward to a regional registry so nodes pull locally. Reach for it when images must stay inside your network, or when a managed registry's policy controls are not enough.
### Helm
**Short:** Kubernetes package manager: templated, versioned chart releases of manifests with values-driven overrides.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/infrastructure-as-code-and-config @2, platform-delivery/ci-cd-and-release @3

A chart is a directory of templated manifests plus a `values.yaml` of defaults. Installing one renders the templates against your overrides and records the result as a named release with revision history, so `helm rollback` restores the previous revision without you having kept the old YAML anywhere. That gives you a single versioned artifact for an application that is really a dozen objects — Deployment, Service, Ingress, ConfigMap, ServiceAccount, HPA.

The standing complaint is that templating YAML with a text templating engine is fragile and indentation bugs surface as invalid manifests. Run `helm template` and review the rendered output rather than trusting the values file alone.

### Helm 3
**Short:** Kubernetes package manager: templated charts, versioned releases, upgrade and rollback of a whole application.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/ci-cd-and-release @2, devtools/build-and-dependency-management @3

### helm-diff
**Short:** Helm plugin that renders the diff between the released manifests and a pending upgrade.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/ci-cd-and-release @2

### HPA
**Short:** Kubernetes HorizontalPodAutoscaler object that scales replica count from CPU or custom metrics such as queue latency.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, observability/metrics-and-monitoring @3

### Humanitec
**Short:** Commercial platform orchestrator for internal developer platforms: workload specs resolved per environment.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/ci-cd-and-release @2, platform-delivery/infrastructure-as-code-and-config @2

### Infracost
**Short:** Estimates the monthly cost delta of a Terraform change and posts it as a comment on the pull request.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/infrastructure-as-code-and-config @2, platform-delivery/ci-cd-and-release @3

It parses a Terraform plan, matches the resources against cloud price lists, and prints the monthly cost of the change -- previous total, new total, and the diff -- for every resource whose price it can determine. In CI it posts that breakdown as a pull-request comment, so cost review happens where the change is reviewed rather than on a bill six weeks later, and a policy can fail the build when the diff exceeds a threshold. The honest limitation is usage-based pricing: data transfer, S3 requests and Lambda invocations have no cost until you declare expected usage in a usage file, so an estimate is a floor for anything not billed purely by the hour. Reach for it the moment infrastructure changes are made by people who never see the bill.

### ingress
**Short:** The Kubernetes object declaring external HTTP routing, TLS and canary traffic weights for a controller to implement.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/proxy-and-load-balancer @2, traffic-edge/api-gateway @3

### Jenkins
**Short:** Self-hosted, plugin-extensible CI/CD server running declarative or scripted pipelines on dynamic agents.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

A pipeline is code, a `Jenkinsfile` in the repository written declaratively or as scripted Groovy, and the controller schedules its stages onto agents that can be long-lived machines or containers provisioned per build by the Kubernetes plugin. Its reach comes from the plugin ecosystem, where essentially every tool, cloud and protocol has an integration, and from the fact that it runs entirely on infrastructure you own, which is often the deciding factor in regulated or air-gapped environments. That same ecosystem is the liability, since plugins are the main source of security advisories and upgrade breakage and the controller becomes a stateful pet nobody wants to touch. For a new project a hosted YAML-configured CI is usually less work; Jenkins earns its place where self-hosting or an unusual integration is mandatory.

### k9s
**Short:** Terminal UI for Kubernetes: browse and edit resources, tail logs and exec into pods far faster than typing kubectl.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, devtools/version-control-and-workbench @3

### Kaniko
**Short:** Builds OCI images inside a cluster without a Docker daemon or privileged access, with layer caching for CI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/ci-cd-and-release @2

Kaniko builds an image from a Dockerfile inside an ordinary unprivileged container. It extracts the base image into its own filesystem, executes each instruction in userspace, and snapshots the changed files as a layer, so there is no Docker daemon, no privileged pod, and no host docker socket mounted into a build job. Removing that socket mount is the security point: anything that can talk to the node's daemon effectively has root on the node, and CI runs untrusted-ish code by definition.

Because there is no daemon there is also no local layer cache between runs, so enable caching to a registry repository or builds get slower, not faster. It is one of several daemonless builders — BuildKit in rootless mode and Buildah solve the same problem with different tradeoffs — so check which one your platform already supports before adopting it.
### Karpenter
**Short:** Kubernetes node autoscaler that provisions just-in-time, right-sized, Spot-aware instances for pending pods.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

Instead of scaling a fixed set of node groups, Karpenter watches for unschedulable pods, reads what they actually ask for — CPU, memory, GPU, architecture, zone, taints — and provisions a matching instance directly through the cloud provider's API, usually in well under a minute. It also works in the other direction: consolidation drains and removes underutilized nodes or replaces them with cheaper ones as workloads shrink, and it drains gracefully when a Spot interruption notice arrives.

Reach for it to cut cluster cost and to stop maintaining a node group per instance shape, which matters most for scarce GPU types where flexibility across instance families is the difference between getting capacity and waiting. The tradeoff is churn: nodes come and go far more often, so PodDisruptionBudgets, sensible termination grace periods and applications that survive being rescheduled stop being optional hygiene and become requirements.

### Kata Containers
**Short:** OCI runtime that boots each container in a lightweight VM, giving hardware isolation for untrusted workloads.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2

### KEDA
**Short:** Kubernetes autoscaler that scales workloads (to zero) off event sources like Kafka lag or queue depth.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/task-queue-and-jobs @3

KEDA adds a `ScaledObject` that reads an external signal — Kafka consumer lag, SQS queue depth, a Prometheus query, a cron schedule — and drives the target Deployment's replica count from it, including down to zero when there is nothing to process. It does this by feeding the Horizontal Pod Autoscaler an external metric, so it composes with the normal Kubernetes scaling machinery rather than replacing it.

Use it for queue and event workers, where CPU is a poor proxy for demand: a consumer sitting 200k messages behind can look completely idle. Scale-to-zero costs a cold start on the next message, so it fits batch and asynchronous work far better than a latency-sensitive request path.

### Knative
**Short:** Kubernetes add-on giving serverless request-driven autoscaling to zero plus an eventing mesh.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

### kopf
**Short:** Python Kubernetes operator framework: decorate handlers on CRD events instead of writing a Go controller.
**Kind:** tech
**Lang:** python
**Roles:** platform-delivery/kubernetes-and-orchestration @1

### kube-proxy
**Short:** Kubernetes node agent that programs iptables/IPVS rules so Service virtual IPs load-balance to pod endpoints.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/service-mesh-and-discovery @2, traffic-edge/proxy-and-load-balancer @2

kube-proxy runs on every node, watches Services and EndpointSlices, and programs kernel packet rules — iptables by default, IPVS for clusters with many services — so a packet sent to a Service's ClusterIP is DNAT'd to one of the ready pod IPs. It also programs NodePort listeners and the node-side half of LoadBalancer Services.

Two consequences explain most Service surprises. The balancing is L4 and per-connection, decided in the kernel on the client's node with no proxy hop, so there is no HTTP awareness and no per-request distribution: a long-lived gRPC or keep-alive connection pins to one pod and stays there regardless of load, which is why teams reach for a service mesh, client-side balancing or a headless Service. And because it is rule programming rather than a data-path process, several CNIs replace it entirely with eBPF, keeping Service semantics while removing the iptables chain-length problem at scale.

### kubeadm
**Short:** Official CLI that bootstraps a conformant self-managed Kubernetes control plane and joins nodes to it.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

### Kubebuilder
**Short:** Scaffolding framework for writing Kubernetes operators in Go: generates CRD manifests and controller boilerplate.
**Kind:** tech
**Lang:** go
**Roles:** platform-delivery/kubernetes-and-orchestration @1, devtools/compiler-toolchain-and-codegen @3

### Kubecost
**Short:** Allocates Kubernetes spend to namespaces, workloads and teams, and flags idle or oversized requests.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/kubernetes-and-orchestration @2

### kubectl
**Short:** The Kubernetes CLI: apply, inspect, exec into and roll out objects against the API server.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

Every subcommand is an authenticated REST call to the API server using the current kubeconfig context, so RBAC constrains you exactly as it would a controller -- and `kubectl auth can-i` will tell you in advance. `apply` performs declarative updates from a manifest, `get -o yaml`, `describe` and `events` explain why an object is stuck, `logs`, `exec` and `port-forward` are the debugging loop, and `rollout status` and `rollout undo` drive a deployment.

Reach for it for everything from a cluster, but prefer applying manifests that live in git over imperative `create`, `edit` and `scale`, which leave the cluster in a state nothing reproduces. Check the context before every destructive command; the usual production incident is the right command in the wrong cluster.

### kubectl describe node
**Short:** kubectl command printing a node's allocatable versus requested resources, taints, conditions and running pods.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, observability/profiling-and-performance @3

### kubectl get endpointslices
**Short:** kubectl query listing the ready pod addresses actually behind a Service, the first step in debugging discovery.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/service-mesh-and-discovery @2

### kubectl get pv,pvc,sc
**Short:** kubectl invocation listing PersistentVolumes, claims and StorageClasses together to see how cluster storage is bound.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-stores/object-and-file-storage @2

### kubectl rollout
**Short:** kubectl subcommand to watch, pause, restart, inspect history and roll back a Deployment's update.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/ci-cd-and-release @2

### KubeRay
**Short:** Kubernetes operator that runs elastic Ray clusters, exposing Ray Train, Serve, Tune and Data as cluster workloads.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/batch-and-distributed-compute @2, ml-lifecycle/ml-platform-and-pipelines @2, model-training/distributed-training @3

### Kubernetes
**Short:** The container orchestrator: declarative workload scheduling, self-healing, service networking and autoscaling.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @2

You declare desired state to the API server — this many replicas of this image, exposed this way — and controllers reconcile reality toward it continuously: a Deployment manages ReplicaSets which manage Pods, the scheduler places Pods by resource requests and constraints, kubelet runs the containers, and Services plus cluster DNS give stable addressing over an unstable set of Pod IPs. Cluster state lives in etcd, and the same reconcile loop underlies rollouts, rollbacks, and self-healing after a node dies.

What it buys is that failure recovery and deployment mechanics stop being scripts you maintain. What it costs is a large operational surface — networking plugins, storage classes, RBAC, resource requests and limits, and a quarterly upgrade cadence. Reach for it when you run enough services that orchestration is a real problem; one small app is better served by a managed container service or a PaaS.

### Kubernetes Deployment
**Short:** Kubernetes workload object managing a ReplicaSet, giving native rolling updates tuned by maxSurge/maxUnavailable.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/ci-cd-and-release @2

### Kubernetes grpc probe
**Short:** Native liveness and readiness probe type calling grpc.health.v1.Health/Check directly, GA since Kubernetes 1.27.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, apis-frameworks/rpc-graphql-and-streaming @2, observability/metrics-and-monitoring @3

### Kubernetes HPA
**Short:** Kubernetes Horizontal Pod Autoscaler: adds or removes replicas to hold a CPU, memory or custom metric target.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

### Kubernetes plugin
**Short:** Jenkins plugin that provisions ephemeral build agents as Kubernetes pods for elastic self-hosted CI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

Configured against a cluster, the plugin turns a pipeline's `agent { kubernetes { yaml ... } }` block into a pod launched for that one build: the containers declared in the pod spec become the tool images individual stages run in, and the pod is deleted when the build ends. That replaces long-lived static agents with capacity that scales to zero, and it kills the drift problem where a build passes only on the one machine that happens to have the right JDK installed. The costs are per-build latency from scheduling and image pulls, and having to think explicitly about workspace persistence and dependency caches across ephemeral pods. Reach for it when a self-hosted Jenkins already sits beside a Kubernetes cluster; a greenfield pipeline is usually better served by a CI system with ephemeral runners built in.

### Kueue
**Short:** Kubernetes job queueing controller adding quotas, fair sharing and gang admission for batch and training jobs.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/task-queue-and-jobs @2, ml-lifecycle/ml-platform-and-pipelines @3

### Kustomize
**Short:** Template-free Kubernetes manifest customization: base manifests plus per-environment overlays and patches.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/infrastructure-as-code-and-config @2, platform-delivery/ci-cd-and-release @3

A base directory holds plain, valid Kubernetes YAML, and each overlay directory patches it, whether through a strategic merge patch, a JSON patch, or the built-in transformers for name prefixes, common labels, images, replica counts and config maps. It is built into kubectl, so applying an overlay is a flag rather than another tool in the chain.

Because there is no templating language, the base is always applyable on its own and diffs stay readable, which is the main argument for it. That is also its limit: anything conditional or loop-shaped that Helm expresses with template logic becomes an awkward patch or a second overlay. Reach for it for environment variation on manifests your own team owns, and for Helm when you are packaging software for other people to install through a values contract.

### Lambda Powertools
**Short:** AWS utility library for Lambda handlers: structured logging, tracing, metrics, idempotency and batch processing.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/tracing-apm-and-llm-observability @2, observability/logging @2, traffic-edge/rate-limiting-and-resilience @3

### LaunchDarkly
**Short:** Commercial feature-flag and progressive-delivery platform, also used to run product experiments.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, ml-lifecycle/evaluation-and-benchmarks @2

SDKs hold a streaming connection to the flag service, so a change takes effect in milliseconds across every running instance without a deploy, and evaluation happens locally against the rules so it is not a network call per check. Rules target on context attributes, which is what supports shipping code dark, enabling it for internal users, rolling out by percentage, and turning a bad feature off instantly rather than waiting for a rollback to build. Tying variations to metrics turns the same mechanism into an experiment.

The discipline it demands is flag hygiene. Every live flag is a branch in production, and combinations of stale flags produce states nobody has tested, so removing a flag once its rollout is finished is part of the work, not an optional cleanup.

### Lens
**Short:** Desktop IDE for Kubernetes clusters: browse resources, stream logs, exec into pods and view live metrics.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, devtools/version-control-and-workbench @3

### local-path-provisioner
**Short:** Rancher provisioner creating PersistentVolumes from node-local disk; simple dynamic storage for dev clusters.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-stores/object-and-file-storage @2

### Managed K8s GPU
**Short:** Cloud-managed Kubernetes with GPU node pools and a GPU operator, as on EKS or GKE, for ML workloads.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/kubernetes-and-orchestration @2, ml-lifecycle/ml-platform-and-pipelines @3

### Metacontroller
**Short:** Kubernetes add-on that lets you write operators as simple webhooks, avoiding a full controller-runtime codebase.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

### metrics-server
**Short:** Kubernetes add-on that collects node/pod CPU and memory from kubelets to power kubectl top and the HPA.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, observability/metrics-and-monitoring @2

It scrapes the resource metrics each kubelet exposes, keeps only the most recent value in memory, and serves it through the `metrics.k8s.io` aggregated API, which is what makes `kubectl top` and CPU or memory based Horizontal and Vertical Pod Autoscaling work at all. Because it keeps no history it is not monitoring: there is nothing to graph, alert on or look back at, so a cluster still needs Prometheus or an equivalent beside it. It is not present by default on every distribution, and a cluster where `kubectl top` errors and HPAs sit at `<unknown>` is usually just missing it. Scaling on a custom or external metric needs a separate adapter, since metrics-server serves only CPU and memory.

### Modal
**Short:** Serverless container/GPU cloud for on-demand training, inference and sandboxed agent execution.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, llm-apps/agentic-environments @2, ml-lifecycle/ml-platform-and-pipelines @3

You write a Python function, decorate it with the container image, GPU type, timeout and concurrency it needs, and Modal builds the image and runs it remotely; replicas scale out with demand and back to zero when idle, billed by execution time. Volumes and network file systems hold model weights between runs, and sandboxes execute untrusted code — including code an agent generated — in an isolated container rather than in your process.

That pricing shape fits spiky work: a fine-tune that needs an H100 for two hours, a batch inference sweep over a dataset, an endpoint with bursty traffic. It fits steady work badly — once a GPU is busy most of the day, a reserved or committed instance is cheaper than per-second serverless — and cold starts matter, because pulling and loading tens of gigabytes of weights is time your first request pays for unless you keep containers warm.

### NCC
**Short:** Google Cloud Network Connectivity Center - a hub-and-spoke control point for connecting many VPCs and on-prem sites.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

### NetworkPolicy
**Short:** Kubernetes object declaring which pods may talk to which, enforced by the CNI for network segmentation.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, security/supply-chain-and-runtime-security @2, runtime-systems/io-networking-and-syscalls @3

### Node autoscaler
**Short:** Kubernetes node-provisioning autoscalers (Karpenter, Cluster Autoscaler) that add node capacity for pending pods.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

### Nomad
**Short:** HashiCorp's lightweight scheduler that orchestrates containers, binaries and VMs from one binary.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @3

One Go binary is both the server and the client agent, and a job is HCL describing groups of tasks with a driver - Docker, raw `exec`, Java, QEMU - so containerized and non-containerized workloads go through the same scheduler with the same bin-packing, health checks and rolling updates. Service discovery and secrets are not bundled: it integrates with Consul and Vault, or its own built-in service registry for simpler setups.

Reach for it when you want scheduling without Kubernetes's API surface and operational weight, when a meaningful share of the workload is not containers, or when the team running it is small. The tradeoff is ecosystem: no operators, no CRDs, far fewer off-the-shelf integrations, and a much smaller pool of engineers who have run it before.

### NVIDIA device plugin
**Short:** Kubernetes DaemonSet advertising nvidia.com/gpu resources, with time-slicing and MIG partition strategies.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, ml-lifecycle/ml-platform-and-pipelines @2, gpu/multi-gpu-and-collectives @3

### NVIDIA GPU Operator
**Short:** Kubernetes operator installing the whole GPU node stack: driver, container toolkit, device plugin, DCGM, MIG.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, gpu/multi-gpu-and-collectives @3, ml-lifecycle/ml-platform-and-pipelines @3

### OCI registries
**Short:** Registries speaking the OCI distribution spec, storing container images and increasingly Helm charts and artifacts.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, devtools/build-and-dependency-management @2

### official Helm chart
**Short:** The Apache Airflow community Helm chart deploying scheduler, webserver and autoscaling workers on Kubernetes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/workflow-and-durable-execution @3

### OLM
**Short:** Operator Lifecycle Manager - installs, upgrades and resolves dependencies between Kubernetes operators.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/ci-cd-and-release @3

### OpenCost
**Short:** CNCF project allocating cloud spend to Kubernetes namespaces, workloads and labels from usage metrics.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/kubernetes-and-orchestration @2

### OpenMeter
**Short:** Usage metering and billing service aggregating events such as LLM token consumption into per-customer billable units.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/metrics-and-monitoring @3

### OpenTofu
**Short:** MPL-licensed community fork of Terraform; a drop-in CLI for declaring and applying infrastructure state.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

It speaks the same configuration language, uses the same provider and module ecosystem and reads the same state format, so moving an existing codebase is close to swapping which binary the pipeline calls. It exists because Terraform's licence moved to a source-available one; the fork is developed under the Linux Foundation and stays open source, and it has since added features of its own, state encryption among them.

Reach for it when the licence matters, which it does if you embed the tool in a product or an internal platform you offer to others, or when one of the fork's own features is the deciding factor. Either way, pick one and standardize: running both against the same state is the way to get a surprise.

### Operator SDK
**Short:** Scaffolds and builds Kubernetes operators in Go, Ansible or Helm, including CRDs and the controller loop.
**Kind:** tech
**Lang:** go
**Roles:** platform-delivery/kubernetes-and-orchestration @1, devtools/compiler-toolchain-and-codegen @3

### OperatorHub.io
**Short:** Community catalogue for discovering and installing Kubernetes Operators through the Operator Lifecycle Manager.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

### Organizations Tag Policy
**Short:** AWS Organizations policy standardizing resource tag keys and values so cost attribution can be enforced.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, security/authorization-and-policy @2

### Packer
**Short:** HashiCorp tool that bakes versioned immutable machine images (AMIs, OVAs, container images) from a declarative template.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/container-and-image @2, platform-delivery/ci-cd-and-release @3

### Podman
**Short:** Daemonless, rootless OCI container engine and image builder with a Docker-compatible CLI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

### Port
**Short:** Commercial internal developer portal: a service catalog, scorecards and self-service actions for platform teams.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/ci-cd-and-release @2

### Provisioned Concurrency
**Short:** AWS Lambda setting keeping a number of execution environments warm, removing cold-start latency for a standing fee.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/profiling-and-performance @3

### Pulumi
**Short:** Infrastructure as code written in real languages (TypeScript, Python, Go, C#, Java) over a state-managed provider model.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

### Puppet
**Short:** Agent-based declarative configuration management: nodes pull a compiled catalog and converge to the declared state.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

### Readiness and liveness
**Short:** Distinct health probes: readiness gates traffic until dependencies load, liveness restarts a wedged process.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, inference/model-server @3, observability/metrics-and-monitoring @3

### RIs
**Short:** Reserved instances: a one- or three-year capacity commitment traded for a large discount on cloud compute.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### runc
**Short:** The reference OCI runtime: creates and runs a container from a bundle using Linux namespaces and cgroups.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

### RunPod
**Short:** GPU cloud renting on-demand and spot A100/H100 pods and serverless endpoints for training and inference.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, model-training/fine-tuning-and-peft @3

### RuntimeClass
**Short:** Kubernetes resource that selects which container runtime handler (e.g. gVisor, Kata) a pod runs under.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @2

### SaltStack
**Short:** Configuration-management system with a high-fan-out push/pull model over YAML plus Jinja state files.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

### Savings Plans
**Short:** AWS commitment-based discount: pledge an hourly spend for 1-3 years in exchange for lower compute rates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### semantic-release
**Short:** CI tool deriving the next semver version from Conventional Commits and publishing the tag, changelog and release.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, devtools/version-control-and-workbench @2, devtools/build-and-dependency-management @3

### Serverless Framework
**Short:** YAML-driven toolkit for declaring and deploying FaaS functions, events and their supporting cloud resources.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @2, platform-delivery/ci-cd-and-release @3

### Serverless GPU
**Short:** Pay-per-invocation GPU capacity such as SageMaker Serverless or Cloud Run GPU; scales inference to zero.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, inference/model-server @2

### Skaffold
**Short:** Google tool automating the build-push-deploy inner loop against a Kubernetes cluster with file-watch redeploys.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2, platform-delivery/container-and-image @3

### skopeo
**Short:** Daemonless CLI to inspect, copy and sign container images directly between registries.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @3

### SkyPilot
**Short:** Runs training and serving jobs on whichever cloud has the cheapest available GPUs, with spot recovery and queuing.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, model-training/distributed-training @2, ml-lifecycle/ml-platform-and-pipelines @3

### SnapStart
**Short:** AWS Lambda feature snapshotting an initialized environment and restoring it, removing cold-start init.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/runtime-internals-and-types @3, observability/profiling-and-performance @3

### Spinnaker
**Short:** Multi-cloud continuous delivery platform with pipeline stages for canary, blue-green and rollback deployments.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

### Spot
**Short:** Interruptible spare cloud capacity at a steep discount, reclaimable with short notice.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### Spot Fleet
**Short:** AWS request for a pool of interruptible spot instances across types and AZs at a steep discount.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### stakater/Reloader
**Short:** Kubernetes controller that triggers a rolling restart when a mounted ConfigMap or Secret changes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, apis-frameworks/dependency-injection-and-config @3

### Strimzi
**Short:** Kafka operator for Kubernetes: Kafka, KafkaTopic, KafkaUser and KafkaRebalance CRDs with rolling KRaft ops.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/event-streaming-and-processing @1

### Tekton
**Short:** Kubernetes-native CI/CD: pipelines and reusable Tasks modeled as CRDs, each step running in its own pod.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

A Task is a list of steps, each a container image with a command, and all steps of one task run in a single pod sharing a workspace volume; a Pipeline wires tasks into a DAG and a PipelineRun is one execution. Because those are all Kubernetes custom resources, your CI history is objects you can query with `kubectl` and secure with RBAC, rather than state locked inside a CI server. Tasks are reusable and shareable, so a pipeline becomes mostly composition of published tasks with parameters.

It suits platform teams standardising delivery on Kubernetes, especially alongside signing of the artifacts a run produced. The cost is that you are assembling a CI system out of primitives rather than buying one with a finished UI.

### Terraform
**Short:** Declarative cloud-agnostic infrastructure-as-code tool: HCL config, provider plugins, state file, plan and apply.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

You describe the desired infrastructure in HCL; Terraform reads the current world through provider plugins, diffs it against a state file mapping your resources to real ids, and `plan` shows exactly what it would create, change or destroy before `apply` acts. The state file is the whole operational story: it must live in remote, locked storage because two concurrent applies against one state corrupt it, and anything created outside Terraform is invisible until imported. Reach for it to make environments reproducible and reviewable across clouds and SaaS providers, and always read the plan for replacements, since a seemingly small attribute change can mean destroy-and-recreate. OpenTofu is the community fork that exists because Terraform moved from an open-source licence to the BUSL.

### Terraform CLI
**Short:** The Terraform binary running init/plan/apply/destroy against providers and state to reconcile declared infrastructure.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

### Terraform Cloud
**Short:** HashiCorp's managed Terraform backend: remote locked state, PR-driven plan/apply runs and policy gates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/ci-cd-and-release @2, security/authorization-and-policy @3

### terraform fmt
**Short:** Terraform subcommand that rewrites configuration files into the canonical HCL style, usually run as a CI gate.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/static-analysis-and-linting @2

### terraform test
**Short:** Terraform 1.6+ native test framework running assertions against plans or real applies from HCL test files.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/testing-and-mocking @1

### terraform-docs
**Short:** CLI generating Markdown documentation of a Terraform module's inputs, outputs and providers from its source.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/static-analysis-and-linting @3

### terraform.lock.hcl
**Short:** Terraform's provider version and checksum lockfile; commit it so every run resolves identical provider binaries.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/build-and-dependency-management @2, security/supply-chain-and-runtime-security @3

### Terragrunt
**Short:** Terraform wrapper that keeps backends and inputs DRY and runs many state roots in dependency order.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

Terragrunt addresses the problem you hit right after splitting Terraform into many small state roots: every root then needs nearly the same backend block, provider configuration and shared inputs. A small config file per root inherits those from parent files and generates the boilerplate before invoking Terraform, and dependency blocks let one root consume another's outputs so a single run-all command walks the whole tree in dependency order.

Reach for it once you have dozens of environment-by-component state files and the copy-paste has become the pain. A single small stack does not need the extra layer, and the indirection makes reading a plan harder than it was.

### Test Kitchen
**Short:** Converges Chef or Puppet code on throwaway VMs/containers and verifies the resulting machine state.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/testing-and-mocking @2

### Tower
**Short:** Ansible Tower/AWX: the web UI, scheduler, credential vault and RBAC layer that runs Ansible playbooks at scale.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/ci-cd-and-release @2, security/authorization-and-policy @3

### Transit Gateway
**Short:** AWS hub-and-spoke router connecting many VPCs, VPNs and Direct Connect links without a mesh of peering connections.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

### Trusted Advisor
**Short:** AWS advisory service flagging idle resources, rightsizing opportunities and security or limit risks.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

### Unleash
**Short:** Open-source feature flag server for runtime toggles, gradual rollout and kill switches across services.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

SDKs fetch the flag configuration and evaluate it in-process, so checking a flag is a memory lookup on the request path with no network call, and usage metrics are reported back asynchronously. A flag is a set of activation strategies: gradual rollout by percentage with a stickiness field so a user does not flip between variants, explicit user or segment lists, and constraints on host, environment or arbitrary context fields.

The point is decoupling deploy from release - ship the code dark, ramp it, and keep a kill switch for the risky path that does not require a rollback. The discipline that decides whether it helps or hurts is removal: a flag that outlives its rollout is an untested branch that doubles the state space, so give every flag an owner and an expiry date and delete it once the decision is made.

### validate
**Short:** terraform validate: canonical syntax, type and reference checking of a configuration without touching state.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/static-analysis-and-linting @2

### Velero
**Short:** Kubernetes backup and DR: snapshots cluster objects and PersistentVolumes to object storage, and restores them.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-access/replication-ha-and-backup @2

A controller in the cluster acts on backup and restore resources you create. A backup selects namespaces, resource types or labels, writes the object definitions to an object-store bucket, and handles persistent volumes either by asking the storage layer for a snapshot through the CSI interface or by copying the file contents when the storage has no snapshot support. A restore replays that into the same cluster or a different one, which is also the practical way to migrate a namespace between clusters or clone production into staging.

Reach for it for cluster-level disaster recovery and for the migration case. Be clear about what a volume snapshot is not: a copy of a running database's files is crash-consistent at best, so use the pre and post backup hooks to quiesce or flush, and keep the database's own backup and point-in-time recovery as the real recovery path for its data.

### Virtual WAN
**Short:** Azure's managed hub-and-spoke backbone connecting VNets, branches and VPN/ExpressRoute links through one hub.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

### Volcano
**Short:** Kubernetes batch scheduler adding gang scheduling, fair-share queues and GPU topology awareness for training jobs.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/task-queue-and-jobs @2, ml-lifecycle/ml-platform-and-pipelines @3

### VPA
**Short:** Kubernetes Vertical Pod Autoscaler: recommends or applies right-sized CPU and memory requests from observed usage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

### VPC Peering
**Short:** Point-to-point private route between two VPCs: non-transitive, no overlapping CIDRs, traffic stays off the internet.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @3

### VPC, Security Groups, NACLs
**Short:** The AWS network isolation primitives: a private virtual network plus stateful instance and stateless subnet firewalls.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2, security/authorization-and-policy @3
