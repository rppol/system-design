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

### ArgoCD
**Short:** Pull-based GitOps controller that reconciles Kubernetes clusters against manifests in git.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2

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

### GitLab CI
**Short:** All-in-one platform bundling Git hosting, YAML-defined CI/CD pipelines and a container/package registry.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, devtools/version-control-and-workbench @2, platform-delivery/container-and-image @3

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

### Helm
**Short:** Kubernetes package manager: templated, versioned chart releases of manifests with values-driven overrides.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/infrastructure-as-code-and-config @2, platform-delivery/ci-cd-and-release @3

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

### Karpenter
**Short:** Kubernetes node autoscaler that provisions just-in-time, right-sized, Spot-aware instances for pending pods.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

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

### Modal
**Short:** Serverless container/GPU cloud for on-demand training, inference and sandboxed agent execution.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, llm-apps/agentic-environments @2, ml-lifecycle/ml-platform-and-pipelines @3

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

### Terraform
**Short:** Declarative cloud-agnostic infrastructure-as-code tool: HCL config, provider plugins, state file, plan and apply.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

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
