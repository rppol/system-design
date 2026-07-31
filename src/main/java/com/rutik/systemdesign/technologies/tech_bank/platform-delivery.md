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

An ACR instance is an Azure resource with three SKU tiers, and most of the interesting behaviour is gated on Premium: geo-replication of one registry name across regions, private endpoints so pulls never traverse the internet, repository-scoped tokens, and higher throughput limits. Authentication goes through Entra ID rather than a static `docker login`, and attaching a registry to an AKS cluster grants the kubelet pull rights so no `imagePullSecret` is needed. ACR Tasks can also build and patch images inside the registry, with a base-image-update trigger.

Reach for it when workloads run on Azure and you want pull traffic and identity to stay inside the tenant. Outside Azure that integration is the whole value, and GHCR or Docker Hub is simpler; if you need policy gates such as refusing to serve an image with a critical CVE, Harbor still goes further.

### act
**Short:** CLI that runs GitHub Actions workflows locally in Docker, so pipeline changes can be tested without pushing.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, devtools/testing-and-mocking @3

`act` parses the workflow YAML under `.github/workflows`, synthesizes the event payload for the trigger you name, and runs each job in a Docker container from a runner image standing in for `ubuntu-latest`, executing steps and marketplace actions much as GitHub would. Secrets come from `-s` flags or a `--secret-file`, and `-j` runs a single job.

It shortens the edit-push-wait loop that makes CI debugging miserable. The fidelity is approximate: the default runner images are far smaller than GitHub's, so a step relying on preinstalled tooling can fail locally and pass remotely; OIDC, hosted caches and some contexts are not reproduced; macOS and Windows runners are out of scope entirely. Treat a green `act` run as a syntax and logic check, not a guarantee.

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

The unit is a task definition, a versioned JSON document naming the containers, their CPU and memory, the IAM task role and the log configuration; a service then holds a desired count of tasks running and registers them into an ALB or NLB target group. In `awsvpc` network mode each task gets its own ENI with a VPC address and security group. Fargate is a launch type rather than a separate product: AWS runs each task in a managed microVM and bills vCPU and GB by the second, so there are no instances to patch or bin-pack.

Reach for it when the workload is containers on AWS and you do not want Kubernetes's API surface or its upgrade cadence. The cost is portability and ecosystem, since there are no operators, no CRDs and no Helm; a platform team building tooling other teams consume is usually better served by EKS.

### Ansible
**Short:** Agentless configuration management and orchestration driven by YAML playbooks over SSH.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

There is no agent and no server: the control node connects over SSH or WinRM, copies small Python modules to the target, runs them and removes them. A playbook maps plays onto host groups drawn from an inventory, and each task names a module with parameters. Modules are written to be idempotent, so `state: present` converges rather than re-executes, which is what makes a rerun safe; a `command` or `shell` task opts out of that unless you add `creates` or `changed_when`.

Reach for it for ad hoc fleet operations, configuring machines that already exist, and orchestrating multi-step procedures across hosts in order. It is push-based, so very large fleets need `forks` tuning, and it does not continuously enforce state the way Puppet's or Chef's agents do. For immutable infrastructure, baking an image with Packer beats converging a running one.

### Ansible AWX
**Short:** Web UI and API over Ansible adding job scheduling, inventories, credential storage, RBAC and run history.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/ci-cd-and-release @3

AWX is the upstream open-source project behind Red Hat's automation controller. It stores inventories, playbook projects synced from Git, and credentials encrypted at rest, and its central object is the job template: a playbook plus an inventory plus credentials plus extra variables, launchable from the UI, from a REST call, or on a schedule. Every run is recorded with full per-host output, and RBAC lets someone use a credential without ever seeing it.

Reach for it when playbooks are run by people who did not write them, or when production secrets must not sit on laptops. It is a real service to operate, with a database, a task queue and a Kubernetes-based deployment through its operator, so a small team running playbooks from a reviewed CI job gains little from adding it.

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

In the current `gha-runner-scale-set` design you install a controller plus one Helm release per scale set; a listener holds a long poll against GitHub for jobs targeting that set's `runs-on` label and creates an ephemeral runner pod per job, which is destroyed when the job ends.

Reach for it when builds need cluster resources, private VPC access, or hardware GitHub does not sell, and when you want a fresh runner per job rather than a reused machine accumulating state. You take on node capacity and image-pull latency at the start of every job, plus the security question of what a workflow can reach from inside your cluster: a self-hosted runner exposed to a public repository's pull requests is a well-known compromise path.

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

An `Application` resource names a repo, a path and a revision; the controller renders whatever is there, plain YAML, Helm or Kustomize, and compares it with live objects, reporting each as Synced or OutOfSync and separately as Healthy or Degraded through per-kind health assessments. Sync waves and resource hooks order the apply, so a migration Job can complete before the Deployment rolls, and an automated sync policy with prune and self-heal turns a manual `kubectl edit` into drift the controller reverts.

The visual diff and resource tree are why it is the usual pick when many people need to see what is actually deployed. Secrets are the standing gap, since manifests live in a repository and need Sealed Secrets or the External Secrets Operator beside them; Flux is the alternative if you prefer a set of composable controllers over a server with a UI.

### ArgoCD Image Updater
**Short:** Argo CD companion that watches a registry and writes new image tags back to Git so GitOps reconciles the deploy.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/container-and-image @3

It polls the registries behind an Application's images, selects a tag according to a per-application strategy such as a semver constraint, newest build time, alphabetical order or a digest, and then writes the result back rather than patching the cluster. Depending on configuration that write is a commit to the Git repository holding the manifests or an Argo CD parameter override, and the ordinary reconcile loop performs the deploy.

It closes the gap where CI builds an image and nothing bumps the tag in Git, without handing CI any cluster credentials. The tradeoff is that a registry now triggers production changes, so constrain the strategy tightly and keep tags immutable, because a mutable `latest` plus automatic updates is how an unreviewed image reaches production. Flux's image-automation controllers do the same job on the Flux side.

### Artifact Hub
**Short:** CNCF catalogue for discovering and verifying public Helm charts, operators and other cloud-native packages.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @3

Artifact Hub is a CNCF-hosted search index over cloud-native packages: Helm charts, Kubernetes operators, OPA and Gatekeeper policies, Tekton tasks, container images, Falco rules and more. Publishers register a repository and the hub crawls it, so each listing carries the package's own metadata, its install command, its values or parameters, and badges for whether the publisher is verified and whether the artifact is signed.

Use it to find which chart for a given piece of software is the maintained one, and to check a repository's provenance before adding it. It hosts nothing itself, so pulls still go to the publisher's registry, and a listing is not an endorsement, so read the chart before installing it. Controlling what may be installed in your clusters belongs in your own registry or admission layer, not here.

### Athena billing export
**Short:** Querying AWS cost and usage report files with Athena SQL for line-item level spend analysis and chargeback.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, data-stores/warehouse-and-olap @3

The Cost and Usage Report is delivered to an S3 bucket as partitioned Parquet with one row per resource per hour, carrying usage type, unblended, amortized and net costs, reservation and Savings Plan attribution, and a column for every activated cost-allocation tag. Athena queries those files in place through a Glue table, so a chargeback report is a `GROUP BY` over a tag column rather than a pipeline into a warehouse.

Reach for it when Cost Explorer's grouping is too coarse or its retention too short, because the raw report is where per-resource and per-tag questions get answered. The costs are real: the report is large, Athena bills per byte scanned so an unpartitioned query gets expensive fast, and the schema shifts as AWS adds columns. Resources that were never tagged simply cannot be attributed, which is why the tag policy comes first.

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

An Auto Scaling group is defined by a launch template, a set of subnets and minimum, maximum and desired capacity; it launches instances to reach desired, replaces any failing an EC2 or ELB health check, and balances them across availability zones. Scaling policies drive the desired count: target tracking holds a metric such as average CPU or requests per target at a set point, step scaling reacts to CloudWatch alarm thresholds, and scheduled actions handle known daily or weekly shapes. A mixed-instances policy blends Spot and On-Demand across several types.

It is the mechanism behind self-healing and demand-following capacity for plain EC2 fleets. What it cannot do is scale faster than an instance boots and warms, so a sharp spike still needs headroom or a warm pool. For containers the equivalent decision belongs to ECS service autoscaling or, on Kubernetes, to Cluster Autoscaler or Karpenter, not here.

### AWS Budgets
**Short:** AWS service setting per-team spend budgets and firing alerts or actions when forecast or actual cost breaches them.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

A budget is a monthly, quarterly or annual threshold on cost, usage, or reservation and Savings Plan coverage, scoped by account, service, tag or any other cost dimension. Alerts fire when actual spend crosses a percentage of the budget or when the forecast does, and budget actions go further by applying a restrictive IAM policy or service control policy, stopping EC2 and RDS instances, or notifying an SNS topic your own automation subscribes to.

Reach for it as the guardrail on top of tagging, one budget per team or environment, since it is the only native control that speaks before the invoice does. Its granularity is bounded by billing data latency of several hours, so it will not catch a runaway job in its first minutes, and it blocks nothing by default. Cost Anomaly Detection is the complement for unusual spend no budget anticipated.

### AWS CDK
**Short:** AWS Cloud Development Kit: define infrastructure in TypeScript, Python, Java or Go and synthesize CloudFormation.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/compiler-toolchain-and-codegen @3

You write TypeScript, Python, Java, Go or C# that instantiates constructs; `cdk synth` executes that program and emits a CloudFormation template, and `cdk deploy` submits it, so the language is a template generator rather than a runtime. Constructs come in layers: L1 maps one-to-one onto CloudFormation resources, L2 adds defaults and helper methods such as `bucket.grantRead(role)` which writes the IAM policy for you, and L3 patterns assemble whole architectures.

The win is genuine abstraction, with loops, types, unit tests and reusable constructs shipped as ordinary packages. The costs are inheriting CloudFormation's limits and error messages underneath, generated logical ids that turn an innocent refactor into a resource replacement, and a general-purpose language making it easy to write infrastructure nobody can review. Terraform stays the choice when the target is not AWS-only.

### AWS CloudFormation
**Short:** AWS-native infrastructure as code: declarative YAML/JSON stacks with managed change sets, drift detection and rollback.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

A stack is a set of resources managed as one unit from a YAML or JSON template; CloudFormation builds the dependency graph, creates or updates resources in order, and rolls the whole stack back to its previous state when any resource fails. A change set shows what an update would do before you run it, drift detection compares live resources against the template, and nested stacks plus StackSets extend the model across accounts and regions.

It is the AWS-native option, so there is no state file to store or lock and IAM is the only permission model; it is also what CDK and SAM emit underneath. The pain is well known: slow updates, uninformative failures, properties that force replacement rather than modification, and coverage of new services that sometimes trails the API. Terraform is the alternative when you want faster feedback or anything beyond AWS.

### AWS Compute Optimizer
**Short:** AWS service that recommends rightsizing and waste reduction from observed resource utilization.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

It reads CloudWatch metrics for EC2 instances, Auto Scaling groups, EBS volumes, Lambda functions and ECS services on Fargate over a rolling lookback, classifies each as under-provisioned, over-provisioned or optimized, and proposes specific alternatives with projected utilization and monthly saving per option. Enabling enhanced infrastructure metrics extends the lookback window and improves the recommendations.

Reach for it as the first pass at rightsizing, because it is free and needs no agent. Its blind spot is memory: without the CloudWatch agent installed it sees only CPU, network and disk, so a memory-bound workload can be labelled over-provisioned when it is not. Treat the output as candidates to verify rather than changes to apply, and read it alongside Cost Explorer's rightsizing view.

### AWS Cost Explorer
**Short:** AWS console and API for spend visibility: cost breakdowns, trends, forecasts and anomaly alerts.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

Cost Explorer sits over billing data and slices spend by service, account, region, usage type, charge type or any activated cost-allocation tag, at daily or hourly granularity within a rolling retention window, with a forecast. The same queries are available through its API, which is what most internal cost dashboards actually call, and it also produces reservation and Savings Plan coverage and utilization reports plus rightsizing recommendations.

It is the right tool for the question of what changed and where, answered in minutes rather than by building a pipeline. It stops being enough when you need per-resource line items, joins against your own metadata, or history beyond its retention, which is where the Cost and Usage Report queried from Athena takes over. API calls are billed per request, so a dashboard polling it constantly has a cost of its own.

### AWS ECR
**Short:** AWS-managed OCI container registry with IAM-based access control, image scanning on push and lifecycle rules.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/cloud-platform-and-cost @2

A repository is a regional resource whose permissions are plain IAM, so a pull is authorized by the task or node role and `docker login` is really an `ecr:GetAuthorizationToken` call yielding a twelve-hour token. Basic scanning matches layers against a CVE feed on push, while enhanced scanning delegates to Amazon Inspector and rescans continuously as new CVEs are published. Lifecycle policies expire images by age or count, and pull-through cache rules mirror an upstream registry such as Docker Hub on first request.

Use it whenever workloads run on ECS, EKS or Lambda: images stay in-region, pulls cross no internet path, and there is no separate credential to rotate. Watch storage cost, because untagged layers accumulate silently without a lifecycle policy, and remember that cross-region or cross-account pulls need explicit replication or a repository policy.

### AWS ECS
**Short:** AWS container orchestrator that schedules tasks and services onto EC2 or Fargate without running Kubernetes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @2, platform-delivery/cloud-platform-and-cost @3

ECS is a control plane AWS operates: you never run a scheduler, you register capacity as EC2 container instances running the ECS agent or as Fargate, and it places tasks according to placement strategies and constraints. Capacity providers connect a service to an Auto Scaling group or to Fargate and Fargate Spot, so cluster scaling follows task demand. Rolling deployments have a circuit breaker that reverts a failed rollout on its own, and `ecs execute-command` opens a shell inside a running task with no SSH.

It is markedly less to learn and operate than Kubernetes, and identity, logging and load balancing are native rather than add-ons. The limits appear when you want portability, a controller ecosystem, or anything expressed as a custom resource, and choosing it is choosing not to move off AWS. EKS is the alternative once the platform, rather than the application, is what you are building.

### AWS IPAM
**Short:** AWS IP Address Manager: plans, allocates and audits VPC CIDR blocks across accounts so ranges never overlap.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

IPAM models your address space as a hierarchy of pools, a top-level pool per region or business unit subdivided down to the CIDRs individual VPCs draw from, and allocates non-overlapping blocks on request. Integrated with AWS Organizations it discovers existing VPCs, subnets and elastic IPs across every account with their utilization, and it retains historical allocation data so you can see what held an address at a point in time.

Reach for it once address planning has outgrown a spreadsheet, which in practice is the moment you have several accounts plus a Transit Gateway or on-premises connectivity, because overlapping CIDRs are what make peering and hybrid routing unfixable later. It is an allocation and audit plane, not a router: it renumbers nothing already deployed, so its value depends on adopting it before the sprawl, and the advanced tier bills per active address it manages.

### AWS Lambda
**Short:** AWS function-as-a-service: event-triggered, per-request-billed compute with no server management.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

You supply a zip or a container image and a handler; on an event Lambda starts an execution environment, runs your initialization code once, then invokes the handler per event and keeps the environment warm for reuse. That two-phase model is the entire performance story, since init is the cold start you pay on the first request and on every scale-out, so an SDK client, a database connection or a loaded model belongs outside the handler. Concurrency is one environment per concurrent request, capped per account and tunable per function.

