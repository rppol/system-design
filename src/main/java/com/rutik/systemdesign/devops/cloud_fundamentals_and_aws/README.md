# Cloud Fundamentals & AWS

> Phase 5 — Cloud Platforms · Difficulty: Intermediate

AWS is the dominant public cloud, and its primitives — **IAM** for identity, **VPC** for networking, **EC2** for compute, **S3/EBS** for storage, **ELB** for load balancing, **Route 53** for DNS, **RDS** for managed databases, and **EKS** for Kubernetes — are the building blocks every cloud architect composes. This module covers what each service does, how they fit together, the concrete limits and pricing that drive design decisions, and the **Well-Architected Framework** lens (six pillars) that ties them into defensible architectures.

---

## 1. Concept Overview

Public cloud replaces capital-expense data centers with on-demand, API-driven, pay-per-use infrastructure. AWS exposes ~200 services, but a working architect leans on a small core:

- **IAM (Identity and Access Management)** — who can do what. Users, roles, policies (JSON), and the principle of least privilege. The control plane for every API call.
- **VPC (Virtual Private Cloud)** — your isolated network: subnets (public/private), route tables, internet/NAT gateways, security groups, and NACLs. See [networking_for_devops](../networking_for_devops/) for the underlying TCP/IP, and [cloud_networking_and_cdn](../cloud_networking_and_cdn/) for cross-VPC connectivity.
- **EC2 (Elastic Compute Cloud)** — virtual machines. Instance families (general/compute/memory/GPU), AMIs, instance store vs EBS-backed, Auto Scaling Groups.
- **S3 (Simple Storage Service)** — object storage, 11 nines (99.999999999%) durability, storage classes, lifecycle policies. **EBS (Elastic Block Store)** — network-attached block volumes for EC2.
- **ELB (Elastic Load Balancing)** — ALB (L7/HTTP), NLB (L4/TCP), GWLB (appliances). Distributes traffic across targets.
- **Route 53** — DNS + health checks + routing policies (weighted, latency, geolocation, failover).
- **RDS (Relational Database Service)** — managed PostgreSQL/MySQL/Aurora etc. Internals owned by [../../database/](../../database/); this module covers the cloud-operational side (Multi-AZ, read replicas, backups).
- **EKS (Elastic Kubernetes Service)** — managed Kubernetes control plane. See [kubernetes_architecture](../kubernetes_architecture/).

The **Well-Architected Framework** organizes design review into six pillars: Operational Excellence, Security, Reliability, Performance Efficiency, Cost Optimization, and Sustainability.

---

## 2. Intuition

> **One-line analogy**: AWS is a city's utility grid. IAM is the keycard system (who enters which building), VPC is the gated district with its own roads (subnets) and checkpoints (security groups), EC2 is the rentable office space, S3 is the warehouse with guaranteed inventory survival, and Route 53 is the city directory that sends visitors to the nearest open branch.

**Mental model**: Every AWS resource lives inside an account, inside a Region, usually inside one or more Availability Zones (AZs — physically separate data centers). IAM gates every API call; VPC gates every network packet. Compute (EC2/EKS) runs inside subnets; storage (S3) is regional and accessed over the network; the load balancer is the front door; Route 53 is the signpost.

**Why it matters**: Misunderstanding the IAM-VPC-AZ model is the root of most cloud incidents — over-permissive roles cause breaches, single-AZ deployments cause outages, and public subnets with open security groups expose databases. Knowing the concrete limits (a Region has 3+ AZs, gp3 gives 3000 IOPS baseline, ALB scales automatically, S3 is 11 nines) lets you size and price designs correctly the first time.

**Key insight**: **Availability comes from spreading across AZs, not from bigger instances.** A single huge EC2 in one AZ is less reliable than two small ones in two AZs behind an ELB. The cloud's reliability primitive is the *Region/AZ topology*, and almost every managed service (RDS Multi-AZ, S3, ALB) is built to exploit it.

---

## 3. Core Principles