It fits event-driven and spiky work: queue consumers, S3 and stream triggers, glue between services, low-traffic APIs. It fits badly against steady high-throughput services, where the per-millisecond price loses to a container, and against anything needing more than the fifteen-minute execution ceiling or durable local state between invocations.

### AWS Lambda SnapStart
**Short:** Lambda feature that snapshots an initialized runtime and restores it per invocation, cutting JVM-style cold starts.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/runtime-internals-and-types @3, observability/profiling-and-performance @3

When you publish a version, Lambda runs the function's initialization once, takes a Firecracker microVM snapshot of the initialized memory and disk, encrypts and caches it; an invocation then restores from that snapshot instead of executing init again. It was introduced for Java, where framework startup dominates the cold start, and later extended to other managed runtimes.

Reach for it when initialization is heavy and unavoidable. Two hazards follow from resuming many environments from one snapshot: anything captured at init is now shared and stale, so a connection or a temporary credential opened before the snapshot must be refreshed in a runtime hook, and a random seed or unique id generated at init is identical in every restored environment. Provisioned Concurrency remains the option for a runtime SnapStart does not cover.

### AWS Parameter Store
**Short:** SSM Parameter Store: hierarchical application config and SecureString secrets, cheaper than Secrets Manager.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, security/secrets-and-cryptography @2, apis-frameworks/dependency-injection-and-config @3

Parameters live in a hierarchical path namespace such as `/app/prod/db/host`, which is what IAM policies and `get-parameters-by-path` operate on, so one call fetches an entire environment's configuration. A `SecureString` is encrypted with a KMS key and decrypted on read with `--with-decryption`, and versions are retained so a previous value can be inspected and restored. Standard parameters are free up to a per-account limit; advanced parameters raise the size ceiling, add policies such as expiry, and are billed.

Reach for it for application configuration and for secrets whose lifecycle you are willing to manage yourself. What Secrets Manager adds is built-in rotation with managed rotation functions and cross-region replication, so a database credential that must rotate belongs there while endpoints, feature toggles and AMI ids belong here. Default throughput is modest, so cache reads rather than calling it on every request.

### AWS SAM
**Short:** Serverless Application Model: shorthand CloudFormation plus a CLI for building, testing and deploying Lambda apps.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @2, platform-delivery/ci-cd-and-release @3

A SAM template is a CloudFormation template with a transform header, adding a handful of serverless resource types such as `AWS::Serverless::Function`, `HttpApi` and `StateMachine` that expand into the dozens of underlying resources a function really needs: execution role, log group, permissions, API stage. The CLI covers the rest of the loop, with `sam build` packaging dependencies per runtime, `sam local invoke` and `sam local start-api` running the handler in a container against a synthesized event, and `sam deploy` pushing the expanded stack.

It suits a serverless application defined declaratively, especially where the team already reads CloudFormation. Local emulation is approximate, since IAM, throttling and real event ordering are not reproduced, so integration tests still need a deployed environment. Choose CDK instead when you want a real language and reusable constructs, or the Serverless Framework when its plugin ecosystem is the draw.

### AWS SDK
**Short:** AWS client libraries for every major language, with credential resolution, adaptive retries and backoff built in.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, traffic-edge/rate-limiting-and-resilience @2

Every language SDK shares the same shape: a credential provider chain that tries environment variables, then a shared profile, then container or instance metadata, so code never contains keys and moves between a laptop, CI and an IAM role unchanged; a region resolution chain of the same kind; and per-service clients generated from the service's own API model. Requests are signed with SigV4 and retried under a configurable mode, where the adaptive mode adds client-side rate limiting once a service starts returning throttling errors.

Use the SDK rather than raw HTTP for anything AWS, because signing, pagination, waiters and retry classification are exactly what people get wrong by hand. Know the defaults you inherit: the standard retry count and timeouts are wrong for a latency-sensitive path, and retrying hard against a throttled service makes it worse. Set explicit timeouts and reuse clients, since constructing one per call re-resolves credentials.

### AWS VPC
**Short:** AWS virtual private cloud: private IP ranges, subnets, route tables, security groups and NAT.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

A VPC is a regional, logically isolated network with a CIDR block you choose, divided into subnets that each sit in one availability zone. Whether a subnet is public or private is not a setting but a consequence of its route table: a route to an internet gateway makes it public, a route to a NAT gateway gives private instances outbound access only. Security groups are stateful and attach to network interfaces, network ACLs are stateless and attach to subnets, and VPC endpoints keep traffic to services such as S3 or DynamoDB off the internet entirely.

Essentially every AWS workload lands in one, so the decisions worth care are the CIDR, which cannot easily be changed and whose overlap with another network blocks peering and hybrid routing later, and the NAT gateway, billed hourly plus per gigabyte processed and a routine surprise on the bill.

### AWS VPC CNI
**Short:** EKS CNI plugin giving pods real VPC IPs and enforcing security-group and network policy.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, runtime-systems/io-networking-and-syscalls @3

The plugin attaches secondary elastic network interfaces to each EKS node and assigns pods addresses from the VPC subnet, so a pod's IP is a real routable VPC address with no overlay or encapsulation between nodes. That is why flow logs, security groups and on-premises routes see pods directly, and it is what makes security groups for pods and pod-level policy enforcement possible at all.

The consequence to plan for is address exhaustion: pods consume subnet addresses, the pods-per-node ceiling is set by how many interfaces and addresses the instance type supports, and a small subnet fills long before the nodes do. Prefix delegation raises density considerably and is worth enabling early. If VPC addresses are genuinely scarce, an overlay CNI such as Calico or Cilium sidesteps the problem at the cost of that direct integration.

### AWS Well-Architected Tool
**Short:** AWS console workflow for reviewing a workload against the Well-Architected pillars and tracking remediation items.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/infrastructure-as-code-and-config @3

The tool is a structured questionnaire in the console: you define a workload, then answer a fixed set of questions per pillar covering operational excellence, security, reliability, performance efficiency, cost optimization and sustainability, each offering best-practice choices. Whatever you do not select becomes a risk item, high or medium, linked to the guidance behind it, and the whole review is saved as a milestone so a later pass shows what moved. Lenses swap in a question set for a domain such as serverless, SaaS or machine learning.

Its value is that it forces a conversation across pillars nobody would otherwise schedule, and that the output is a tracked remediation list rather than a document. It is self-assessed and point-in-time, so it detects nothing automatically and reflects only how honestly it was filled in; Trusted Advisor, Config and Security Hub are the automated checks that belong beside it.

### az CLIs
**Short:** The Azure command-line interface for creating, inspecting and scripting cloud resources from a shell.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/infrastructure-as-code-and-config @3

`az` is a Python-based command tree of the form `az <group> <subgroup> <command>`, talking to Azure Resource Manager. Authentication comes from `az login` or a service principal or managed identity, and the resulting token cache is what the Azure SDKs' `DefaultAzureCredential` and tools such as Terraform pick up. Every command takes `--output json`, `table` or `tsv` plus a JMESPath `--query`, which is how it is used in scripts, and extensions add preview and service-specific command sets.

Reach for it for exploration, one-off operations, and the bootstrap commands other tools depend on, `az aks get-credentials` being the one everybody runs. Resist letting it become how infrastructure is created: imperative commands leave nothing that reproduces an environment, which is what Bicep, ARM templates or Terraform are for. Azure PowerShell covers the same surface if that is the shell your team lives in.

### Azure Budgets
**Short:** Azure Cost Management feature setting per-team or per-subscription spend budgets with threshold alerts and actions.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

A budget is a Cost Management object scoped to a subscription, resource group, management group or a filtered slice by tag or service, with a monthly, quarterly or annual amount. Alert rules fire at percentages of actual or forecast spend and notify email addresses or an action group, and an action group can run a Logic App, a webhook or an automation runbook, which is how teams wire a budget breach to shutting down a non-production environment.

Use one per team or environment as the guardrail on top of a tagging convention. The limits are the same as every cloud budget: cost data lags by hours so it is not a real-time brake, it blocks no deployment on its own, and it depends entirely on resources being scoped or tagged correctly. Anomaly alerts complement it by catching spend patterns no budget anticipated.

### Azure Container Apps
**Short:** Azure's serverless container platform: run containers with scale-to-zero and KEDA autoscaling, no cluster to manage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/container-and-image @2, platform-delivery/kubernetes-and-orchestration @3

Container Apps runs containers on a managed Kubernetes and Dapr foundation you never see. An app declares its image, resources, ingress and a scale rule; the platform gives it an HTTPS endpoint, revisions with traffic split between them for blue-green or canary, and scaling driven by KEDA, so HTTP concurrency, a queue length or any KEDA scaler can take replicas from zero upward. Dapr sidecars are opt-in for service invocation, pub/sub and state.

It sits between a bare container instance and AKS: far less to operate than a cluster, and the right choice for microservices, event consumers and background jobs where scale-to-zero matters more than control. You give up the Kubernetes API, so no CRDs, no operators and no custom controllers, and you accept a cold start on the first request after idling. Reach for AKS when the platform itself is what you are building.

### Azure Files CSI
**Short:** Kubernetes CSI driver mounting Azure Files shares as ReadWriteMany volumes for pods needing a shared filesystem.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-stores/object-and-file-storage @2

The driver provisions or mounts an Azure Files share as a Kubernetes volume over SMB, or NFS 4.1 on premium accounts. Because a file share allows concurrent mounts from many nodes, it is one of the few ways to get a `ReadWriteMany` volume on Azure, where managed disks are `ReadWriteOnce`, and dynamic provisioning through a StorageClass creates the share and its secret for you. Mount options carry the identity mapping, so `uid`, `gid` and `fileMode` decide what a non-root container can actually write.

Reach for it when several pods genuinely need one shared filesystem: uploaded assets, a shared cache, a legacy application expecting a directory. Do not put a database or any small-file latency-sensitive workload on it, because SMB round trips make per-file operations far slower than a block volume; Azure Disks or object storage is the better answer there.

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

The current generation follows one design across languages: a client per service, `DefaultAzureCredential` from the Azure Identity library resolving environment variables, a managed identity, the Azure CLI login and a developer tool in a fixed order, and a shared pipeline of policies for retries, logging, distributed tracing and telemetry. Long-running operations return a poller and paged results an iterable, so the same idioms recur in every service client.

Use them rather than calling the REST APIs directly, mainly for the credential chain: identical code runs on a laptop and under a managed identity in production with no key anywhere. What to tune is the retry policy, because a service answering with HTTP 429 and a `Retry-After` header should be respected rather than retried harder, and the defaults are generic. Watch for older packages predating this design, which behave differently.

### Backstage
**Short:** Open-source developer portal: a plugin-based service catalog, software templates and docs for an internal platform.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/ci-cd-and-release @3

Backstage is a React application you fork and build, not a product you install. Its core is the software catalog, populated from `catalog-info.yaml` files in each repository that declare entities such as components, APIs, systems and resources along with the teams that own them, which makes ownership and dependencies queryable. Software templates scaffold a new service from a skeleton and register it, TechDocs renders docs-as-code from the same repositories, and everything else is a plugin, which is how CI status, cloud cost or Kubernetes state appear on a service page.

Reach for it when enough services exist that nobody can say who owns one. Be honest about the cost: it is an application your platform team owns, upgrades and writes plugins for, and it decays the moment the catalog stops being accurate. Port and Humanitec are the commercial alternatives if you want the portal without maintaining a codebase.

### boto3
**Short:** The AWS SDK for Python: typed clients and resources for S3, DynamoDB, SQS and every other AWS service.
**Kind:** tech
**Lang:** python
**Roles:** platform-delivery/cloud-platform-and-cost @1, data-stores/object-and-file-storage @2

boto3 exposes two layers over the same API. A `client` is a thin generated interface where every operation maps to an API call and returns raw dictionaries; a `resource` is a higher-level object interface that exists for only some services. Credentials and region come from botocore's chain, so nothing is hardcoded. The parts people miss are paginators, because most list operations truncate at a page and silently omit the rest, and waiters, which poll a resource until it reaches a state.

It is the default for AWS work in Python and is already present in the Lambda Python runtime. Clients are expensive to construct and safe to share between threads, so build one per process rather than per call, while resources are not thread-safe. Set explicit connect and read timeouts and a retry mode through `botocore.config.Config`, since the defaults are generous enough to hang a request path.

### Buildah
**Short:** Daemonless, rootless OCI image builder that can build from a Containerfile or by scripting layers directly.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

Buildah builds OCI images with no daemon: `buildah bud` consumes a Containerfile the way `docker build` does, but its more distinctive mode is scripting the build directly, where `buildah from` starts a working container, ordinary shell commands and `buildah copy` modify it, and `buildah commit` writes the layer. Rootless operation uses user namespaces and fuse-overlayfs, so an unprivileged user builds images with no setuid helper and no socket to a privileged process.

Reach for it in CI and on hosts where a Docker daemon is unacceptable, and when you want an image assembled from a package manager or a tarball without inventing a Containerfile. It builds only, since running containers is Podman's job on the same underlying libraries, and its build cache is weaker than BuildKit's, so a heavily layered multi-stage build is usually faster under `buildx`.

### BuildKit
**Short:** Docker's concurrent image builder with cache mounts and daemonless in-cluster builds for CI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/ci-cd-and-release @3

BuildKit rebuilt Docker's builder around a dependency graph rather than a linear list of instructions, so independent stages of a multi-stage Dockerfile run concurrently and only the stages the target actually needs are executed. Its cache is content-addressed and exportable, which is what makes CI caching work, with `--cache-to` and `--cache-from` pushing and pulling layers through a registry so a fresh runner starts warm. Dockerfile frontend features add cache mounts for package-manager directories, secret mounts so a token never lands in a layer, and bind mounts for build context.

It is the default builder in modern Docker, and it also runs as a standalone rootless daemon inside a Kubernetes pod, which is how images get built in a cluster without mounting the node's docker socket. The friction is that the cache is only as good as its key: copying the whole source tree early in the file invalidates everything after it.

### Buildkite
**Short:** Hosted CI/CD control plane that orchestrates pipelines while the build agents run on your own infrastructure.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

Buildkite splits the control plane from the compute. Pipelines, scheduling, the UI and the API are hosted by Buildkite, while agents run on your machines, in your cloud accounts or in your Kubernetes cluster and poll for work. A step can emit more YAML at run time through a dynamic pipeline upload, so the shape of a build depends on what changed, and there is no proprietary runner image you have to fit inside.

Reach for it when builds need your own hardware, private network access or large caches but you do not want to operate a CI server the way Jenkins demands, and when source and secrets should never leave your network. The cost is that you own the agent fleet, so provisioning, autoscaling, patching and isolation between builds are yours; a fully hosted service is simpler for a team with no infrastructure to spare.

### buildx
**Short:** Docker's BuildKit-backed builder: cache mounts, multi-stage and multi-architecture image builds.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

`docker buildx` is the CLI front end to BuildKit and, more importantly, a manager of builders. A builder is an instance with a driver: `docker` uses the daemon's embedded BuildKit, `docker-container` starts a dedicated BuildKit container that unlocks the full feature set, and `kubernetes` and `remote` point at builders elsewhere. `docker buildx create` and the `--builder` flag are how you choose between them, and only a non-default driver supports cache export and multi-platform output.

Reach for it when the default builder refuses something: a registry cache export, a multi-platform manifest, or a build that must run somewhere other than the local daemon. The catch is that the driver decides where the result lands, so with `docker-container` an image is not in your local image store unless you pass `--load`, and `--load` cannot carry a multi-platform manifest, which means those builds have to be pushed.

### Calico
**Short:** Kubernetes CNI plugin providing Pod networking (BGP or overlay) and enforcing NetworkPolicy at the host.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, runtime-systems/io-networking-and-syscalls @2, security/authorization-and-policy @3, traffic-edge/service-mesh-and-discovery @3

Calico gives each pod an address and, in its native mode, routes to it as plain layer 3 rather than encapsulating: a BGP daemon on every node advertises the pod CIDRs it owns, so pod-to-pod packets are ordinary routed traffic the surrounding fabric can see. Where BGP is unavailable it falls back to an IP-in-IP or VXLAN overlay, and on capable kernels it offers an eBPF dataplane that replaces kube-proxy. Policy is enforced per node, implementing Kubernetes NetworkPolicy plus its own richer global policy with rule ordering, explicit deny and host endpoints.

Reach for it when you need real segmentation, when pods should be routable on the wider network, or when policy has to cover the hosts themselves. Its extended policy model does not port to another CNI, and BGP mode needs cooperation from whoever runs the network; Cilium is the direct alternative when L7 policy and a sidecar-free mesh matter more.

### CDKTF
**Short:** Cloud Development Kit for Terraform: write infrastructure in TypeScript, Python or Go and synthesize Terraform JSON.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, devtools/compiler-toolchain-and-codegen @3

CDKTF reuses the CDK's construct programming model but synthesizes Terraform JSON instead of CloudFormation, and Terraform then performs plan and apply as usual. Provider bindings are generated into typed classes from any Terraform provider's schema, so the whole provider registry is reachable from TypeScript, Python, Go, Java or C#, and existing HCL modules can be consumed as constructs.

Reach for it when you want a real language over Terraform's provider ecosystem, with loops, types and shared abstractions published as packages, while keeping Terraform's state and workflow. The costs are an extra synthesis layer, so plan output and errors refer to generated JSON rather than your source; a much smaller community than either HCL or the AWS CDK; and no help at all with state management, which is still Terraform's problem. Pulumi is the alternative that replaces Terraform rather than wrapping it.

### Chainguard images
**Short:** Minimal, distroless-style container base images rebuilt continuously to keep CVE counts near zero, signed with SBOMs.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @1

These are minimal container images built from Wolfi, a distribution assembled specifically for containers with the `apk` package manager and none of the traditional userland baggage. Most variants ship no shell and no package manager at all, so the image is the application plus its runtime dependencies. Each is rebuilt frequently from source, signed, and published with an SBOM and provenance attestation, so `cosign` can verify what you are running and where it came from.

Reach for them when a scanner's CVE count is a gate you must pass, or when supply-chain attestation is a requirement, because starting from a near-empty base removes most findings by removing the packages that carry them. The costs are practical: no shell means debugging needs an ephemeral debug container, builds must move to a multi-stage pattern with the toolchain in an earlier stage, and the hardened catalog beyond the free tier is a paid product. Google's distroless images are the free neighbour.

### chart-testing
**Short:** CLI (ct) that lints and install-tests changed Helm charts in CI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, devtools/testing-and-mocking @2, devtools/static-analysis-and-linting @2

`ct` is the linting and testing harness the Helm chart community built for CI. It diffs a branch against its target to find which charts actually changed, then for each one enforces conventions, checking that the chart version was bumped and that `Chart.yaml` and its maintainers are valid, runs `helm lint` against every values file under `ci/`, and optionally installs the chart into a live cluster, waits for it to become ready and runs `helm test`.

Reach for it in a repository of many charts, where changed-chart detection is what stops the pipeline reinstalling everything on every commit; it pairs with `kind` to give each pull request a throwaway cluster. It is chart-repository tooling rather than general Kubernetes testing, so it tells you a chart installs and its tests pass, not that the rendered manifests satisfy policy, which is what a `conftest` or Kyverno step adds.

### Chef
**Short:** Agent-based, pull-model configuration management with procedural Ruby cookbooks.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

Chef converges a node by compiling and executing a run list of recipes written in a Ruby DSL. Resources such as `package`, `template` and `service` declare desired state and are idempotent, but the recipe around them is a real Ruby program executed in two phases, compile and then converge, which is the source of most Chef confusion. A `chef-client` agent runs periodically, fetching cookbooks and the node's attributes from a Chef Infra Server and reporting back, so drift is corrected on a schedule rather than only when someone runs a command.

It suits large, long-lived fleets of mutable machines where continuous enforcement and a searchable node inventory matter. The costs are the agent and server to operate and Ruby as a prerequisite for anyone touching infrastructure. Ansible is the lighter agentless alternative for the same work, and on cloud-native estates the whole model loses ground to immutable images built with Packer and replaced rather than converged.

### Cilium
**Short:** eBPF-based Kubernetes CNI delivering Pod networking, L3-L7 network policy, load balancing and a sidecar-free mesh.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/service-mesh-and-discovery @2, runtime-systems/io-networking-and-syscalls @2, security/authorization-and-policy @3

Cilium attaches eBPF programs to kernel hooks on each node, so forwarding, load balancing and policy enforcement happen in the kernel datapath with no iptables chains and no userspace proxy. That lets it replace kube-proxy outright and keep Service lookup cost flat as the number of services grows. Identity is a label-derived security identity rather than an address, so policy survives pod churn, and because eBPF can parse application protocols it enforces L7 rules such as an HTTP method and path, a Kafka topic or a DNS name. Hubble exposes the same datapath as flow-level observability.

Reach for it when policy must be identity-aware or protocol-aware, when iptables rule volume has become a scaling problem, or when you want mesh features without a sidecar per pod. The price is a recent kernel and a genuinely deep system to debug when something misbehaves; Calico is the simpler choice if plain NetworkPolicy is all you need.

### CircleCI
**Short:** Hosted CI/CD service running build, test and deploy pipelines defined in a YAML config inside the repository.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

`.circleci/config.yml` declares jobs, each running in an executor that is a Docker image, a Linux, macOS or Windows VM, or an Arm machine, and workflows wire jobs into a graph with fan-out, fan-in, manual approval gates and scheduled triggers. Orbs are versioned, shareable packages of jobs, commands and executors that collapse a common integration to a line. Caching is explicit and key-based, and test splitting distributes a suite across parallel containers using timing data from previous runs.

Reach for it when you want a hosted CI with strong parallelism and non-Linux support and no agents to run. The costs are credit-based pricing that rewards attention to resource classes, configuration that grows unwieldy without orbs, and lock-in to their YAML. If the code is on GitHub and needs nothing exotic, GitHub Actions is usually less machinery for the same outcome.

### client-go
**Short:** Official Kubernetes Go client with informers, listers and workqueues for building controllers and operators.
**Kind:** tech
**Lang:** go
**Roles:** platform-delivery/kubernetes-and-orchestration @1

client-go is the Go client the Kubernetes components themselves use. Beneath the typed clientsets sits the machinery that makes controllers viable: a reflector performs one list then a watch and feeds a delta queue, an informer maintains an in-memory cache of the objects it watches so reads never hit the API server, and a lister reads from that cache. Handlers do not act directly, they push a namespace and name key onto a rate-limited workqueue that a worker drains, which deduplicates repeated events and gives retries with backoff.

Reach for it directly when writing something low-level or when a framework's abstractions are in the way; for an ordinary operator, controller-runtime wraps exactly this machinery and removes hundreds of lines of wiring. Two invariants hold either way: never mutate an object obtained from a lister, because it is the shared cache, and watches can be dropped and resynced, so reconcile from observed state rather than assuming an event arrives exactly once.

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

A builder image contains an ordered set of buildpacks. The detect phase asks each whether it applies to the source tree, looking for a `go.mod`, a `package.json`, a `requirements.txt`, and the build phase runs those that do, each contributing layers. The output is an OCI image assembled by a lifecycle rather than by a Dockerfile, with metadata recording which buildpack produced which layer. That metadata enables rebase: when a base image is patched for a CVE, the application layers are re-pointed at the new base with no rebuild or recompile.

Reach for them on a platform serving many teams, where nobody should be writing or reviewing Dockerfiles and OS patching has to be driven centrally. The costs are less control and opaque debugging when detection picks the wrong thing, plus images usually larger than a hand-tuned multi-stage build. A team that needs precise control over layers should keep the Dockerfile.

### Cloud Run
**Short:** Google Cloud's serverless container runtime: request-driven autoscaling from zero with no cluster to manage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/container-and-image @2

You give Cloud Run a container image that listens on the port named in the `PORT` environment variable; it runs the container behind a managed HTTPS endpoint with a certificate, autoscales instances by concurrent requests, and scales to zero when idle. Unusually for a serverless platform, one instance handles many concurrent requests, so an efficient asynchronous server serves far more traffic per instance than a per-request model would. Revisions are immutable and traffic can be split between them by percentage or pinned by tag, which is how canaries and rollbacks work.

Reach for it for HTTP services, webhooks and event consumers where you want a container without a cluster, with jobs covering run-to-completion work. Cold starts are the standing cost, mitigated by a minimum instance count at the price of scale-to-zero, and CPU is throttled outside a request unless you keep it always allocated, which is what breaks background threads people assumed were still running.

### cloud-init
**Short:** First-boot instance bootstrap: applies user-data to configure users, packages, disks and network on cloud VMs.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @3

cloud-init runs on the first boot of a cloud VM, reads instance metadata from the provider's metadata service, and applies the user-data supplied at launch. That user-data is usually a `#cloud-config` YAML document with modules for creating users and injecting SSH keys, writing files, installing packages, formatting and mounting disks, and setting hostname and networking, or it can simply be a shell script. Stages run in a defined order, output lands in `/var/log/cloud-init-output.log`, and state under `/var/lib/cloud` is what stops per-instance modules re-running on reboot.

Reach for it for the minimum that makes a fresh instance reachable and identifiable, keys, hostname and an agent, and let a baked image or configuration management do the rest. Long user-data scripts are a poor place to build a machine: they run once, slow every boot, fail where only a log on the box can see it, and user-data has a size limit. Packer plus an immutable image is the alternative.

### Cloudability
**Short:** Third-party FinOps platform for multi-cloud cost allocation, rightsizing recommendations and IaC cost estimates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/infrastructure-as-code-and-config @3

Cloudability ingests billing data from AWS, Azure and Google Cloud and normalizes it into one model so multi-cloud spend can be compared and allocated. Its differentiator is business mapping rather than raw tags: rules attribute untagged and shared costs such as support charges, data transfer and a shared cluster to teams and products, which is what makes a chargeback number defensible. It also tracks reservation and commitment coverage and recommends purchases and rightsizing.

Reach for it in an organization with several clouds and a finance function whose allocation has to survive scrutiny. The costs are a commercial contract usually priced against spend, an integration effort, and allocation rules that need an owner or they drift. On a single cloud, that provider's native cost tooling plus an enforced tagging policy usually answers the same questions; Kubernetes-level allocation is better served by OpenCost or Kubecost.

### CloudHealth
**Short:** Third-party FinOps platform for multi-cloud cost visibility, allocation, and pre-deploy IaC cost estimates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

CloudHealth ingests billing and usage data from multiple clouds and organizes it into perspectives, its own grouping construct that assigns every resource to a business dimension such as team, product or environment from tags, account membership and rules. Because a perspective also covers resources that are untagged or shared, the allocation adds up to the whole bill rather than the tagged subset, and the same model drives budgets, reports and policy-based alerts.

Reach for it when cost reporting must satisfy finance across more than one cloud and native tools cannot express the organizational structure. It is a commercial platform with an onboarding project attached, and its numbers are only as good as the perspectives someone maintains. For a single-cloud estate, the provider's own cost explorer plus enforced tagging costs nothing and answers the same questions.

### CloudNativePG
**Short:** Kubernetes operator that runs PostgreSQL clusters with declarative failover, backups and rolling upgrades.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-access/replication-ha-and-backup @2, data-stores/relational @3

CloudNativePG runs PostgreSQL on Kubernetes without a StatefulSet: its operator manages the pods directly, elects a primary, and streams to replicas using Postgres's own physical replication. Failover is the operator promoting a replica and repointing the read-write Service, with a separate read-only Service for replicas. Backups go continuously to object storage as base backups plus archived WAL, which is what makes point-in-time recovery and bootstrapping a new cluster from a backup possible, and minor-version upgrades roll replicas first, then switch over.

Reach for it when Postgres has to live in the cluster, giving one platform, one RBAC model and GitOps for databases too, and you accept operating a database. The honest comparison is against a managed service, which removes this work entirely, and the operator does not remove the need for someone who understands vacuum, tuning and storage performance. A fast CSI class or local NVMe matters more than any operator setting.

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

The Cluster Operator watches the `Kafka` custom resource and its relatives and materializes them into broker and controller pods, per-node persistent volumes, internal Services and listeners, TLS certificates and each node's configuration. It also deploys the companion Topic and User operators and the optional Kafka Connect, MirrorMaker and Cruise Control components. A change to the custom resource becomes a rolling reconcile, so a configuration change or a version upgrade restarts brokers one at a time while respecting readiness.

It is the piece that makes running Kafka on Kubernetes tractable, and it is what you interact with rather than the brokers themselves. The tradeoff is the usual one for stateful workloads on Kubernetes: storage class performance, rack-aware placement and disruption budgets are yours to get right, and a broker rolled at the wrong moment is an availability event. A managed Kafka removes all of it if you are not committed to your own cluster.

### containerd
**Short:** OCI container runtime handling image pull, snapshots and container lifecycle; the default Kubernetes CRI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/kubernetes-and-orchestration @2

containerd is the daemon between the kubelet and the OCI runtime. It implements the CRI, so it handles image pull and unpack through content-addressed stores and snapshotters, manages container and task lifecycle, sets up networking through a CNI plugin, and delegates the final job of creating the process to `runc` or another OCI runtime by way of a shim process per container. That shim is what lets containerd itself be restarted or upgraded without killing running containers.

On any current Kubernetes distribution this is what actually runs your pods, and Docker's own engine uses it underneath as well. You touch it directly when debugging on a node, with `crictl` for the CRI view and `ctr` for the low-level one, and when configuring registry mirrors, authentication or an alternative runtime handler in its `config.toml`. CRI-O is the alternative, narrower in scope and the default on OpenShift.

### containers
**Short:** Running untrusted code (such as an MCP server) inside an isolated container as a sandbox boundary.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2, llm-apps/tool-use-and-mcp @2

The isolation is not a machine boundary. A container is a process on the host kernel, fenced by namespaces for what it can see, covering PIDs, mounts, network and users, and cgroups for what it can consume. Everything else, the kernel and every syscall it exposes, is shared, so the sandbox is only as strong as the syscall surface left reachable. In practice that means dropping capabilities, a read-only root filesystem, a non-root user with user-namespace remapping, no new privileges, a seccomp profile, and no network unless the workload needs one.

This is a reasonable boundary for code you distrust but that was not written to attack you, such as a generated script or a third-party tool server. It is the wrong boundary for genuinely hostile code, since a single kernel vulnerability crosses it; gVisor, Kata Containers or a Firecracker microVM give an intercepted-syscall or hypervisor boundary instead, at some startup and compatibility cost.

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

A cost allocation tag is an ordinary resource tag that has additionally been activated in the billing console, which is the step everyone forgets: until activation the tag exists on the resource but never appears as a column in cost reports, and activation is not retroactive. Two kinds exist, user-defined tags you apply and provider-generated ones such as the creating service, and both flow into the detailed billing report where one column per activated key lets spend be grouped by team, service or environment.