1. **Least privilege via IAM.** Grant the minimum permissions; prefer roles (temporary credentials) over long-lived access keys.
2. **Design for failure across AZs.** Assume any single AZ or instance can die; spread workloads and use Multi-AZ managed services.
3. **Private by default.** Databases and app servers live in private subnets; only load balancers and bastions touch the internet.
4. **Managed over self-managed** where it fits — RDS over self-hosted Postgres, EKS over self-managed control plane — to offload undifferentiated heavy lifting.
5. **Everything is an API.** Provision with IaC ([infrastructure_as_code_terraform](../infrastructure_as_code_terraform/)), not the console.
6. **Tag and budget from day one.** Cost and ownership are attributes ([cloud_cost_optimization_finops](../cloud_cost_optimization_finops/)).
7. **Apply the Well-Architected pillars** as a recurring review, not a one-time checklist.

---

## 4. Types / Architectures / Strategies

### EC2 instance families

| Family | Prefix | Use case | Example |
|--------|--------|----------|---------|
| General purpose | m, t | Balanced web/app servers | m7i.large, t3.micro (burstable) |
| Compute optimized | c | CPU-bound (batch, gaming) | c7g.xlarge (Graviton) |
| Memory optimized | r, x | In-memory DB, caches | r7g.2xlarge |
| Storage optimized | i, d | High local IOPS | i4i.large (NVMe) |
| Accelerated | p, g, inf | GPU/ML inference | p5.48xlarge (H100), inf2 |

### S3 storage classes

| Class | Retrieval | Use case | Relative cost |
|-------|-----------|----------|---------------|
| Standard | Instant | Hot data | Baseline (~$0.023/GB-mo) |
| Standard-IA | Instant (fee) | Infrequent access | ~$0.0125/GB-mo |
| Intelligent-Tiering | Instant | Unknown/changing access | Auto-moves + monitoring fee |
| Glacier Instant | Instant | Archive, occasional read | ~$0.004/GB-mo |
| Glacier Flexible | Minutes-hours | Backups | ~$0.0036/GB-mo |
| Glacier Deep Archive | 12 hours | Compliance/cold | ~$0.00099/GB-mo |

### EBS volume types

| Type | Baseline | Max | Use case |
|------|----------|-----|----------|
| gp3 (SSD) | 3000 IOPS, 125 MB/s | 16000 IOPS, 1000 MB/s | Default general purpose |
| io2 Block Express (SSD) | Provisioned | 256000 IOPS | Critical DBs |
| st1 (HDD) | Throughput | 500 MB/s | Big sequential (logs, data lakes) |
| sc1 (HDD) | Cold | Lowest cost | Infrequent |

### Load balancers

| LB | Layer | Protocol | Use case |
|----|-------|----------|----------|
| ALB | L7 | HTTP/HTTPS/gRPC | Path/host routing, microservices |
| NLB | L4 | TCP/UDP/TLS | Ultra-low latency, static IP, millions of req/s |
| GWLB | L3/4 | GENEVE | Inserting firewalls/appliances |

---

## 5. Architecture Diagrams

```
Standard 3-tier AWS architecture (Multi-AZ)

  Internet
     |
  Route 53 (DNS: latency/failover routing)
     |
  +--------------------- VPC 10.0.0.0/16 ---------------------+
  |                                                            |
  |   AZ-a                          AZ-b                       |
  |  +-------------------+         +-------------------+        |
  |  | public subnet     |         | public subnet     |       |
  |  |  [ALB node]  <-----+--------+--> [ALB node]      |       |
  |  +--------|----------+         +---------|---------+        |
  |           |  (SG: 443 from internet)     |                 |
  |  +--------v----------+         +---------v---------+        |
  |  | private subnet    |         | private subnet    |       |
  |  |  [EC2/EKS pods]    |         |  [EC2/EKS pods]   |       |
  |  +--------|----------+         +---------|---------+        |
  |           |  (SG: app port from ALB SG)  |                 |
  |  +--------v----------+         +---------v---------+        |
  |  | private (DB) subnet|        | private (DB) subnet|       |
  |  |  [RDS primary] -------sync replication----> [RDS standby]|
  |  +-------------------+         +-------------------+        |
  |                                                            |
  |  NAT Gateway (per AZ) -> Internet GW for egress only       |
  +------------------------------------------------------------+
            |
         S3 (regional, accessed via Gateway VPC Endpoint - no NAT cost)

Well-Architected six pillars (review lens over the above)
  [Operational Excellence] [Security] [Reliability]
  [Performance Efficiency] [Cost Optimization] [Sustainability]
```

---

## 6. How It Works — Detailed Mechanics