They are the foundation of any chargeback model and they fail in predictable ways. Keys are case-sensitive, so two spellings become two columns that each hold half the answer; not every resource type supports them, and shared costs such as data transfer, NAT gateways and support charges attach to nothing; and a resource created untagged can rarely be attributed after the fact. Enforce keys at creation through an organization tag policy or an IaC module rather than auditing later.

### Cost Anomaly Detection
**Short:** AWS service that models normal spend per account or service and alerts on statistically unusual cost spikes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @2

It builds a model of normal spend for each monitor you define, whether a whole account, a single service, a cost category or a tag-based slice, and evaluates recent charges against that baseline, raising an anomaly with a magnitude, a suspected root cause and a time window rather than a simple threshold breach. Alert subscriptions deliver those individually or as a daily or weekly digest, with an impact threshold so trivial deviations stay quiet.

It catches the class of problem budgets cannot: a change that is unusual rather than over a number, such as one service tripling while total spend is still under budget. Two limits matter, in that billing data lags by up to a day so this is detection after the fact rather than prevention, and a genuinely new workload looks exactly like an anomaly until the baseline adapts. Pair it with budgets, which cover the case it does not.

### crane
**Short:** go-containerregistry CLI to copy, inspect and mutate OCI images in a registry without a Docker daemon.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

crane is the CLI over go-containerregistry, and it speaks the registry API directly rather than through any container runtime. `crane copy` moves an image between registries by transferring only the blobs the destination lacks, without pulling it to local disk; `crane digest`, `manifest` and `config` inspect what is actually stored; `crane append` adds a layer and `crane mutate` rewrites entrypoint, environment or labels, producing a new image with no build step at all.

Reach for it for registry plumbing: mirroring images into an air-gapped environment, resolving a tag to a digest so a deploy is pinned, or checking which platforms a manifest list covers. It is neither a builder nor a runtime, and rewriting an image with `mutate` invalidates any signature over it. skopeo covers much the same ground with a different flag vocabulary and broader transport support.

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

CRI-O exists to implement the Kubernetes Container Runtime Interface and nothing else, so there is no build command, no general-purpose run command and no client CLI for everyday use. The kubelet calls it over the CRI socket; it pulls images, sets up the pod sandbox and its network through CNI, and hands off to an OCI runtime such as `runc` or `crun`, with a `conmon` process per container holding the terminal and exit status so the runtime can exit. Its versions track Kubernetes minor versions, making the pairing explicit.

The argument for it is scope: a smaller surface means less to secure and no features irrelevant on a Kubernetes node. It is the default on OpenShift and supported on most other distributions. containerd is the alternative and the more common default, with a broader ecosystem of snapshotters and plugins and use outside Kubernetes; for most clusters the distribution decides this rather than preference.

### crictl
**Short:** CRI-level CLI for inspecting and debugging containerd/CRI-O pods and containers directly on a node.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/kubernetes-and-orchestration @2

`crictl` talks to the CRI socket on a node, whether containerd's or CRI-O's, so it sees exactly what the kubelet sees: `crictl pods`, `crictl ps`, `crictl images`, `crictl logs` and `crictl inspect` over sandboxes and containers. That is the layer where you find out whether an image pull genuinely failed, whether a container exited before the kubelet reported it, or what a sandbox's state actually is.

Reach for it on a node when `kubectl` cannot help, because the API server is unreachable, the kubelet is misbehaving, or a pod is stuck in a state with no object-level explanation. Use it read-only in practice, since creating or removing containers behind the kubelet's back leaves the cluster and the node disagreeing. `ctr` goes one level lower into containerd's own namespaces, which is where images the kubelet did not pull become visible.

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

crun is an OCI runtime written in C doing the same job as `runc`: take an OCI bundle, set up namespaces, cgroups, seccomp and capabilities, and execute the container process. Being C rather than Go it carries no language runtime or garbage collector, so it starts faster and its per-container memory footprint is smaller, which matters most at high container density. It has fuller cgroup v2 support and can also run WebAssembly workloads through embedded runtimes.

Swap it in when container startup latency or per-container memory overhead is measurable, which shows up on nodes packing hundreds of short-lived containers. It is a drop-in for `runc` in containerd or CRI-O through a runtime handler, and it is the default in Podman on Fedora-family systems. `runc` remains the reference implementation and the more widely deployed default, so it is the safer choice without a specific reason.

### ctr
**Short:** containerd's low-level debugging CLI for inspecting images, containers and namespaces beneath Kubernetes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/kubernetes-and-orchestration @3

`ctr` ships with containerd as a debugging client for containerd's own API, below the CRI layer. Everything in containerd lives in a namespace and Kubernetes uses `k8s.io`, which is why `ctr images ls` looks empty until you add `-n k8s.io`, the single most common confusion with the tool. From there it lists and pulls images, inspects content and snapshots, and can create tasks directly.

Reach for it when you need containerd's own view: verifying that an image and its layers are really present on a node, testing registry credentials or a mirror configuration, or importing an image tarball onto an air-gapped node. It is explicitly unsupported as an operational interface and bypasses the kubelet entirely, so anything it creates is invisible to Kubernetes. Use `crictl` for the pod and container view the kubelet shares.

### CUDs
**Short:** Google Cloud committed use discounts: lower rates in exchange for a one- or three-year usage commitment.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

Google Cloud sells two shapes. A resource-based commitment pledges a quantity of vCPUs and memory in a specific region and machine family for one or three years and discounts that capacity; a spend-based commitment pledges an hourly dollar amount on a service and applies across machine types and regions. Neither reserves capacity, since they are billing constructs rather than a guarantee that an instance is available, and discounts apply automatically to matching usage rather than to named instances.

Buy them for the steady baseline you are certain of, and leave the variable layer on-demand or Spot. The risk is that a resource-based commitment is largely non-cancellable and tied to a family and region, so a migration to a newer machine type or another region leaves you paying for capacity you no longer use; flexible spend-based commitments trade a smaller discount for far less lock-in. Measure the trough of your usage, not the average, before committing.

### Direct Connect
**Short:** AWS dedicated private network link between on-premises data centres and a VPC, bypassing the public internet.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

Direct Connect is a physical cross-connect from your router to an AWS router at a colocation facility, either a dedicated connection at 1, 10 or 100 Gbps or a smaller slice through a partner. On top of the physical link you create virtual interfaces: a private one reaches a VPC through a Direct Connect gateway, a transit one reaches a Transit Gateway, and a public one reaches AWS public endpoints. Routing is BGP, so failover and path preference are yours to configure.

Reach for it for consistent latency and high sustained throughput between a data centre and AWS, and because data transfer out over Direct Connect is billed far below internet egress, which for large steady volumes is often the whole business case. The costs are lead time measured in weeks, a port fee whether you use it or not, and no redundancy from a single connection; a site-to-site VPN is the fast, cheap alternative and the usual backup path.

### distroless
**Short:** Google base images containing only the app and its runtime deps, with no shell or package manager, cutting CVE surface.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2

These are base images built by Google from Debian packages containing only what an application needs to run, meaning glibc, CA certificates, timezone data and a language runtime where relevant, with no shell, no package manager and no coreutils. Variants exist per language, each with a debug tag that adds a BusyBox shell when you need one and a nonroot tag that sets a non-root user by default. They are meant as the final stage of a multi-stage build, with compilation happening in a full image before the artifact is copied across.

Reach for them to shrink both image size and CVE surface, since a scanner cannot report a vulnerability in a package that is not present,. The costs are debugging, because there is no shell to exec into and you need an ephemeral debug container, and the requirement that your binary's dynamic linking match what the image provides. Chainguard's images and a statically linked binary on `scratch` are the neighbouring options.

### dive
**Short:** TUI that explores a container image layer by layer and reports wasted space.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

dive opens a built image and shows, layer by layer, which files each one added, changed or removed, alongside an efficiency score and a total for wasted space. The waste it finds is almost always the same pattern: a file created in one layer and deleted or overwritten in a later one still occupies space forever, because layers are additive and a deletion is only a whiteout entry. It also runs non-interactively in CI with a threshold that fails the build.

Reach for it when an image is larger than it should be and you want to know which instruction is responsible rather than guessing. It diagnoses and does not fix: the remedies are combining the install and cleanup into one `RUN`, moving build tooling into an earlier multi-stage stage, and choosing a smaller base. For images already assembled as a slim final stage on distroless or scratch there is usually nothing left for it to find.

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

Multi-platform is the headline capability: `--platform linux/amd64,linux/arm64` builds each architecture and pushes a single manifest list, so one tag serves Apple Silicon laptops, Arm server nodes and x86 hosts alike. Underneath, the foreign architecture is either emulated through QEMU and binfmt or, far faster, built natively by adding a remote builder node of that architecture to the same builder instance.

Use it whenever consumers of an image sit on more than one architecture, and prefer native nodes over emulation for anything that compiles, where QEMU can be an order of magnitude slower. Remember that a multi-platform result cannot be loaded into the local daemon's image store and has to be pushed to a registry, and that a registry-backed cache shared across CI jobs is what stops every build starting cold.

### Docker Swarm
**Short:** Docker's built-in container orchestrator: simpler than Kubernetes, with services, overlay networks and rolling updates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/container-and-image @3

Swarm mode is built into the Docker engine: `docker swarm init` turns a daemon into a manager, managers hold cluster state in a Raft log, and workers run tasks. You deploy a stack from a Compose file, and a service declares an image, a replica count or global mode, and an update policy. The built-in routing mesh publishes a service port on every node and load-balances to its tasks, and an encrypted overlay network plus secrets and configs are part of the engine rather than add-ons.

Reach for it when a small team needs multi-host container scheduling with almost no learning curve and the whole system is already Compose files. Be clear-eyed about momentum: development is minimal, the ecosystem has largely moved on, and managed offerings and tooling target Kubernetes. Anything expected to grow or to outlive the team that built it is safer on Kubernetes or a managed container service.

### docker-slim
**Short:** Watches a container at runtime and rebuilds a minified image of only what was used, shrinking attack surface.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @3

The build command starts the original image, exercises it through its own probes, an HTTP probe, or a command you supply, and records with kernel-level tracing exactly which files the process actually touched. It then constructs a new image containing only those files, frequently an order of magnitude smaller, and can emit seccomp and AppArmor profiles derived from the syscalls it observed. The project is now developed as SlimToolkit.

Reach for it when you cannot rebuild an image properly, such as a vendor image or a legacy application whose dependencies nobody can enumerate. The risk is inherent in the method: anything exercised only on a code path the probe never hit is deleted, so a rarely used feature fails in production with a missing-file error long after the build. Test the slimmed image against a real suite, and prefer a multi-stage build onto a distroless base whenever you control the Dockerfile.

### EC2
**Short:** AWS elastic compute: virtual machines, instance families, auto scaling groups and spot capacity.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

An instance is a virtual machine launched from an AMI onto a chosen instance type, where the family letter encodes its bias toward general purpose, compute, memory, storage or accelerators, and the generation and size follow. Storage is either an EBS volume over the network, which survives a stop, or instance store on local NVMe, which does not. Purchase options are the real cost lever, spanning on-demand by the second, Spot at a steep discount with a short interruption notice, and Savings Plans or reservations for the committed baseline. Instance metadata plus an attached IAM role supplies credentials with no key on disk.

It remains the right answer when you need control of the operating system, a specific kernel, licensed software, or long-running processes a function cannot host. What it costs is everything above the hypervisor, since patching, image building, autoscaling and configuration management are yours. For a stateless container, ECS, EKS or Lambda removes most of that.

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

`envsubst` reads text on stdin and replaces variable references with values from the environment, writing the result to stdout. That is the whole program, with no conditionals, no loops, no defaults and no includes, and by default any variable that is unset expands to an empty string, silently. Passing a list of names as an argument restricts substitution to those, which is how you stop it consuming dollar sequences that belong to the target format.

Reach for it when a config file needs a handful of values injected at container start or in a CI step and pulling in a templating engine would be absurd; a common entrypoint pattern renders an nginx or application config from environment variables before exec'ing the real process. Its failure mode is that empty expansion produces a syntactically valid but wrong file, so set the shell's nounset option and validate the output. Anything with logic belongs in Helm, Kustomize or a real template engine.

### etcdctl
**Short:** CLI for etcd: inspect keys, check cluster health, and take or restore the snapshot that backs up a control plane.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-access/replication-ha-and-backup @2, data-access/transactions-and-consistency @3

`etcdctl` is etcd's client, and against the v3 API it exposes the key-value surface: `get` with `--prefix` walks a keyspace, `endpoint status` and `endpoint health` report each member's role, database size and Raft term, and `member list` shows cluster composition. `snapshot save` writes a point-in-time copy of the whole keyspace and `snapshot restore` builds a new data directory from one. Every command needs the client certificates, which on a kubeadm node sit under `/etc/kubernetes/pki/etcd`.

On a self-managed control plane this is the backup tool, because a Kubernetes cluster's entire state lives in etcd and a snapshot plus the certificate authority is what restoring it requires. It is also the diagnostic for the failures that take an API server down: a member behind on Raft, a database that hit its space quota and went read-only, slow disk showing as elevated fsync latency. Do not write keys directly, since Kubernetes owns that encoding and nothing validates it below the API server.

### Feature flags
**Short:** Runtime switches that decouple deploy from release, enabling dark launches, gradual rollout and instant rollback.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

A flag is a conditional in running code whose value comes from configuration rather than a deploy, which separates two things usually welded together: shipping the code and turning the behaviour on. Lifespans vary widely, from a release toggle that lasts days to an operational kill switch that outlives the subsystem. Evaluation is normally in-process against rules fetched from a service, so it costs no network call on the request path.

They are what makes trunk-based development and continuous deployment safe, and they turn a rollback from a rebuild into a configuration change. The cost is combinatorial, since every live flag doubles the number of states and no test suite covers the product of a dozen of them. Give every flag an owner and a removal date, treat a stale flag as a defect, and keep flags that gate risk separate from those that gate entitlement.

### Firecracker
**Short:** AWS microVM monitor giving VM-grade isolation with ~125ms boot; the substrate under Lambda, Fargate and agent sandboxes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @2, llm-apps/agentic-environments @3, platform-delivery/cloud-platform-and-cost @3

Firecracker is a virtual machine monitor on KVM that deliberately implements almost no devices, offering a virtio block device, a virtio network device, a serial console and a one-button keyboard controller. That minimal model is why a microVM boots in roughly a hundred milliseconds with a few megabytes of overhead, and why its attack surface is small enough to trust between tenants. Each microVM is a process configured through a REST API, jailed with seccomp and cgroups, and snapshot and restore let a fully booted guest be resumed rather than started.

Reach for it when you need a hardware isolation boundary with container-like startup, which is the shape of multi-tenant function and container platforms and of sandboxes running untrusted or model-generated code. It is a building block rather than a product, since you supply the kernel image, root filesystem, networking and an orchestrator. Kata Containers packages the same idea behind an OCI runtime, and gVisor trades hardware isolation for a userspace kernel with no VM at all.

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

Flagsmith models flags per environment inside a project, so the same flag holds independent values in development, staging and production, and a flag carries both a boolean state and an optional string or JSON value, which lets one switch also deliver configuration. Segments are rule sets over user traits that override the default, and individual identities can be overridden directly. SDKs either evaluate locally against a fetched ruleset or query the API per identity, and the server can be self-hosted, which is frequently why it is chosen.

Reach for it when you want release decoupling without a commercial contract, or with flag data staying inside your own infrastructure. Self-hosting means running and scaling the service and its database, and making sure an SDK that cannot reach it falls back to a safe default. LaunchDarkly is the managed alternative with a deeper experimentation and governance story; Unleash is the closest open-source neighbour.

### Flannel
**Short:** Simple Kubernetes CNI plugin giving every pod a routable IP via a VXLAN or host-gw overlay network.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, runtime-systems/io-networking-and-syscalls @2

Flannel does one thing: give every pod an address from a per-node subnet and get packets between nodes. A `flanneld` agent on each node claims a subnet out of the cluster CIDR, records it through etcd or the Kubernetes API, and configures a backend, which is VXLAN by default, encapsulating pod traffic in UDP between node addresses, or host-gw, which simply adds routes and requires the nodes to share one layer-2 network.

Reach for it when you want pod networking to be boring and comprehensible: a small cluster, a lab, an edge deployment. What it does not do is policy, since it implements no NetworkPolicy at all, so a cluster on plain Flannel has no pod-to-pod segmentation whatsoever, which surprises people who assumed the objects they applied were being enforced. Calico or Cilium is the answer when policy, observability or performance matter.

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

Three controllers do the work. The image-reflector controller scans a registry repository and maintains the list of tags, an image policy object selects one according to a semver range, a numeric or alphabetical ordering, or a tag filter, and the image-automation controller rewrites the manifests in Git wherever a policy marker sits beside an image field, then commits and pushes. The ordinary Flux reconcile then performs the deploy.

This keeps Git as the single source of truth while removing the manual tag bump after every build, and the commit becomes a reviewable, revertible record of the deploy. It requires write credentials to the repository, so scope that deploy key tightly and consider committing to a branch that a pull request gates for production. Tag scanning against a large repository costs registry API calls, and an immutable tagging scheme is a prerequisite for any of it to be safe.

### Fly.io Machines
**Short:** Fly.io's API-driven microVMs that boot in under a second, used for per-request sandboxes and agent workloads.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, llm-apps/agentic-environments @2

A Machine is a Firecracker microVM created directly through an API from an OCI image, with a chosen CPU, memory and region and an explicit lifecycle you drive: start, stop, destroy. A stopped machine can be woken by an incoming request in well under a second because its state is suspended rather than rebuilt, and billing follows running time, so an idle stopped machine costs only its volume. Machines are placed in named regions close to users and each gets a private address on a WireGuard mesh.

Reach for it when you want per-tenant or per-session compute, such as a sandbox for an agent, a preview environment or a stateful session, that you can create and destroy programmatically without a scheduler. The tradeoff is that you are the scheduler: nothing places, heals or scales machines unless you write it or move up to the higher-level app abstraction, and a machine stays pinned to the region and volume it was created with.

### Gateway API
**Short:** Kubernetes successor to Ingress: role-oriented CRDs for L7 routing, TLS termination and traffic splitting.
**Kind:** spec
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, traffic-edge/api-gateway @2, traffic-edge/proxy-and-load-balancer @3

Gateway API splits what Ingress crammed into one object across three roles. A `GatewayClass` is the implementation, supplied by whoever runs the infrastructure; a `Gateway` is a deployed listener with ports, protocols and TLS, owned by the cluster operator; and `HTTPRoute`, `GRPCRoute` and their siblings are owned by application teams and attach to a Gateway, subject to rules the Gateway sets about which namespaces may attach. Routing is expressive where Ingress was not, with matching on headers, methods and query parameters, weighted backends for canaries, and request and header filters as typed fields rather than controller-specific annotations.

Reach for it for new ingress, particularly where routing is shared between teams or where traffic splitting previously happened through annotations nobody could port. Ingress is not removed and keeps working, so migration is incremental. The caveat is that implementations differ in which parts of the spec they support and how mature the experimental channel features are, so check your controller before designing around a feature.

### gcloud
**Short:** Google Cloud's CLI for creating, inspecting and scripting every GCP resource and service.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, devtools/version-control-and-workbench @3

`gcloud` is organized as `gcloud <group> <command>` over the same APIs the console uses, with `--format` and `--filter` on essentially every command for scripting. Named configurations hold a project, region and account so switching context is one command, and the distinction between `gcloud auth login` and `gcloud auth application-default login` is worth internalizing, since the first authenticates the CLI while the second writes the credentials client libraries and Terraform pick up. Components such as `kubectl` and beta command sets install through it.

Reach for it for exploration, one-off administration and the credential bootstrapping other tools depend on, with `gcloud container clusters get-credentials` the command everyone runs. Resist building environments with it, because imperative commands leave no reproducible artifact, which is what Terraform or Config Connector is for.

### GCP Budgets
**Short:** Google Cloud budget objects that track per-team spend against a threshold and fire alerts or automation.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/alerting-and-incident-response @3

A budget is a Cloud Billing object scoped to a billing account and optionally filtered to projects, services, labels or a credit treatment, set either to a fixed amount or to last period's spend. Threshold rules fire on actual or forecast spend at percentages you choose, and beyond email they can publish to a Pub/Sub topic, which is the interesting path because a function subscribing to it can act, up to detaching the billing account from a project to hard-stop a runaway.

Use one per project or team on top of a label convention. The usual caveats apply: cost data is not real time, a budget is an alert rather than a cap by default, and the Pub/Sub shutdown pattern is powerful enough to take down production if pointed at the wrong project. Quotas are the mechanism that actually limits consumption; budgets tell you it happened.

### GCP client libraries
**Short:** Google Cloud's official SDKs, shipping default retry, backoff and deadline behaviour you tune rather than rewrite.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, traffic-edge/rate-limiting-and-resilience @2

Google publishes two generations and knowing which you hold matters: the older discovery-generated libraries are produced uniformly from API description documents, while the newer Cloud Client Libraries are handwritten per service over gRPC with idiomatic types. Credentials come from Application Default Credentials, which checks an environment variable, then the gcloud user credentials, then the metadata server on a Compute Engine, GKE or Cloud Run workload, so attaching a service account to the workload replaces a key file entirely. Retry, timeout and backoff are configured per call or per client, and gRPC channels are pooled and reused.

Prefer the Cloud Client Library for any service that has one. The default retry policy only retries operations the service declares idempotent, so a write that fails ambiguously is yours to make safe, and deadlines should be set explicitly because an unbounded gRPC call waits far longer than any user will.

### GHCR
**Short:** GitHub Container Registry: OCI registry wired to repo permissions and Actions tokens.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, platform-delivery/ci-cd-and-release @3

GHCR stores images under a path owned by a user or organization rather than by a repository, which is why visibility and access are configured on the package itself and can then be linked back to a source repository. Inside Actions the automatically provided token can push to it given the right packages permission, so no separate registry credential is needed, and outside Actions a personal access token with package scopes authenticates.

Reach for it when the code already lives on GitHub and you want images beside it with no extra account, since public images pull anonymously without the rate limits Docker Hub imposes and it stores OCI artifacts such as Helm charts too. What it does not give you is policy, so there is no blocking a pull on a critical CVE, no signature enforcement and no replication topology; a regulated pipeline still wants Harbor or a cloud registry, and pulls from a cloud provider's nodes cross the internet instead of staying in-region.

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

The container registry is part of a GitLab project, so image paths mirror the project path and permissions are the project's: whoever can read the repository can pull. CI receives short-lived registry credentials injected into every job, which makes pushing a few lines of `.gitlab-ci.yml`, and cleanup policies expire tags by regex and age. The same project also hosts package registries for Maven, npm, PyPI and others plus a dependency proxy that caches upstream Docker Hub images.

Reach for it when GitLab is already the platform, giving one permission model, one audit trail, and a one-click path from an image back to the pipeline that built it. Self-managed instances need attention to registry storage, since garbage collecting untagged layers is a maintenance task rather than something that happens by itself. If your runtime is a cloud provider's, its own registry keeps pull traffic in-region and integrates with its IAM.

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

Goldilocks creates a Vertical Pod Autoscaler object in recommendation mode for every workload in a labelled namespace, then presents what the VPA computed as a dashboard: for each container, the requests it would suggest at a guaranteed quality of service and at a burstable one, next to what is configured today. The VPA never acts and nothing is resized, so it is safe to run in production, and the numbers come from real observed usage over the recommender's window.

Reach for it as the answer to what these requests should be, which is the question sitting behind both cluster cost and mysterious evictions. Two limits: recommendations are only as good as the window observed, so a workload with a monthly peak or a recent traffic change will be undersized by them, and it says nothing about replica count, which is the HPA's business. Treat the dashboard as input to a decision a human makes.

### Google Artifact Registry
**Short:** Google Cloud OCI and language-package registry with IAM controls and scan-on-push.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, devtools/build-and-dependency-management @2

Artifact Registry is the successor to Container Registry and handles more than images, storing Docker and OCI artifacts alongside Maven, npm, Python, Go, Debian and RPM packages, each in a repository that is a regional or multi-regional resource with its own IAM policy. Repositories can also be remote, proxying and caching an upstream such as Docker Hub or PyPI, or virtual, presenting several repositories behind one endpoint. Vulnerability scanning runs on push, and Binary Authorization can then refuse to deploy an image that fails an attestation policy.

Reach for it for anything running on Google Cloud, where per-repository IAM, regional locality with GKE and Cloud Run, and the Binary Authorization gate are the reasons to prefer it over a generic registry. It is provider-specific, and the remote-repository and scanning features carry charges on top of storage and egress, so a small project publishing public images may be better served by GHCR.

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

### helm-diff
**Short:** Helm plugin that renders the diff between the released manifests and a pending upgrade.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/ci-cd-and-release @2

The plugin renders what an upgrade would apply and compares it against the manifests recorded for the current release, printing a per-object diff. `helm diff upgrade` is the command that matters, and a three-way merge option compares against live cluster state instead, which is what catches drift someone introduced with `kubectl edit`. It also diffs one revision against another, and it exits non-zero when there are changes so CI can gate on it.

Reach for it before every production upgrade, because a values file tells you nothing about what a chart version bump changed inside the templates, whether a new default, a removed field or a rewritten resource. Argo CD and Flux show the equivalent diff natively, so the plugin matters most for teams driving Helm from a pipeline or a laptop. It renders locally, so whatever a mutating admission webhook or a controller adds after apply will not appear.

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

Humanitec is a platform orchestrator. Developers write a workload specification, with Score the open format it promotes, declaring what the application needs, such as a Postgres database, a queue and some environment variables, and no environment-specific detail at all. The orchestrator resolves that against per-environment resource definitions the platform team owns, which decide that Postgres means a shared instance in staging and a provisioned managed instance in production, then generates the manifests or Terraform and deploys them.

Reach for it when a platform team wants a contract with developers instead of a folder of copied Helm values per environment, and wants environment-specific decisions and credentials to stay on their side of that line. It is a commercial product with an adoption cost, and it inserts another abstraction between an engineer and the manifests they end up debugging. Assembling the same pattern from Crossplane, Kustomize and Argo CD is the alternative teams weigh it against.

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

k9s renders the cluster as a live terminal UI: a resource view refreshes continuously, a colon command jumps to any resource kind, a slash filters, and single keys perform the common operations of logs, describe, edit, port-forward, shell into a container, delete and scale. It reads your kubeconfig and honours context switching, and RBAC constrains it exactly as it constrains `kubectl`, since it is making the same API calls. Plugins bind arbitrary commands to keys, and skins and aliases are configurable.

Reach for it for interactive investigation, where its advantage over `kubectl` is that state updates in front of you instead of requiring another command, whether watching a rollout, following a pod through its restarts, or comparing containers' logs. Keep it out of automation and out of production change management, because it puts destructive actions one keystroke away in whichever context happens to be current; scripts and reviewed manifests should still do the writing.

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

Kata is an OCI runtime that, instead of creating a namespaced process, boots a lightweight virtual machine per pod and runs the containers inside it under their own guest kernel. A hypervisor such as Firecracker, Cloud Hypervisor or QEMU provides the boundary, an agent inside the guest manages the workload, and virtio devices plus a shared filesystem connect it back to the host. Kubernetes selects it per pod through a `RuntimeClass`, so a cluster can run most pods on `runc` and only the sensitive ones under Kata.

Reach for it for multi-tenant clusters, untrusted or customer-supplied workloads, and anything where a kernel vulnerability crossing between tenants is unacceptable. The costs are startup latency and memory overhead per pod, plus friction from the separate kernel, since device access, host-path mounts and GPU passthrough all need explicit configuration and the node must support nested virtualization where applicable. gVisor is the lighter alternative, intercepting syscalls in userspace rather than booting a VM.

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

Knative Serving turns a container into a request-driven service. A `Service` resource creates an immutable `Revision` on every change, a queue-proxy sidecar in each pod reports concurrency to the autoscaler, and the autoscaler drives replica count from requests in flight rather than CPU, down to zero, where an activator buffers an incoming request while a pod starts so a request to a scaled-to-zero service waits rather than failing. Traffic splits between revisions by percentage, which gives canary and rollback with no extra tooling, and Knative Eventing is the separate half delivering CloudEvents through brokers and triggers.

Reach for it when you want Cloud Run-style semantics on your own cluster, on a cloud that does not offer them, or across clouds. The costs are a sidecar per pod, a networking layer to install and operate, and cold starts whenever it scales to zero. KEDA is the lighter option if all you need is event-driven scaling of ordinary Deployments.

### kopf
**Short:** Python Kubernetes operator framework: decorate handlers on CRD events instead of writing a Go controller.
**Kind:** tech
**Lang:** python
**Roles:** platform-delivery/kubernetes-and-orchestration @1

kopf is a Python framework in which an operator is a module of decorated functions: handlers for a custom resource's create, update and resume events, a field-level handler for a specific path, plus timers and daemons for periodic or long-running work. The framework runs the watch loop, retries a handler that raises with backoff, records progress and errors as status and Kubernetes events, and manages finalizers so deletion handlers actually run.

Reach for it when the operator's logic is glue around Python libraries, such as a cloud SDK, a data tool or a machine-learning stack, and rewriting those in Go would be the bulk of the work. The tradeoffs are performance and idiom: the Python watch client is heavier than an informer-backed Go controller at high object counts, and the event-handler style makes it easy to write logic that assumes an event rather than reconciling from observed state. controller-runtime with Kubebuilder is the mainstream path for anything long-lived.

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

kubeadm does the control-plane bootstrap and nothing beyond it. `kubeadm init` generates the certificate authority and every certificate, writes static pod manifests for the API server, controller manager, scheduler and etcd into `/etc/kubernetes/manifests` for the kubelet to run, sets up the bootstrap token, and prints the `kubeadm join` command that adds nodes. `kubeadm upgrade` manages version skew between components across a minor upgrade, and `kubeadm certs` renews certificates that otherwise expire after a year.

Reach for it when you must run your own control plane, whether on-premises, air-gapped or on hardware no managed service covers, and want the conformant community-supported way rather than a bespoke script. It deliberately omits everything else a cluster needs, so a CNI plugin, storage, load balancing in front of a multi-master API server and etcd backups are all yours. The forgotten certificate renewal is the classic outage, and a managed control plane exists precisely to avoid all of this.

### Kubebuilder
**Short:** Scaffolding framework for writing Kubernetes operators in Go: generates CRD manifests and controller boilerplate.
**Kind:** tech
**Lang:** go
**Roles:** platform-delivery/kubernetes-and-orchestration @1, devtools/compiler-toolchain-and-codegen @3

Kubebuilder scaffolds a Go operator project: the init command lays out the module, Makefile, Dockerfile and manager entrypoint, and the create api command generates the Go types for a custom resource plus a controller stub. Code generation then does the tedious part, with `controller-gen` deriving the CRD OpenAPI schema including validation and defaulting from struct tags on your types, and producing deepcopy methods and RBAC manifests from markers in comments. The runtime underneath is controller-runtime; Kubebuilder is the layout and codegen around it.