### IAM policy (least privilege)

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "ReadOnlyOneBucket",
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:ListBucket"],
    "Resource": [
      "arn:aws:s3:::orders-prod",
      "arn:aws:s3:::orders-prod/*"
    ],
    "Condition": {"StringEquals": {"aws:PrincipalTag/team": "orders"}}
  }]
}
```

IAM evaluation: an explicit `Deny` always wins; otherwise an `Allow` must exist or the request is denied by default. Prefer **roles** assumed via STS (temporary credentials, default 1-hour) — for EC2 use an **instance profile**, for EKS use **IRSA** (IAM Roles for Service Accounts).

### VPC + security group (Terraform)

```hcl
resource "aws_vpc" "main" { cidr_block = "10.0.0.0/16" }

resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.1.0/24"   # 256 addresses (251 usable; AWS reserves 5)
  availability_zone = "us-east-1a"
}

resource "aws_security_group" "app" {
  vpc_id = aws_vpc.main.id
  ingress {                            # stateful: only allow from the ALB's SG
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress { from_port = 0; to_port = 0; protocol = "-1"; cidr_blocks = ["0.0.0.0/0"] }
}
```

Security groups are **stateful** (return traffic auto-allowed) and allow-only; NACLs are **stateless** subnet-level and support explicit deny.

### EC2 launch + Auto Scaling

```hcl
resource "aws_autoscaling_group" "web" {
  min_size            = 2          # one per AZ minimum for HA
  max_size            = 10
  desired_capacity    = 2
  vpc_zone_identifier = [aws_subnet.private_a.id, aws_subnet.private_b.id]
  target_group_arns   = [aws_lb_target_group.web.arn]
  health_check_type   = "ELB"      # replace if the LB health check fails, not just EC2 status
}
```

### RDS Multi-AZ vs read replicas

```hcl
resource "aws_db_instance" "orders" {
  engine               = "postgres"
  instance_class       = "db.r7g.large"
  allocated_storage    = 100
  multi_az             = true     # synchronous standby in another AZ -> ~60-120s failover
  backup_retention_period = 7     # automated daily snapshots + 5-min PITR
  storage_encrypted    = true
}
```

Multi-AZ = **HA** (synchronous standby, automatic failover, not readable). Read replicas = **scale reads** (asynchronous, readable, can promote). For DB internals see [../../database/](../../database/).

### Route 53 failover

```hcl
resource "aws_route53_record" "api" {
  zone_id = var.zone_id
  name    = "api.example.com"
  type    = "A"
  set_identifier = "primary"
  failover_routing_policy { type = "PRIMARY" }
  health_check_id = aws_route53_health_check.primary.id
  alias { name = aws_lb.primary.dns_name; zone_id = aws_lb.primary.zone_id; evaluate_target_health = true }
}
```

---

## 7. Real-World Examples

- **Netflix** runs almost entirely on AWS across thousands of EC2/ASG instances, multi-Region active-active, with Route 53 + their own Zuul gateway; chaos engineering (Chaos Monkey) validates AZ-failure resilience.
- **Airbnb** moved from a Rails monolith on EC2 to services on EKS, using RDS/Aurora for transactional data and S3 + Glacier for the photo archive with lifecycle policies.
- **A typical SaaS** uses the 3-tier pattern above: ALB -> EKS in private subnets -> Aurora Multi-AZ, with S3 for uploads behind CloudFront, IRSA for per-service S3 access, and Route 53 latency routing across two Regions.
- **Data lakes**: S3 Standard for hot partitions, Intelligent-Tiering or lifecycle rules transitioning to Glacier Deep Archive after 90 days, cutting storage cost ~95% on cold data.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| HA model | Single large EC2 | ASG across AZs + ELB | Resilience vs simplicity |
| DB resilience | Multi-AZ (HA) | Read replicas (scale) | Failover vs read throughput (often both) |
| Storage | EBS (block, per-instance) | S3 (object, shared, durable) | Latency/POSIX vs durability/scale |
| Load balancer | ALB (L7 routing) | NLB (L4, static IP, speed) | Smart routing vs raw performance |
| Egress | NAT Gateway | VPC Gateway/Interface Endpoint | Cost (NAT ~$0.045/GB) vs reach |
| Compute pricing | On-Demand | Spot / Savings Plans | Flexibility vs up-to-90% savings ([finops](../cloud_cost_optimization_finops/)) |
| Identity | Access keys | IAM roles (STS) | Convenience vs security |

---

## 9. When to Use / When NOT to Use

**Use AWS managed services when:** you want to offload operational toil (RDS, EKS, ELB), need proven Multi-AZ durability, are building net-new and want to move fast, or your scale/burst patterns favor elastic pay-per-use. The default for most cloud-native architectures.

**Reconsider when:** strict data-residency or regulatory constraints rule out a Region; predictable steady-state workloads where on-prem/colo is cheaper at scale; you're locked into a heavily multi-cloud strategy (then prefer portable primitives — see [gcp_and_azure_essentials](../gcp_and_azure_essentials/)); or ultra-low-latency hardware needs that VMs can't meet. Also avoid self-managing what AWS manages well (don't run your own Postgres on EC2 without a strong reason).

---

## 10. Common Pitfalls

**Pitfall 1 — Database in a public subnet with an open security group.**

```hcl
# BROKEN: RDS reachable from the entire internet on 5432
resource "aws_db_instance" "orders" {
  publicly_accessible = true
}
resource "aws_security_group_rule" "db_open" {
  type        = "ingress"
  from_port   = 5432
  to_port     = 5432
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]   # the entire internet can attempt to connect
}
```

```hcl
# FIX: private subnet, allow only the app's security group
resource "aws_db_instance" "orders" {
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.private.name
  vpc_security_group_ids = [aws_security_group.db.id]
}
resource "aws_security_group_rule" "db_from_app" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.app.id   # only app tier
  security_group_id        = aws_security_group.db.id
}
```

**Pitfall 2 — Single-AZ deployment.** Putting all instances and a non-Multi-AZ RDS in one AZ means an AZ outage (which happens) takes you fully down. FIX: spread ASG across 2+ AZs and enable RDS `multi_az = true`.

**Pitfall 3 — Wildcard IAM policies.** `"Action": "*", "Resource": "*"` grants full account access; a leaked key is then catastrophic. FIX: scope actions and resources, add conditions, use roles with short-lived STS credentials, and run IAM Access Analyzer.

**Pitfall 4 — NAT Gateway data charges for S3.** Routing S3 traffic through NAT costs ~$0.045/GB processed. FIX: add an **S3 Gateway VPC Endpoint** (free) so S3 traffic bypasses NAT entirely.

---

## 11. Technologies & Tools

| Tool/Service | Purpose |
|--------------|---------|
| IAM / IAM Identity Center | Identity, roles, SSO |
| VPC, Security Groups, NACLs | Networking and isolation |
| EC2 / Auto Scaling / EKS | Compute (VMs, scaling, Kubernetes) |
| S3 / EBS / EFS | Object / block / shared-file storage |
| ELB (ALB/NLB/GWLB) | Load balancing ([cloud_networking_and_cdn](../cloud_networking_and_cdn/)) |
| Route 53 | DNS, health checks, routing policies |
| RDS / Aurora | Managed relational DB ([../../database/](../../database/)) |
| CloudWatch / CloudTrail | Metrics/logs / API audit ([observability_metrics_prometheus](../observability_metrics_prometheus/)) |
| Terraform / CloudFormation / CDK | IaC ([infrastructure_as_code_terraform](../infrastructure_as_code_terraform/)) |
| AWS Well-Architected Tool | Pillar-based architecture review |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between a Region and an Availability Zone, and why does it matter for design?**
A Region is a geographic area (e.g., us-east-1); an Availability Zone is one or more discrete data centers within a Region with independent power/cooling/networking, and each Region has at least 3 AZs. It matters because AZs fail independently, so spreading instances and using Multi-AZ managed services gives you fault tolerance against a data-center-level outage. Always deploy across at least two AZs for any production workload.

**Q2: Explain IAM roles vs IAM users and when to use each.**
An IAM user has long-lived credentials (password/access keys) tied to a human or legacy app; an IAM role has no permanent credentials and is *assumed* via STS to get temporary tokens (default 1-hour). Use roles for workloads (EC2 instance profiles, EKS IRSA, cross-account access) and for humans via SSO, because temporary credentials drastically reduce the blast radius of a leak. Reserve users for break-glass or systems that genuinely can't assume roles, and rotate their keys.

**Q3: Security groups vs network ACLs — what's the difference?**
Security groups are stateful, instance/ENI-level, allow-only rules — return traffic is automatically permitted. NACLs are stateless, subnet-level, and support both allow and explicit deny, so you must define inbound and outbound rules separately. Use security groups as the primary control (they're easier and stateful) and NACLs for coarse subnet-wide deny rules like blocking a bad CIDR.

**Q4: RDS Multi-AZ vs read replicas — what problem does each solve?**
Multi-AZ maintains a synchronous standby in another AZ for high availability with automatic failover (~60-120s) but the standby is not readable. Read replicas are asynchronous copies you can read from to scale read traffic, and they can be promoted to standalone primaries. They solve different problems — HA vs read scaling — and are often used together: Multi-AZ for resilience plus replicas for read-heavy workloads.

**Q5: How do you choose between ALB and NLB?**
Choose ALB for HTTP/HTTPS/gRPC when you need Layer-7 features: path/host-based routing, WAF integration, TLS termination, and sticky sessions. Choose NLB for Layer-4 TCP/UDP when you need ultra-low latency, millions of requests per second, a static IP, or to preserve the client source IP. A common pattern is NLB in front of ALB only when you specifically need NLB's static IP plus ALB's routing.

**Q6: What gives S3 its durability, and what are the storage classes for?**
S3 stores objects redundantly across multiple devices in at least three AZs, yielding 99.999999999% (11 nines) durability — statistically you'd lose one object in 10 million every 10,000 years. Storage classes trade retrieval latency/cost for storage cost: Standard for hot data, Standard-IA and Glacier Instant for infrequent access, and Glacier Flexible/Deep Archive for archives (minutes to 12 hours retrieval). Use lifecycle policies to transition data automatically and cut cost by up to ~95% on cold data.

**Q7: How does EC2 Auto Scaling decide when and how to scale?**
An Auto Scaling Group has min/desired/max sizes and scaling policies — target tracking (e.g., keep CPU at 50%), step scaling on CloudWatch alarms, or scheduled scaling. It uses health checks (EC2 status or ELB) to replace unhealthy instances and launches new ones from a launch template across the configured AZs. Set the health check type to `ELB` so an instance failing the application health check (not just the hypervisor check) gets replaced.

**Q8: What's the role of a NAT Gateway, and how can it bite you on cost?**
A NAT Gateway lets instances in private subnets make outbound internet connections (e.g., to download packages) without being inbound-reachable. It charges per hour (~$0.045) plus ~$0.045 per GB processed, so high-volume egress — especially to S3 or other AWS services routed through it — gets expensive fast. Use VPC Gateway Endpoints for S3/DynamoDB (free) and Interface Endpoints (PrivateLink) for other services to bypass NAT.

**Q9: Walk through the six pillars of the Well-Architected Framework.**
Operational Excellence (run and monitor systems, automate, learn from failures), Security (least privilege, defense in depth, encryption), Reliability (recover from failure, scale horizontally, test recovery), Performance Efficiency (use the right resource types, scale, experiment), Cost Optimization (right-size, use the right pricing model, measure), and Sustainability (minimize resource use and carbon footprint). It's a recurring review lens, not a one-time checklist — you run a Well-Architected Review to surface risks against each pillar.

**Q10: How does EKS differ from running Kubernetes on EC2 yourself?**
EKS provides a managed, Multi-AZ Kubernetes control plane (API server, etcd) that AWS patches and scales, while you manage the worker nodes (or use Fargate/managed node groups). Self-managed Kubernetes on EC2 gives full control but you operate the control plane, including etcd backups, upgrades, and HA. Choose EKS to offload the hardest operational part (the control plane) and integrate with IAM via IRSA — see [kubernetes_architecture](../kubernetes_architecture/).

**Q11: How would you give an application running on EKS access to a specific S3 bucket securely?**
Use IRSA (IAM Roles for Service Accounts): create an IAM role with a least-privilege policy scoped to that bucket, associate it with a Kubernetes service account via an OIDC trust relationship, and run the pod under that service account. The pod then gets temporary STS credentials automatically, with no static keys in the container. This is far safer than node-wide instance-profile permissions, which would grant access to every pod on the node.

**Q12: What are the main ways to reduce VPC data transfer and egress costs?**
Keep traffic in-Region and in-AZ where possible (cross-AZ transfer is billed, internet egress more so), use VPC Gateway Endpoints for S3/DynamoDB to avoid NAT data charges, and place chatty services in the same AZ when latency and cost matter more than AZ isolation. For cross-VPC or cross-account traffic, use PrivateLink or peering instead of routing through the internet, and use CloudFront to offload origin egress. Tag and monitor with Cost Explorer to find the biggest transfer line items — see [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/).

---

## 13. Best Practices

- **Least privilege everywhere**: scoped IAM policies, roles over keys, IAM Access Analyzer, MFA on root.
- **Multi-AZ by default**: ASG across 2+ AZs, RDS `multi_az = true`, AZ-aware NAT.
- **Private by default**: DBs/app tiers in private subnets; only LBs/bastions are public.
- **Provision with IaC** ([infrastructure_as_code_terraform](../infrastructure_as_code_terraform/)); never click-ops production.
- **Encrypt at rest and in transit**: KMS for EBS/S3/RDS, TLS on ELB.
- **Use VPC Endpoints** for S3/DynamoDB to cut NAT cost and keep traffic private.
- **Tag for cost and ownership** from day one ([cloud_cost_optimization_finops](../cloud_cost_optimization_finops/)).
- **Run Well-Architected Reviews** periodically against all six pillars.

---

## 14. Case Study

### Scenario: A startup's "it works" architecture survives a load test but fails an AZ outage

A startup launched on a single m5.large EC2 running both the app and a self-managed Postgres, with an Elastic IP and a wide-open security group. It worked in demos. Then us-east-1a had a partial outage, the instance went unreachable, and there was no standby — full outage for 4 hours plus a manual database restore.

```hcl
# BROKEN: single instance, single AZ, DB on the same box, open SG
resource "aws_instance" "app" {
  ami           = "ami-123"
  instance_type = "m5.large"
  subnet_id     = aws_subnet.public_a.id   # one AZ
  # Postgres installed on the same instance, Elastic IP, SG allows 0.0.0.0/0
}
```

```hcl
# FIX: 3-tier, Multi-AZ, managed DB, private subnets
resource "aws_autoscaling_group" "app" {
  min_size            = 2
  max_size            = 8
  vpc_zone_identifier = [aws_subnet.private_a.id, aws_subnet.private_b.id]  # 2 AZs
  target_group_arns   = [aws_lb_target_group.app.arn]
  health_check_type   = "ELB"
}