Reach for it for any nontrivial Go operator, since hand-writing a CRD schema and keeping it in step with the Go types is a job nobody should do twice. It is opinionated about project layout and assumes Kustomize for deployment, which is friction if your organization standardizes on Helm. Operator SDK builds on the same foundation and adds Ansible and Helm based operators plus OLM packaging.

### Kubecost
**Short:** Allocates Kubernetes spend to namespaces, workloads and teams, and flags idle or oversized requests.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/kubernetes-and-orchestration @2

Kubecost joins Kubernetes usage with cloud billing: it reads pod resource requests and actual usage from Prometheus, works out each pod's share of its node's cost, adds attached volumes, load balancers and network transfer, and rolls the result up by namespace, controller, label or team. Because it holds both requests and usage it can quantify the gap between them, which is where the idle spend in most clusters lives, and it recommends request changes and cluster-level savings such as different instance types or Spot.

Reach for it when a shared cluster's bill has to be attributed to the teams inside it, which no cloud cost tool can do since it sees only nodes. Its numbers are estimates, because shared and idle capacity is allocated by a rule you choose and accuracy depends on the billing integration being connected. OpenCost is the CNCF project at its core and is free; the commercial product adds long-term storage, multi-cluster aggregation and support.

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

KubeRay runs Ray on Kubernetes through three custom resources. `RayCluster` manages a head pod and autoscaling worker groups; `RayJob` creates a cluster, submits a job and can tear the cluster down when it finishes; and `RayService` manages a Ray Serve deployment with a zero-downtime update by standing up a new cluster and switching traffic across. Worker groups carry their own pod templates, so heterogeneous CPU and GPU pools live inside one logical Ray cluster, and the Ray autoscaler drives the operator to add pods when tasks or actors request resources.

Reach for it when Ray workloads such as distributed training, tuning, batch inference and serving have to share a Kubernetes platform rather than run on dedicated machines. The costs are two schedulers with different views of resources, which is why gang scheduling through Kueue or Volcano matters for training jobs, and the operational reality that the head pod is a single point of failure for its cluster.

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

Kueue sits in front of the Kubernetes scheduler and decides when a job may start rather than where its pods go. Jobs are created suspended; a local queue in the namespace points at a cluster queue holding nominal quota per resource flavour, and Kueue unsuspends a job only when its whole resource ask fits inside that quota. That all-or-nothing admission is what stops half a distributed training job running while the rest waits. Cluster queues in a cohort borrow unused quota from each other, and it understands batch jobs, JobSet, Kubeflow training jobs and Ray jobs.

Reach for it on a shared GPU cluster where teams need guaranteed shares and partially admitted jobs would waste expensive accelerators. It queues rather than preempts by default, so a long-running job still holds the quota it took, and it governs jobs rather than long-lived services. Volcano is the alternative when you also want a batch scheduler making placement decisions such as GPU topology awareness.

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

Powertools is a per-language library of the handler concerns everybody re-implements: a logger emitting JSON with the request id and cold-start flag injected, a tracer wrapping the tracing SDK around handlers and clients, a metrics utility writing Embedded Metric Format so CloudWatch derives metrics from a log line with no extra API call, an idempotency decorator persisting a hash of the payload so a retried event is not processed twice, batch processing that reports partial failures so only the failed records return to the queue, plus cached parameter fetching and typed event classes.

Reach for it on any nontrivial function, since the idempotency and partial-batch-failure utilities in particular fix bugs that are easy to get wrong and hard to notice. The costs are package size and the cold-start time of the extra imports, plus a set of conventions your code now follows. It is provider-specific by design, so a codebase meant to run elsewhere should keep its own abstractions.

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

Lens is a desktop application that reads your kubeconfig files and presents every context as a browsable cluster, showing workloads, events, configuration and RBAC in a resource tree with built-in log streaming, a terminal preloaded with the right context, and port forwarding. It installs metrics into the cluster or reads an existing Prometheus to draw CPU and memory graphs per node, pod and container, and extensions add views for custom resources.

Reach for it when several clusters need watching at once, or when people who are not fluent in `kubectl` still need visibility. Be careful about exactly that convenience, because it holds credentials for every cluster in your kubeconfig on a laptop, and editing a live resource in a UI bypasses whatever review the manifests in Git go through. k9s covers similar ground in a terminal, and recent releases moved parts of Lens behind a paid subscription, which pushed some teams toward alternatives such as Headlamp.

### local-path-provisioner
**Short:** Rancher provisioner creating PersistentVolumes from node-local disk; simple dynamic storage for dev clusters.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-stores/object-and-file-storage @2

It is a dynamic provisioner that satisfies a claim by creating a directory on whichever node the pod is scheduled to and binding a host-path PersistentVolume to it. Its StorageClass uses `WaitForFirstConsumer` binding, so the volume is not created until the scheduler has picked a node, since otherwise the volume and the pod could land in different places. It ships as the default StorageClass in K3s and is a one-manifest install elsewhere.

Reach for it when a cluster needs dynamic provisioning to exist at all, such as a single-node development cluster, a throwaway CI cluster or an edge box with no CSI driver available. Understand what you gave up: the data lives on one node's disk, so a rescheduled pod finds an empty volume, there is no replication, no snapshot and no capacity enforcement, and losing the node loses the data. Anything with a durability requirement needs a real CSI driver or a replicated storage layer.

### Metacontroller
**Short:** Kubernetes add-on that lets you write operators as simple webhooks, avoiding a full controller-runtime codebase.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

Metacontroller is a controller that runs other controllers for you. You declare a composite or decorator controller naming a parent resource, the child kinds it manages and a webhook URL; Metacontroller watches those objects and, on every change, posts the parent's current state and its observed children to your webhook as JSON. The webhook returns the list of children it wants to exist, written in any language, and Metacontroller performs the creates, updates and deletes that make reality match.

Reach for it when the operator is genuinely a transformation, where this custom resource should expand into these Deployments and Services, and you want it in Python, JavaScript or a few lines of shell rather than a Go project. The limits follow from the model: your function is stateless and sees only the declared parent and children, so anything needing side effects outside Kubernetes, fine-grained status handling or its own caching wants a real controller built on controller-runtime.

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

Network Connectivity Center models connectivity as a hub with spokes, where a spoke is a VPC, a Cloud VPN tunnel, a Cloud Interconnect attachment or a router appliance instance. Attaching spokes to a hub gives transitive reachability between them, which plain VPC peering explicitly does not provide, and route exchange between spokes is handled through Cloud Router and BGP rather than by hand-maintained routes in every network.

Reach for it once the number of VPCs and on-premises sites makes a full mesh of peerings unmanageable, or the moment a hybrid site needs to reach more than one VPC. The constraints are familiar to any hub-and-spoke design: overlapping CIDRs still cannot be joined, so address planning comes first, and centralizing traffic through a hub concentrates both cost and failure. Shared VPC is the simpler answer when the real requirement is one network shared across projects.

### NetworkPolicy
**Short:** Kubernetes object declaring which pods may talk to which, enforced by the CNI for network segmentation.
**Kind:** api
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, security/supply-chain-and-runtime-security @2, runtime-systems/io-networking-and-syscalls @3

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

The plugin runs as a DaemonSet on GPU nodes and implements the Kubernetes device plugin API: it discovers the GPUs present, advertises them to the kubelet as an extended resource, and on allocation tells the kubelet which device to expose to the container, which the NVIDIA container runtime then mounts along with the driver libraries. Because it is an extended resource, a GPU is an integer request with no overcommit, so limits equal requests and a fraction cannot be asked for.

That indivisibility is the thing to plan around. Time-slicing configures the plugin to advertise several replicas of one physical GPU, letting small inference pods share a card at the cost of context switching and no memory isolation between them, while MIG partitions a supported card into hardware-isolated instances advertised as separate resources. It is one component of the node stack alongside the driver, toolkit and monitoring, which is why most clusters install the GPU Operator rather than this alone.

### NVIDIA GPU Operator
**Short:** Kubernetes operator installing the whole GPU node stack: driver, container toolkit, device plugin, DCGM, MIG.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, gpu/multi-gpu-and-collectives @3, ml-lifecycle/ml-platform-and-pipelines @3

The operator installs and manages every layer a GPU node needs, so nodes can start as plain machines: a driver container that builds or loads the kernel modules, the container toolkit so the runtime can inject devices and libraries, the device plugin that advertises the GPU resource, DCGM and its exporter for per-GPU metrics, node feature discovery to label hardware, a MIG manager, and a validator that gates node readiness until the whole stack works. A single cluster policy custom resource configures the set.

Reach for it on any cluster with GPU nodes, because keeping driver, toolkit and CUDA versions consistent across a fleet by baking images is the alternative and it does not scale. The costs are that a driver upgrade becomes a node-draining rolling operation, that failures are opaque when a kernel version and driver disagree, and that its preference for managing the driver itself conflicts with distributions or clouds shipping drivers in the node image, in which case you disable that component and let it manage the rest.

### OCI registries
**Short:** Registries speaking the OCI distribution spec, storing container images and increasingly Helm charts and artifacts.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, devtools/build-and-dependency-management @2

The OCI distribution specification is a small HTTP API covering blob upload and manifest put and get, and everything in a registry is content-addressed by digest with a tag as a mutable pointer to one. Because a manifest simply names a config blob and a list of layer blobs with a media type, anything can be stored this way, which is why Helm charts, WebAssembly modules, SBOMs, signatures and model artifacts now live in registries beside images. A manifest list, or image index, is what makes one tag resolve to a different image per platform.

Consolidating artifacts into the registry you already run buys one authentication model, one replication path and one retention policy. The consequences to keep in mind are that a tag is not an identity, so pin deployments to digests when it matters, that garbage collecting unreferenced blobs is an operational task, and that support for the referrers API linking signatures and attestations to an image still varies between implementations.

### official Helm chart
**Short:** The Apache Airflow community Helm chart deploying scheduler, webserver and autoscaling workers on Kubernetes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/workflow-and-durable-execution @3

The Apache Airflow community chart deploys the whole stack as one release: scheduler, the web or API server, triggerer, an optional Flower, a metadata database either bundled or external, and workers whose shape depends on the executor selected in `values.yaml`. With the Kubernetes executor there is no standing worker pool and each task becomes its own pod, while with the Celery executor a worker Deployment plus a broker is created and can scale from queue depth. DAGs reach the pods either baked into a custom image or synced from Git by a sidecar.

Reach for it rather than assembling the components yourself, because the chart encodes the version compatibility between them and runs the database migration job on upgrade. What needs your attention is what it cannot decide: a Fernet key and webserver secret that must stay stable across upgrades or existing connections become unreadable, log persistence, and resource requests for the scheduler, which is the component that fails first under load.

### OLM
**Short:** Operator Lifecycle Manager - installs, upgrades and resolves dependencies between Kubernetes operators.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/ci-cd-and-release @3

OLM manages operators as installable packages. A catalog source points at an index image of available operators, a subscription declares that a namespace wants one from a channel, and OLM resolves a ClusterServiceVersion, the manifest describing an operator's deployment, its RBAC, the CRDs it owns and those it requires, then installs the dependencies and the operator itself. An operator group scopes which namespaces it watches, and upgrades follow the channel automatically or wait for manual approval.

Reach for it when a cluster runs many third-party operators and you want their versions, dependencies and permissions managed rather than a folder of applied YAML. It is standard on OpenShift and optional elsewhere, which is the practical catch, because it adds concepts and a resolution step whose failures are hard to read while many projects ship a Helm chart that installs the operator in one command. A cluster with three operators does not need it.

### OpenCost
**Short:** CNCF project allocating cloud spend to Kubernetes namespaces, workloads and labels from usage metrics.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/kubernetes-and-orchestration @2

OpenCost computes the cost of a Kubernetes workload from two inputs: what each node costs, taken from the cloud provider's pricing API or a custom price sheet, and what each pod consumed of it, from Prometheus metrics on requests and usage over time. It allocates node cost across pods, adds persistent volumes, load balancers and network transfer where those can be attributed, and exposes the result by namespace, controller, label or any aggregation through an API and a Prometheus metric.

Reach for it because a cloud bill stops at the node and nothing in a provider's cost explorer knows that one namespace is responsible for most of a cluster. It needs Prometheus and a working pricing source, it produces estimates rather than invoice-accurate figures, and how idle and shared capacity is attributed is a policy choice you make. It is the CNCF specification and reference implementation behind Kubecost, whose commercial product adds retention, multi-cluster views and a supported UI.

### OpenMeter
**Short:** Usage metering and billing service aggregating events such as LLM token consumption into per-customer billable units.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, observability/metrics-and-monitoring @3

OpenMeter ingests raw usage as CloudEvents and aggregates them against meters you define, where a meter names the event type, the value to aggregate, the aggregation such as sum, count or unique count, and the fields to group by, such as customer or model. The aggregated result is queryable per subject and time window and can be pushed into a billing system or used to enforce entitlements and quotas inside the product. Ingestion is designed to be idempotent so a replayed event does not double-count.

Reach for it when a product bills or limits on consumption rather than seats, whether tokens, API calls or gigabytes processed, and the ledger of what a customer used has to be trustworthy enough to invoice from. That is the hard part and the reason not to build it out of your metrics stack, since Prometheus and its relatives are lossy and downsampled by design, which is fine for dashboards and unacceptable for a bill. If billing is per-seat or per-plan, this is machinery you do not need.

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

Operator SDK is a scaffolding and packaging tool with three operator types. The Go type generates a controller-runtime project essentially identical to Kubebuilder's, since they share that foundation. The Ansible type maps a custom resource to a playbook run on each reconcile, and the Helm type maps one to a chart install and upgrade, so a CRD-driven operator can exist with no controller code at all. It also generates the bundle format that OLM installs, comprising the ClusterServiceVersion, CRDs and metadata, and its scorecard runs conformance checks against that bundle.

Reach for the Helm or Ansible flavour to wrap existing automation quickly, and for the Go flavour when reconciliation needs real logic, since the two non-Go types cannot express much beyond applying something and their status handling is shallow. Reach for Kubebuilder instead when you are writing Go and do not need OLM bundles, because it is the leaner path to the same runtime.

### OperatorHub.io
**Short:** Community catalogue for discovering and installing Kubernetes Operators through the Operator Lifecycle Manager.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1

OperatorHub.io is a community catalogue where each entry is an operator packaged in OLM's bundle format, carrying its ClusterServiceVersion, CRDs, channels and a maturity level on the capability model, which runs from basic install through seamless upgrades, full lifecycle, deep insights and autopilot. Installing from it means applying a catalog source and a subscription so OLM resolves and manages the operator, rather than applying manifests yourself.

Use it to find whether a maintained operator already exists for something you were about to run by hand, and read the capability level before believing it will handle day-two operations. Two caveats: a listing is not vetting, so check who publishes an entry and how recently it was updated, and an operator asks for substantial cluster permissions, frequently cluster-wide, which deserves review before installation. Artifact Hub indexes the same bundles alongside Helm charts and other package types.

### Organizations Tag Policy
**Short:** AWS Organizations policy standardizing resource tag keys and values so cost attribution can be enforced.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, security/authorization-and-policy @2

A tag policy is an AWS Organizations policy attached to the root, an organizational unit or an account, declaring the canonical spelling of a tag key, the values it may take, and the resource types the rule covers. Attached in reporting mode it produces non-compliance findings in Resource Groups; with enforcement enabled for specified resource types, an operation that would create or tag a resource with a non-compliant value is refused outright.

Reach for it because tag-based cost allocation dies of case sensitivity, where three spellings of the same key become three columns in the billing report and none of them adds up. A tag policy fixes the vocabulary centrally instead of asking every team to remember it. Two limits: it governs the tags that are applied rather than whether anything is tagged at all, so pair it with a service control policy or an IaC module that requires the tag, and enforcement covers only the resource types you list.

### Packer
**Short:** HashiCorp tool that bakes versioned immutable machine images (AMIs, OVAs, container images) from a declarative template.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/container-and-image @2, platform-delivery/ci-cd-and-release @3

Packer builds machine images from a declarative HCL template with three parts: a source describing the base image and where to build, whether an instance for an AMI, a VM for an OVA or a container; provisioners that run shell scripts, Ansible, Chef or Puppet against the booted machine; and post-processors that tag, upload or convert the result. It launches a temporary instance, provisions it, snapshots it and tears the instance down, and one template can produce the equivalent image for several platforms in parallel.

Reach for it to move configuration work from boot time to build time, since an image with the agent, hardening and runtime already baked in boots in seconds and is identical everywhere, which is what makes autoscaling and immutable infrastructure work. The costs are a build pipeline of its own, image sprawl and the cleanup policy nobody writes, and a slower feedback loop than editing a file on a running host. For containers a Dockerfile does the same job, and Packer is only relevant for VM images.

### Podman
**Short:** Daemonless, rootless OCI container engine and image builder with a Docker-compatible CLI.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

Podman runs containers without a long-running daemon: the CLI forks the container process directly under your user, with `conmon` holding the terminal and exit status, so a container is a child of your session rather than of a root-owned service. Rootless operation uses user namespaces plus subordinate uid and gid mappings, so root inside the container maps to an unprivileged host user. The CLI mirrors Docker's closely enough that aliasing works, it exposes a Docker-compatible API socket for tools expecting one, and pods group containers sharing a network namespace the way Kubernetes does, with a command that even emits a Pod manifest.

Reach for it where a root daemon is unacceptable, which covers most hardened and multi-user Linux hosts, and where systemd should supervise containers, since Quadlet generates unit files from container definitions. The friction is at the edges: rootless networking goes through a userspace proxy with different performance and source-address behaviour, low ports need configuration, and tooling that assumes a Docker socket sometimes needs help finding Podman's.

### Port
**Short:** Commercial internal developer portal: a service catalog, scorecards and self-service actions for platform teams.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, platform-delivery/ci-cd-and-release @2

Port is a hosted internal developer portal built around a data model you define: you declare blueprints such as service, environment, deployment and cloud resource together with their relations, then ingest real state into them from Kubernetes, cloud providers, Git, CI and incident tools. On top of that graph sit scorecards, which grade every entity against rules such as having an owner, a readme, alerting and a recent deploy, and self-service actions, which expose a form that triggers a backend workflow like scaffolding a service or provisioning a database.

Reach for it when you want a catalogue and self-service without maintaining an application, which is the main difference from Backstage, since the model is configured rather than coded and stands up in days rather than a quarter. What you accept is a commercial dependency, a vendor-defined extension model, and the failure mode every portal shares: an ingestion pipeline nobody maintains produces a catalogue nobody trusts.

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

Pulumi executes a program in TypeScript, Python, Go, C#, Java or YAML; resource constructors register desired state with an engine, which diffs it against a state file and calls providers underneath to create, update or delete. Dependencies between resources flow through output values, which are futures rather than plain strings, so you cannot print or concatenate one directly and instead apply a function to it, and that is exactly what lets the engine order operations correctly. State lives in Pulumi's managed backend by default or in one you host, and stacks give per-environment instances of the same program.

Reach for it when infrastructure benefits from real abstraction and testing, and when a team would rather own components in their own language than in a module registry's DSL. The costs are that a general-purpose language makes it easy to write infrastructure that is hard to review, that the managed backend is the default and a paid product beyond small use, and that a smaller community means fewer ready answers than Terraform has.

### Puppet
**Short:** Agent-based declarative configuration management: nodes pull a compiled catalog and converge to the declared state.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1

Puppet is declarative and agent-based. Nodes run an agent that contacts a Puppet server, which compiles a catalog for that node from manifests written in Puppet's own DSL plus the node's facts, and the agent then applies the catalog and reports back, by default every half hour. Because a manifest declares resources and their relationships rather than steps, the language deliberately has no execution order beyond the dependencies you state, which is the source of most confusion for people arriving from Ansible or shell.

Reach for it on large fleets of long-lived machines where continuous enforcement is the point, since a change made by hand on a server is reverted at the next run and compliance can be reported across thousands of nodes. The costs are the server infrastructure, a DSL to learn, and a model that fits poorly with immutable infrastructure where machines are replaced rather than converged. Ansible is the agentless alternative for push-style work, and a Packer-baked image sidesteps the question entirely.

### Readiness and liveness
**Short:** Distinct health probes: readiness gates traffic until dependencies load, liveness restarts a wedged process.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, inference/model-server @3, observability/metrics-and-monitoring @3

They answer different questions and Kubernetes acts on them differently. A failing readiness probe removes the pod's address from its Service endpoints, so traffic stops arriving while the container keeps running; a failing liveness probe kills the container and the kubelet restarts it. A startup probe exists to cover slow initialization, disabling the other two until it first succeeds, which is the correct fix for a container a liveness probe keeps killing during a long warm-up.

The classic outage is a liveness probe that checks a downstream dependency: the database wobbles, every replica fails liveness at once, and the cluster restarts a fleet that was otherwise healthy. Liveness should test only that this process is not wedged and that a restart would help, while dependency checks belong in readiness. Also make readiness fail on shutdown before the process stops accepting connections, or endpoint removal races the terminating pod and requests are dropped mid-rollout.

### RIs
**Short:** Reserved instances: a one- or three-year capacity commitment traded for a large discount on cloud compute.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

A reserved instance is a billing commitment rather than reserved hardware, unless you explicitly buy a zonal form that includes a capacity reservation. You pledge one or three years against a particular configuration of instance family, size, region, tenancy and platform, and the discount applies automatically to any matching running instance. Standard reservations discount most and can only be resold on a marketplace, convertible ones discount less and can be exchanged for a different family, and payment is all upfront, partial or none with the rate improving accordingly.

Buy against the baseline you are confident will still exist in a year, and cover the variable layer with on-demand or Spot. The exposure is that a commitment outlives your architecture, so a move to a newer instance family, a shift to containers or serverless, or a workload that simply shrinks leaves you paying for nothing. Savings Plans are the more flexible successor for compute, committing to an hourly dollar amount rather than a shape.

### runc
**Short:** The reference OCI runtime: creates and runs a container from a bundle using Linux namespaces and cgroups.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1

`runc` is the reference implementation of the OCI runtime specification and it is where the container is actually created: given a bundle of a root filesystem plus a configuration document, it clones a process into new namespaces, applies cgroup limits, sets capabilities, seccomp filters and security-module labels, pivots the root and executes the entrypoint. It is a short-lived binary rather than a daemon, setting the process up and exiting, which is why the layers above keep a shim or `conmon` around to hold the terminal and reap the exit code.

You never invoke it directly outside debugging, but containerd, CRI-O, Docker and Podman all end up calling it, so its behaviour defines what a container is on Linux. It matters operationally in two places: security advisories in it are node-level container-escape issues and must be patched promptly, and substituting `crun` or a sandboxed runtime such as Kata is a per-pod `RuntimeClass` decision made above it.

### RunPod
**Short:** GPU cloud renting on-demand and spot A100/H100 pods and serverless endpoints for training and inference.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, model-training/fine-tuning-and-peft @3

RunPod rents GPU capacity in two shapes. Pods are containers with one or more attached GPUs that you keep running and reach over SSH or a proxied HTTP port, drawn either from a secure tier in dedicated data centres or a cheaper community tier of third-party hosts. Serverless endpoints instead scale worker containers per request behind a queue, so an idle endpoint costs nothing between requests. Network volumes persist model weights across pods, and interruptible instances trade a lower rate for eviction.

Reach for it when hyperscaler GPU pricing or availability is the blocker, which covers experiments, fine-tunes and inference endpoints with bursty traffic. Weigh what is missing before putting production on it: the compliance posture, networking, identity model and managed data services of a major cloud are not there, community-tier hosts vary in reliability, and cold starts on serverless endpoints are dominated by pulling and loading model weights unless workers stay warm.

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

Salt's distinguishing piece is its transport: minions hold a persistent message-bus connection to the master, so a command fans out to thousands of hosts and returns in seconds instead of opening an SSH session each. Configuration is declarative state files in YAML rendered through Jinja, with pillar data supplying per-minion values and grains supplying facts, and the beacon and reactor system lets a minion-side event trigger a state run, which is how Salt does event-driven automation. An agentless SSH mode exists for hosts that cannot run a minion.

Reach for it at fleet sizes where per-host SSH becomes the bottleneck, or when you want remote execution and configuration management in one tool with an event bus underneath. The costs are master and minion infrastructure to secure, where key acceptance is a real trust decision, a YAML-plus-Jinja layering that becomes hard to read, and a community that has thinned relative to Ansible's.

### Savings Plans
**Short:** AWS commitment-based discount: pledge an hourly spend for 1-3 years in exchange for lower compute rates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

A Savings Plan commits you to a dollar amount of compute spend per hour for one or three years, and AWS applies the discounted rate to matching usage up to that amount while billing anything above it at on-demand rates. Compute Savings Plans are the flexible kind and cover EC2 across any instance family, size, region and operating system plus Fargate and Lambda; EC2 Instance Savings Plans discount more deeply but tie you to one instance family in one region. Payment is all upfront, partial or none, with the rate improving accordingly.

They are the default choice over reserved instances for compute now, because the commitment is to spend rather than to a shape, so refactoring from EC2 onto Fargate does not strand it. The commitment is still non-cancellable and non-transferable, so size it against the trough of your usage rather than the average, and layer it: cover the stable baseline with a plan, the predictable middle with on-demand, and the burst with Spot.

### semantic-release
**Short:** CI tool deriving the next semver version from Conventional Commits and publishing the tag, changelog and release.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, devtools/version-control-and-workbench @2, devtools/build-and-dependency-management @3

On each CI run it reads the commits since the last release tag, maps their Conventional Commit types to a version bump so a fix becomes a patch, a feature becomes a minor and a breaking-change footer becomes a major, and if anything warrants a release it generates the notes, sets the version, publishes the artifact, creates the tag and the release, and comments on the issues involved. The whole thing is plugin-driven, so which registry it publishes to and what it writes are configurable, and no human commits a version number anywhere.

Reach for it when releases should be a consequence of merging rather than a ritual somebody performs, especially for libraries whose consumers depend on semver being honest. The cost is discipline at the commit level, since the version is only as correct as the commit messages and a breaking change described as a fix ships as a patch, which is why teams pair it with commit linting. It also assumes trunk-based releases and fits awkwardly with long-lived release branches.

### Serverless Framework
**Short:** YAML-driven toolkit for declaring and deploying FaaS functions, events and their supporting cloud resources.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/cloud-platform-and-cost @2, platform-delivery/ci-cd-and-release @3

A `serverless.yml` declares functions and their events, whether HTTP, schedule, queue or stream, plus IAM statements and any supporting resources, and the framework expands that into a full deployment for the target provider, which on AWS means generating and deploying a CloudFormation stack while uploading the packaged code. Plugins are the ecosystem, covering local emulation, bundling, step functions, warmers and support for providers beyond AWS.

Reach for it for a function-centric application when you want less ceremony than raw CloudFormation and a large plugin catalogue. Weigh two things: it is another abstraction whose failures surface as provider errors underneath, and recent major versions introduced a commercial licence for larger organizations, which pushed some teams elsewhere. SAM is the AWS-native equivalent, and CDK is the choice when you want a real language and infrastructure beyond the functions themselves.

### Skaffold
**Short:** Google tool automating the build-push-deploy inner loop against a Kubernetes cluster with file-watch redeploys.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1, platform-delivery/kubernetes-and-orchestration @2, platform-delivery/container-and-image @3

Skaffold reads a `skaffold.yaml` describing how to build each image, whether through Docker, Buildpacks, Bazel, Jib or Kaniko, and how to deploy, whether raw manifests, Kustomize or Helm. In development mode it watches the source tree, rebuilds only the images whose inputs changed, rewrites the image references in the manifests to the new digests, applies them, then streams logs and port-forwards the services; a file sync mode copies changed files straight into a running container for interpreted languages, skipping the rebuild entirely. A single run command performs the same pipeline once, which is what CI uses.

Reach for it to remove the hand-written loop of build, tag, push, edit the manifest and apply, particularly when several services are developed together against one cluster. It is an inner-loop tool rather than a delivery system, since it does no progressive rollout, approvals or drift correction, which is where Argo CD or Flux takes over, and building against a remote cluster still costs image push and pull latency every iteration.

### skopeo
**Short:** Daemonless CLI to inspect, copy and sign container images directly between registries.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/container-and-image @1, security/supply-chain-and-runtime-security @3

skopeo works on images in place, with no daemon and without unpacking them: a copy command streams blobs from one location to another, an inspect command prints a manifest or config without pulling, a delete command removes a tag, and a sync command mirrors whole repositories. Its distinguishing feature is transports, so a registry, a local directory, an OCI layout, a tarball and a local container store are all addressable, which means it moves images between forms as well as between registries, and it can sign and verify against a signature policy.

Reach for it for air-gapped mirroring, where copying into a directory or archive on one side of the boundary and back into a registry on the other is exactly the intended use, and for inspecting a remote image before deciding to pull tens of gigabytes. It is deliberately neither a builder nor a runtime, and copying between registries with different credentials means supplying both. crane is the closest equivalent from the Go container ecosystem.

### SkyPilot
**Short:** Runs training and serving jobs on whichever cloud has the cheapest available GPUs, with spot recovery and queuing.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, model-training/distributed-training @2, ml-lifecycle/ml-platform-and-pipelines @3

You describe a job in YAML, naming the resources it needs as a GPU type and count, its setup commands, its run command, and which files or storage to mount, and SkyPilot queries the clouds and regions you hold credentials for, finds where that shape is actually available and cheapest, provisions there, syncs the code and runs. It manages the cluster lifecycle afterwards, autostopping idle machines, and its managed-job mode runs on spot capacity with automatic recovery, so a preempted instance is replaced elsewhere and the job resumes from the last checkpoint your code wrote.

Reach for it when accelerator availability rather than price alone is the binding constraint, because taking a GPU in whichever region has one is often the difference between training this week and next. Two honest caveats: recovery only works if your training loop checkpoints to object storage frequently, and multi-cloud means multi-cloud egress plus several sets of credentials and quotas to keep in order.

### SnapStart
**Short:** AWS Lambda feature snapshotting an initialized environment and restoring it, removing cold-start init.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/runtime-internals-and-types @3, observability/profiling-and-performance @3

SnapStart is enabled per function and applies to published versions and their aliases rather than to the unpublished latest, so the snapshot is taken at publish time and each deploy produces a new version. The runtime exposes before-checkpoint and after-restore hooks, so a function can flush or close resources before the snapshot is taken and re-establish them when an environment is restored from it.

The practical checklist is what to distrust after a restore: open sockets and connection pools, cached temporary credentials, anything time-based, and anything that must be unique per environment, since every restored environment resumes from identical memory and would otherwise generate the same value. It buys latency rather than money, and a function whose initialization is already trivial gains nothing from it.