resource "aws_lb" "app" {                  # ALB in public subnets, app stays private
  load_balancer_type = "application"
  subnets            = [aws_subnet.public_a.id, aws_subnet.public_b.id]
  security_groups    = [aws_security_group.alb.id]   # 443 from internet only
}

resource "aws_db_instance" "orders" {      # managed, Multi-AZ, encrypted, private
  engine                  = "postgres"
  instance_class          = "db.r7g.large"
  multi_az                = true
  publicly_accessible     = false
  storage_encrypted       = true
  backup_retention_period = 7
}
```

After the redesign, an AZ failure now drains traffic to the healthy AZ via the ALB health checks, the ASG launches replacement instances, and RDS fails over to its standby automatically in ~90 seconds. Route 53 health checks add a second Region as a failover target for Region-level events. The team also added an S3 Gateway Endpoint, cutting their NAT bill by ~$600/month.

**Outcome:** availability went from a single point of failure to surviving any single-AZ event with sub-2-minute database failover, the database was no longer internet-exposed, and recovery became automatic rather than a manual restore. The architectural shift — "reliability comes from AZ spread and managed services, not bigger instances" — was the key lesson.

**Discussion questions:**
1. Why is a single large instance less reliable than two small instances across two AZs?
2. What concrete Well-Architected pillars did the original design violate, and how does the fix address each?
3. When would you add a second Region with Route 53 failover, and what new failure modes does that introduce?

---

**Cross-references:** [networking_for_devops](../networking_for_devops/) (TCP/IP under VPC), [cloud_networking_and_cdn](../cloud_networking_and_cdn/) (cross-VPC connectivity, CDN), [kubernetes_architecture](../kubernetes_architecture/) (EKS control plane), [infrastructure_as_code_terraform](../infrastructure_as_code_terraform/) (provision all of this), [cloud_cost_optimization_finops](../cloud_cost_optimization_finops/) (pricing models, tagging), [gcp_and_azure_essentials](../gcp_and_azure_essentials/) (multi-cloud equivalents), [../../database/](../../database/) (RDS/Aurora internals), [secrets_management](../secrets_management/) (KMS, secrets).