### Spinnaker
**Short:** Multi-cloud continuous delivery platform with pipeline stages for canary, blue-green and rollback deployments.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/ci-cd-and-release @1

Spinnaker is a multi-cloud delivery platform assembled from a set of microservices, with two central abstractions: an application groups its infrastructure, and a pipeline is a sequence of stages such as bake an image, deploy, wait for a manual judgment, evaluate a canary and promote, triggered from CI, a registry or a schedule. Deployment strategies are first class, so red/black, rolling red/black and canary are options on a stage rather than something you script, and it reads live cloud state so a pipeline knows what is currently deployed. Automated canary analysis compares metrics between baseline and canary and decides promotion.

Reach for it in a large organization deploying across several clouds where release governance, meaning approvals, staged promotion across environments and regions and automated canaries, is the actual requirement. The cost is substantial, since it is many services to run and upgrade and it predates the Kubernetes-native GitOps generation. Smaller teams get most of the value from Argo CD with Argo Rollouts or Flagger at a fraction of the weight.

### Spot
**Short:** Interruptible spare cloud capacity at a steep discount, reclaimable with short notice.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

Spot capacity is a cloud provider's unused inventory, sold at a large discount on the condition that it can be reclaimed when the provider needs it back, with a short warning of about two minutes on AWS and less on some other providers. Price varies by instance type, region and availability zone, and so does the interruption rate, which is why the practical strategy is diversification: accept several instance types across zones so a shortage in one pool does not take the whole fleet at once.

Reach for it for anything interruptible and replaceable, such as stateless capacity behind a load balancer, batch and CI jobs, training runs that checkpoint, or Kubernetes nodes carrying workloads with disruption budgets. Do not put a stateful singleton, a database or a deadline-bound job on it without a fallback. The engineering cost is real, since handling the termination notice, draining gracefully and checkpointing are all work you would not otherwise do, which is why only workloads designed for it actually realize the savings.

### Spot Fleet
**Short:** AWS request for a pool of interruptible spot instances across types and AZs at a steep discount.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

A Spot Fleet request asks for a target capacity, expressed in instances or in vCPU or memory units, together with a set of launch specifications describing acceptable instance types, sizes and subnets, and the fleet then chooses where to place capacity according to an allocation strategy. That strategy is the decision that matters, because capacity-optimized strategies pick pools with the deepest spare capacity and lower the interruption rate far more than chasing the lowest price does. A fleet can mix Spot and On-Demand so a baseline stays guaranteed, and it replaces interrupted instances automatically.

Reach for it for large interruptible fleets such as batch, rendering or CI runners, where diversification across pools is the whole point. Note that AWS now steers most use cases toward EC2 Fleet and toward Auto Scaling groups with a mixed-instances policy, which subsume the same capability with better integration, and that on Kubernetes Karpenter makes the equivalent decisions directly from pending pods.

### stakater/Reloader
**Short:** Kubernetes controller that triggers a rolling restart when a mounted ConfigMap or Secret changes.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, apis-frameworks/dependency-injection-and-config @3

You opt in with an annotation on the Deployment, StatefulSet or DaemonSet, either an auto annotation that follows everything the pod template mounts or references, or a reload annotation naming specific objects, and the controller then patches a dummy environment variable in the pod template on change, which is what makes the Deployment controller perform an ordinary rolling update.

Reach for it because the gap is real and surprising: a ConfigMap mounted as a volume is updated in place after a delay, one consumed through `envFrom` is not updated at all, and an application that reads its configuration once at startup silently keeps the old values either way. The alternative needing no controller is putting a hash of the config into a pod template annotation, which Helm charts commonly do; Reloader is the answer when you do not control the chart or the config changes outside the release.

### Strimzi
**Short:** Kafka operator for Kubernetes: Kafka, KafkaTopic, KafkaUser and KafkaRebalance CRDs with rolling KRaft ops.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/event-streaming-and-processing @1

Strimzi runs Kafka on Kubernetes through custom resources. A `Kafka` resource declares the cluster, with node pool resources describing broker and controller roles under KRaft, and the Cluster Operator materializes pods, storage, listeners and certificates from it. Separate Topic and User operators reconcile `KafkaTopic` and `KafkaUser` objects, so topics and their ACLs and credentials are declared in Git rather than created with a CLI, and a rebalance resource drives Cruise Control to move partitions. Companion resources cover Connect, MirrorMaker 2 and the HTTP bridge.

Reach for it when Kafka must run inside your own cluster for data residency, cost or platform consistency, and you want topic and user management to be declarative. What it does not remove is Kafka expertise, since partition sizing, retention, storage class performance, rack awareness and rolling restarts remain yours, and a broker rolled at the wrong moment is an availability incident. A managed Kafka removes that operational surface at a price.

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

The commands split by what they touch. `init` downloads providers and modules and configures the backend, writing the lock file; `validate` and `fmt` never touch state; `plan` refreshes state against the real world and produces a diff you can save as a file; and `apply` executes it. Around those sit the state operations: `state list`, `state mv` for a refactor that would otherwise destroy and recreate a resource, `import` to bring an existing resource under management, and `apply -replace` for a forced rebuild. Workspaces give one configuration several independent states.

Two habits separate calm operations from incidents. Save the plan and apply that artifact, so what was reviewed is exactly what runs, and read it for replacements rather than counting creates. And treat `-target` as a debugging escape hatch rather than a workflow, because it applies part of the graph and leaves state diverged from the configuration in a way the next full plan has to reconcile.

### Terraform Cloud
**Short:** HashiCorp's managed Terraform backend: remote locked state, PR-driven plan/apply runs and policy gates.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/ci-cd-and-release @2, security/authorization-and-policy @3

Now branded HCP Terraform, it supplies the pieces a team-scale workflow needs beyond the CLI: remote state with locking and versioned history, remote runs executed on its workers so credentials live there instead of on laptops, and a VCS integration that plans on every pull request and posts the result for review before a gated apply. Workspaces map to state instances with their own variables, run triggers chain one workspace's apply to another's plan, and Sentinel or OPA policies plus cost estimation gate an apply before it executes.

Reach for it when more than a couple of people apply Terraform and you want the state, the credentials and the audit trail off individual machines. The alternatives are an object-store backend with locking plus your own CI, or Atlantis for the pull-request workflow self-hosted, both cheaper and more work. Paid tiers price per managed resource, which is the number to model before adopting it across a large estate.

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

It parses a module's `.tf` files and emits a table of its inputs with types, defaults and descriptions, its outputs, its required providers and the resources it manages, in Markdown, table, JSON or a custom template. The useful mode is injection: given marker comments in the module's README it rewrites only the block between them, so running it in a pre-commit hook or a CI check keeps documentation matching the code and a failing check catches the variable somebody added without describing.

Reach for it for any module other people consume, because the input table is the module's real interface documentation and it is the first thing to go stale. What it cannot generate is the part that matters most, namely why the module exists, what it assumes and an example invocation, so treat the generated block as reference material inside a hand-written README rather than as the README. Descriptions on every variable are the prerequisite; without them the table is a list of names.

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

Test Kitchen runs a matrix of platforms against a configuration-management codebase. Its config file names drivers that create the instance, whether Vagrant, Docker or a cloud instance, a provisioner that applies the code with Chef, Ansible or Puppet, and a verifier, usually InSpec, that asserts the resulting machine state: this package installed, this service enabled and listening, this file with these permissions. The test command runs the whole create, converge, verify and destroy cycle for every platform in the matrix, and converging twice is how idempotence is checked, since the second run should report no changes.

Reach for it when cookbooks or roles run on several distributions and a regression would otherwise be found in production. The costs are wall-clock time, since real machines boot per platform per run, and maintaining the matrix as distributions come and go. Its relevance tracks that of mutable-machine configuration management generally; on immutable infrastructure the equivalent check is building an image with Packer and testing that artifact.

### Tower
**Short:** Ansible Tower/AWX: the web UI, scheduler, credential vault and RBAC layer that runs Ansible playbooks at scale.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/infrastructure-as-code-and-config @1, platform-delivery/ci-cd-and-release @2, security/authorization-and-policy @3

Tower was the commercial product built on the Ansible engine and is now the automation controller inside Red Hat's automation platform, with AWX as its upstream project. Beyond running playbooks it is the governance layer: credentials are stored encrypted and attached to a job template so an operator can run a play against production without ever seeing the key, surveys turn variables into a form, workflow templates chain job templates with success and failure branches and approval nodes, and every run is recorded per host with its full output.

Reach for it when automation is delegated, whether to a support team running a runbook they did not write or an application team given a self-service action, and when who ran what against which hosts must be auditable. It is a substantial service to operate or a subscription to buy, and it constrains playbooks to its project and inventory model. A small team already running playbooks from a reviewed CI pipeline gets much of the same control for far less.

### Transit Gateway
**Short:** AWS hub-and-spoke router connecting many VPCs, VPNs and Direct Connect links without a mesh of peering connections.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2

A Transit Gateway is a regional router that VPCs, site-to-site VPNs and Direct Connect gateways attach to, replacing a quadratic mesh of peerings with hub-and-spoke. Unlike peering it is transitive, and the control it gives you is route tables, several per gateway, with each attachment associated to one and propagating its routes into others, so isolation patterns such as a shared-services VPC every spoke can reach while spokes cannot reach each other are expressed there. Peering between gateways extends the same model across regions.

Reach for it once there are more than a handful of VPCs or any hybrid connectivity, because retrofitting it later means renumbering and re-routing. Two costs to model: it bills per attachment-hour plus per gigabyte processed, which for chatty cross-VPC traffic is materially more than peering, where in-region data transfer is cheaper; and overlapping CIDRs remain unroutable, so address planning still comes first. For two VPCs that only talk to each other, plain peering is cheaper and simpler.

### Trusted Advisor
**Short:** AWS advisory service flagging idle resources, rightsizing opportunities and security or limit risks.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1

Trusted Advisor runs a fixed set of automated checks against an account and groups the findings into cost optimization, performance, security, fault tolerance, service limits and operational excellence, each with a status and the specific resources involved, whether an idle load balancer, an underutilized instance, an unassociated elastic IP, a security group open to the world or a quota you are approaching. The full check set requires a Business or Enterprise support plan, while a smaller core set is available to every account.

Reach for it as the free, no-setup first sweep of an account, especially the service-limit checks, which catch the quota you are about to hit before a deploy fails on it. It is generic by design, since the recommendations know nothing about your architecture, so an idle resource may be a deliberate standby and the cost checks are shallower than Compute Optimizer's or Cost Explorer's rightsizing view. Treat it as a checklist rather than an audit; Security Hub and Config are the deeper controls for the security findings.

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

Virtual WAN is a managed hub-and-spoke backbone. You create a virtual hub per region, a Microsoft-managed network containing the gateways and a router, and attach spokes to it: virtual network connections, site-to-site VPN, point-to-site VPN, ExpressRoute circuits and network virtual appliances. Hubs in different regions are meshed over Microsoft's backbone automatically, so branch-to-branch and cross-region traffic transits it without you building peerings or running routers, and routing between attachments is controlled by hub route tables. A secured hub adds a managed firewall in the path.

Reach for it for a multi-region or branch-heavy estate, where the point is not connecting two networks but not hand-maintaining a topology. For a couple of virtual networks in one region, plain peering plus a VPN gateway is far cheaper and less machinery. The costs to model are per hub, per connection and per gigabyte processed, and the managed hub is opinionated, since you do not control its subnets or run your own routing inside it, so unusual designs fight it.

### Volcano
**Short:** Kubernetes batch scheduler adding gang scheduling, fair-share queues and GPU topology awareness for training jobs.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, data-movement/task-queue-and-jobs @2, ml-lifecycle/ml-platform-and-pipelines @3

Volcano is a batch scheduler that runs alongside or in place of the default Kubernetes scheduler for jobs that name it. Its unit is a pod group with a minimum member count, and gang scheduling means it admits either all of them or none, which is what a distributed training job needs, since half the workers running while the rest wait deadlocks the collective and burns accelerators. Queues carry weights and capacities for fair sharing between teams, plugins add binpack, task topology and NUMA or GPU-topology-aware placement, and it supports preemption and reclaim between queues.

Reach for it on a shared cluster running distributed training or HPC-style jobs, where the default scheduler's pod-at-a-time model produces deadlock and fragmentation. The costs are a second scheduler to operate and reason about, workloads that must be labelled to use it, and a feature surface aimed at batch that adds nothing for long-running services. Kueue is the lighter alternative when you need quota and gang admission but are content to let the default scheduler place pods.

### VPA
**Short:** Kubernetes Vertical Pod Autoscaler: recommends or applies right-sized CPU and memory requests from observed usage.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/kubernetes-and-orchestration @1, platform-delivery/cloud-platform-and-cost @2

The Vertical Pod Autoscaler has three components: a recommender reading historical CPU and memory usage and computing target, lower-bound and upper-bound requests; an updater evicting pods whose requests fall outside those bounds; and an admission controller rewriting the requests as the pod is recreated. The mode decides how much of that runs, with off producing recommendations only, initial setting requests at creation time, and auto evicting and resizing running pods.

Reach for it in recommendation mode almost always, and use the numbers to set requests deliberately, which is exactly what Goldilocks exists to make readable. Auto mode is disruptive because changing requests has historically meant recreating the pod, so an autoscaler restarts your workload; in-place pod resizing is changing that, but check what your cluster version actually supports. It also conflicts with an HPA on the same resource metric, since both react to the same signal, so the supported combination is VPA on memory with HPA on a different metric.

### VPC Peering
**Short:** Point-to-point private route between two VPCs: non-transitive, no overlapping CIDRs, traffic stays off the internet.
**Kind:** tech
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @3

A peering connection joins two VPCs, whether in the same account or not and the same region or not, through a request-and-accept handshake, after which you add routes on both sides pointing the other's CIDR at the connection and open security groups accordingly. Traffic stays on the provider's network throughout. Two properties dominate the design: it is non-transitive, so if A peers with B and B with C, A still cannot reach C, and the CIDRs must not overlap, which cannot be fixed afterwards without renumbering.

Reach for it when a small, stable number of VPCs need to talk, because within a region it is the cheapest option and the connection itself carries no hourly charge. It stops scaling at the point where the mesh becomes an unauditable set of connections and route tables, which is where a Transit Gateway, or Virtual WAN and Network Connectivity Center on the other clouds, takes over. Cross-region peering carries data transfer charges on both ends.

### VPC, Security Groups, NACLs
**Short:** The AWS network isolation primitives: a private virtual network plus stateful instance and stateless subnet firewalls.
**Kind:** concept
**Lang:** *
**Roles:** platform-delivery/cloud-platform-and-cost @1, runtime-systems/io-networking-and-syscalls @2, security/authorization-and-policy @3

The VPC and its subnets define the address space and, through route tables, what is reachable at all, so a subnet with no route to an internet gateway is private no matter what the firewalls say. A security group attaches to a network interface and is stateful, so the reply to a permitted outbound request is allowed back automatically, and it holds allow rules only with no way to express a deny. A network ACL attaches to a subnet, is stateless so both directions need rules including the ephemeral port range for return traffic.

The working default is to do essentially everything with security groups, referencing another security group as a source rather than a CIDR so the rule follows instances as they come and go. Reserve network ACLs for a coarse subnet-wide guardrail such as blocking a hostile range, because their statelessness is the source of the classic bug where an allowed inbound request has its reply silently dropped on the way out.
