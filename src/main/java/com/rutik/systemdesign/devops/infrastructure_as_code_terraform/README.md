# Infrastructure as Code (Terraform)

> Phase 4 — Infrastructure as Code & Config · Difficulty: Advanced

Terraform makes **infrastructure declarative, versioned, and reproducible**. Instead of clicking through the AWS console or running imperative scripts, you describe the desired end state in HCL (HashiCorp Configuration Language), and Terraform computes the diff between that desired state and a recorded **state file**, then calls cloud provider APIs to converge reality to the declaration. The state file is the keystone: it maps your config to real-world resource IDs, and almost every Terraform sharp edge — drift, locking, secrets leakage, blast radius — traces back to how state is stored and shared.

---

## 1. Concept Overview

Terraform is a provider-agnostic provisioning tool. Its model has four moving parts:

1. **Configuration (HCL)** — the desired state: `resource`, `data`, `variable`, `output`, `module`, `provider` blocks.
2. **State** — a JSON file (`terraform.tfstate`) mapping each config resource to a real resource ID (e.g., `aws_instance.web` → `i-0abc123`). State also caches attributes so Terraform can diff without re-reading everything.
3. **Providers** — plugins (downloaded into `.terraform/`) that translate Terraform's CRUD into cloud API calls (AWS, GCP, Azure, Kubernetes, etc.).
4. **The core loop** — `init` (download providers/modules, configure backend) → `plan` (refresh state, compute diff) → `apply` (execute the diff) → `destroy` (tear down).

The central idea is the **reconciliation diff**: `desired (HCL) − actual (state, refreshed against the cloud) = a plan` of creates, updates, replaces, and deletes. Terraform is declarative (you say *what*, not *how*) and uses a dependency graph to order operations and parallelize independent ones (default 10 concurrent operations).

Terraform's lineage matters for naming: HashiCorp relicensed Terraform from MPL 2.0 to the BUSL in August 2023, which forked the community-driven **OpenTofu** (covered in [terraform_advanced_and_alternatives](../terraform_advanced_and_alternatives/)). The core concepts here apply to both.

---

## 2. Intuition

> **One-line analogy**: Terraform is a GPS for your infrastructure. The destination is your HCL config; your current location is the state file; `terraform plan` is the route preview ("turn left, demolish a subnet, build 3 EC2s"); `terraform apply` drives the route. If someone moves the car while you're parked (a manual console change), the GPS is now lying about where you are — that's *drift*, and you must refresh to relocate.

**Mental model**: HCL is the desired state, the state file is Terraform's belief about the actual state, and the cloud is the real actual state. `plan` refreshes the state file against the cloud, then diffs it against HCL. The state file is a cache plus an identity map — without it, Terraform can't tell "create a new bucket" from "this bucket already exists, leave it alone."

**Why it matters**: Manual cloud changes are unrepeatable, undocumented, and impossible to review. IaC turns infrastructure into reviewed pull requests with full Git history, makes environments reproducible (dev mirrors prod), and enables disaster recovery as a `terraform apply`. The state file's location and locking determine whether a team can collaborate safely or will corrupt each other's deployments.

**Key insight**: **State is the source of truth Terraform trusts, not the cloud.** If state and the cloud disagree (someone deleted a resource in the console), Terraform's next plan will try to recreate it; if state is lost, Terraform thinks nothing exists and will try to build duplicates. Protecting, locking, and reconciling state is 80% of operating Terraform safely.

---

## 3. Core Principles

1. **Declarative desired state** — describe the end state in HCL; Terraform computes the path.
2. **State is the identity map** — it binds config addresses to real resource IDs; guard it like a database.
3. **Plan before apply** — `plan` is a dry run; review the diff (especially `-/+` replacements) before applying.
4. **Remote state + locking for teams** — never share `terraform.tfstate` over a shared drive; use S3 + DynamoDB (or equivalent) with locking.
5. **Modules for reuse** — encapsulate a pattern (a VPC, an EKS cluster) once, instantiate many times with inputs.
6. **Immutability over mutation** — many changes force *replacement* (destroy + recreate), not in-place edits; design for it.
7. **Idempotency** — applying the same config twice yields no changes the second time (a clean `plan` shows "No changes").

---

## 4. Types / Architectures / Strategies

### Backends (where state lives)

| Backend | Locking | Use case |
|---------|---------|----------|
| Local (`terraform.tfstate` on disk) | None | Solo experiments only |
| S3 + DynamoDB | DynamoDB lock table | AWS teams (most common) |
| S3 native lock (`use_lockfile`) | S3 conditional writes (Terraform 1.10+) | Drop the DynamoDB table |
| Terraform Cloud / Enterprise (HCP) | Built-in | Managed runs, policy, RBAC |
| GCS / Azure Blob | Built-in (GCS object lock / Azure lease) | GCP / Azure teams |

### Environment isolation strategies

| Strategy | Mechanism | Tradeoff |
|----------|-----------|----------|
| Workspaces | One config, multiple state files (`terraform workspace`) | Easy, but same backend/config; weak isolation |
| Directory per env | `envs/dev`, `envs/prod` with separate backends | Strong isolation; some duplication |
| Terragrunt | DRY wrapper generating backends per env | Less duplication (see [terraform_advanced_and_alternatives](../terraform_advanced_and_alternatives/)) |
| Separate AWS accounts | Account per env + per-account state | Strongest blast-radius isolation |

### Meta-arguments (control resource creation)

| Meta-argument | Purpose |
|---------------|---------|
| `count` | Create N copies indexed by integer |
| `for_each` | Create instances keyed by a map/set (stable addresses) |
| `depends_on` | Force explicit ordering when implicit deps don't exist |
| `lifecycle` | `create_before_destroy`, `prevent_destroy`, `ignore_changes` |
| `provider` | Pin a resource to a specific provider alias (multi-region) |

---

## 5. Architecture Diagrams

```
Terraform core loop

  main.tf / variables.tf (DESIRED state, HCL)
        |
   terraform init  -> download providers into .terraform/, configure backend
        |
   terraform plan  -> 1) refresh: read state, query cloud APIs for actual attrs
                      2) diff:    desired (HCL) vs refreshed state
                      3) output:  + create, ~ update, -/+ replace, - destroy
        |
   terraform apply -> walk dependency graph (parallelism=10), call provider CRUD
        |
        v
   write back terraform.tfstate (S3) + release DynamoDB lock


Remote state with locking (S3 + DynamoDB)

  engineer A --apply--> [acquire DynamoDB lock]--> mutate AWS --> write S3 state --> release lock
  engineer B --apply--> [lock held] ... waits ... -> acquires after A -> sees A's state
       (without the lock, A and B's concurrent writes CORRUPT the single state file)


Dependency graph (Terraform builds a DAG, applies in topological order)

  aws_vpc.main
     +-> aws_subnet.app (depends on vpc id)
     |       +-> aws_instance.web (depends on subnet id)
     +-> aws_internet_gateway.igw
  independent nodes (igw, subnet) created in parallel; dependents wait
```

---

## 6. How It Works — Detailed Mechanics

### A minimal, real configuration

```hcl
terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.40" }   # pin major; allow patch
  }
  backend "s3" {
    bucket         = "acme-tfstate-prod"
    key            = "network/terraform.tfstate"     # one key per state slice
    region         = "us-east-1"
    dynamodb_table = "tf-locks"                       # state locking
    encrypt        = true                             # SSE on state at rest
  }
}

provider "aws" {
  region = "us-east-1"
}

variable "cidr" {
  type    = string
  default = "10.0.0.0/16"
}

resource "aws_vpc" "main" {
  cidr_block           = var.cidr
  enable_dns_hostnames = true
  tags                 = { Name = "prod-vpc", ManagedBy = "terraform" }
}

resource "aws_subnet" "app" {
  vpc_id            = aws_vpc.main.id          # implicit dependency -> ordering
  cidr_block        = cidrsubnet(var.cidr, 8, 1)
  availability_zone = "us-east-1a"
}

output "vpc_id" {
  value = aws_vpc.main.id
}
```

### Reading the plan output (the four operation symbols)

```bash
terraform plan
#   + create        -> resource will be added
#   ~ update         -> in-place attribute change (no downtime)
#   -/+ replace      -> destroy then recreate (DOWNTIME / new ID) -- read these carefully
#   - destroy        -> resource removed
#
# Example dangerous line:
#   aws_db_instance.main must be replaced
#   ~ engine_version = "15.4" -> "16.1"   # forces replacement -> data loss risk!
```

The `-/+` (replace) is where production incidents are born. A change to an immutable attribute (an EC2 AMI, an RDS engine version on some engines, an S3 bucket name) destroys and recreates the resource.

### Modules — reuse a pattern

```hcl
# modules/vpc/main.tf -- the reusable unit
variable "name"  { type = string }
variable "cidr"  { type = string }
resource "aws_vpc" "this" { cidr_block = var.cidr, tags = { Name = var.name } }
output "id" { value = aws_vpc.this.id }

# root usage -- instantiate the module twice
module "vpc_dev"  { source = "./modules/vpc", name = "dev",  cidr = "10.1.0.0/16" }
module "vpc_prod" { source = "./modules/vpc", name = "prod", cidr = "10.2.0.0/16" }
# reference outputs:  module.vpc_prod.id
```

### `count` vs `for_each` (and why `for_each` is usually better)

```hcl
# count: indexed by integer -> removing the middle element re-indexes the rest
resource "aws_instance" "web" {
  count = 3
  ami   = "ami-123"
  # address: aws_instance.web[0..2]; delete [1] and [2] shifts to [1] -> Terraform DESTROYS+RECREATES
}

# for_each: keyed by a stable map key -> add/remove without disturbing others
resource "aws_instance" "web" {
  for_each = toset(["api", "worker", "cron"])
  ami      = "ami-123"
  tags     = { role = each.key }
  # address: aws_instance.web["api"]; removing "worker" leaves "api"/"cron" untouched
}
```

### Workspaces — multiple states, one config

```bash
terraform workspace new staging
terraform workspace select staging       # now applies write to a staging state file
# reference in config:  terraform.workspace == "prod" ? 5 : 1   # bigger fleet in prod
```

### Drift detection and reconciliation

```bash
terraform plan -refresh-only            # show drift WITHOUT proposing config-driven changes
# Someone changed an SG rule in the console:
#   ~ aws_security_group.web ingress = [80] -> [80, 22]   (drift from cloud)
# Either: (a) accept it into config (commit the change), or
#         (b) apply to revert the cloud back to HCL.
```

### Importing existing resources (bring unmanaged infra under Terraform)

```hcl
# Terraform 1.5+ declarative import block (preferred over the old `terraform import` CLI)
import {
  to = aws_s3_bucket.legacy
  id = "acme-legacy-logs"          # existing bucket name
}
resource "aws_s3_bucket" "legacy" {
  bucket = "acme-legacy-logs"
}
```

```bash
terraform plan -generate-config-out=generated.tf   # 1.5+: scaffold HCL for imported resource
terraform apply                                     # state now tracks the existing bucket
```

### State surgery (use sparingly, always back up first)

```bash
terraform state list                                  # enumerate managed resources
terraform state mv aws_instance.old aws_instance.new  # rename without destroy/recreate
terraform state rm aws_instance.web                   # stop managing (does NOT delete in cloud)
terraform state pull > backup.tfstate                 # ALWAYS back up before surgery
```

---

## 7. Real-World Examples

- **Multi-account AWS landing zone**: a platform team uses one state slice per account-and-domain (`network`, `iam`, `eks`), with S3 + DynamoDB per account. A new team gets an account provisioned by a `terraform apply` of an account-factory module — repeatable, reviewed, audited.
- **EKS cluster + node groups as a module**: the `terraform-aws-modules/eks` community module (10M+ downloads) is instantiated per environment; upgrading Kubernetes is a version bump in the module input plus a reviewed `plan`. Cross-link [kubernetes_architecture](../kubernetes_architecture/).
- **Drift remediation in CI**: a nightly `terraform plan -detailed-exitcode` runs in CI; exit code 2 (changes present) opens a ticket — catching console hot-fixes before they cause surprise replacements.
- **Disaster recovery**: because the entire VPC/RDS/EKS topology is in HCL with remote state, rebuilding a destroyed region is `terraform apply` against a fresh backend — see [disaster_recovery_and_resilience](../disaster_recovery_and_resilience/).

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| State location | Local file | Remote (S3+DynamoDB) | Team collaboration & locking |
| Env isolation | Workspaces | Directory/account per env | Convenience vs blast-radius isolation |
| Resource fan-out | `count` | `for_each` | Stable addressing (avoid re-index churn) |
| State granularity | One big state | Many small states | Plan speed/blast radius vs cross-ref complexity |
| Provider version | Loose (`>=`) | Pinned (`~>`) | Latest features vs reproducible plans |
| Secrets | Variables in state | External (Vault/SSM) | Convenience vs leakage (state stores plaintext) |
| Apply driver | Local CLI | Atlantis / TFC / CI | Simplicity vs policy + audit + locking |

---

## 9. When to Use / When NOT to Use

**Use Terraform when:** provisioning cloud infrastructure (networks, compute, managed DBs, IAM, Kubernetes clusters) that should be reproducible, reviewed, and version-controlled; managing multiple environments that must stay consistent; or you need multi-cloud/provider-agnostic provisioning under one tool and workflow.

**Reconsider when:** configuring the *inside* of servers (package installs, files, services) — that's [configuration_management](../configuration_management/)'s job (Ansible/Packer), though Terraform can trigger it. Avoid Terraform for fast-changing application deploys where a GitOps/Kubernetes controller fits better (see [gitops_argocd_flux](../gitops_argocd_flux/)). For a single resource you'll touch once, the console may be faster — but anything you'll reproduce or audit belongs in IaC. Heavy imperative logic (loops with side effects, complex branching) signals you may want Pulumi instead (see [terraform_advanced_and_alternatives](../terraform_advanced_and_alternatives/)).

---

## 10. Common Pitfalls

**Pitfall 1 — Local state shared over a drive / no locking → state corruption.**

```hcl
# BROKEN: default local backend, state file emailed/dropbox'd between engineers.
terraform {
  # no backend block -> local terraform.tfstate on each laptop
}
# Two engineers apply concurrently -> divergent local states -> resources double-created
# or orphaned; merging two .tfstate files by hand is a nightmare.
```

```hcl
# FIX: remote backend with locking so applies serialize and state is shared.
terraform {
  backend "s3" {
    bucket         = "acme-tfstate-prod"
    key            = "app/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tf-locks"     # DynamoDB lock -> second apply waits, no corruption
    encrypt        = true
  }
}
```

**Pitfall 2 — Secrets land in state as plaintext.** Any sensitive value passed to a resource (an RDS `password`, a `tls_private_key`) is stored *unencrypted inside the state JSON*, regardless of `sensitive = true` (which only masks console output). Anyone with read access to the S3 bucket can read the password. FIX: restrict S3 bucket policy + KMS encryption, never commit state to Git, and source secrets from [secrets_management](../secrets_management/) (Vault dynamic creds / AWS Secrets Manager) rather than passing them through Terraform.

**Pitfall 3 — `count` re-indexing destroys the wrong resources.**

```hcl
# BROKEN: removing the middle host re-indexes everything after it.
resource "aws_instance" "node" {
  count = 3                          # node[0], node[1], node[2]
  ami   = var.ami
}
# Delete node[1] (set count=2 and drop it) -> Terraform shifts node[2] to node[1]
# -> it DESTROYS the real node[2] and RECREATES it at index 1. Unintended downtime.
```

```hcl
# FIX: use for_each with stable keys so identities don't shift.
resource "aws_instance" "node" {
  for_each = toset(["api", "worker", "cron"])   # node["api"], node["worker"], node["cron"]
  ami      = var.ami
  tags     = { role = each.key }
}
# Removing "worker" leaves "api" and "cron" completely untouched.
```

**Pitfall 4 — Ignoring `-/+ replace` in the plan.** Engineers skim the plan, miss that a `~ engine_version` change *forces replacement* of an RDS instance, and `apply` destroys the production database. FIX: read every `-/+` line, add `lifecycle { prevent_destroy = true }` to critical stateful resources, and require plan review in CI.

**Pitfall 5 — Provider version drift.** Using `>= 5.0` lets a CI run pick up a new provider that changes a default and proposes surprise diffs. FIX: pin with `~> 5.40` and commit `.terraform.lock.hcl` so every machine resolves identical provider versions.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Terraform CLI | Core `init`/`plan`/`apply`/`destroy` engine |
| OpenTofu | Open-source (MPL) fork; drop-in CLI (see [terraform_advanced_and_alternatives](../terraform_advanced_and_alternatives/)) |
| S3 + DynamoDB | Remote state + locking on AWS |
| `.terraform.lock.hcl` | Provider version + checksum lockfile (commit it) |
| `terraform fmt` / `validate` | Canonical formatting + syntax/type checks |
| `tflint` | Linter catching provider-specific mistakes |
| `tfsec` / `checkov` | Static security scanning of HCL |
| `terraform-docs` | Auto-generate module docs from variables/outputs |
| Atlantis | PR-driven plan/apply with locking and audit |
| Terragrunt | DRY backends/inputs across many states |
| Infracost | Cost estimate in the PR |

---

## 12. Interview Questions with Answers

**Q1: What is the Terraform state file and why is it critical?**
The state file is a JSON document mapping each resource in your config (e.g., `aws_instance.web`) to its real-world ID (`i-0abc123`) and caching its attributes, so Terraform can compute diffs without re-discovering everything. It is the source of truth Terraform trusts: if state is lost, Terraform thinks nothing exists and will try to recreate duplicates; if state disagrees with the cloud, the next plan reconciles toward state. Always store it remotely with locking and never edit it by hand without a backup.

**Q2: Explain the `init` → `plan` → `apply` lifecycle.**
`init` downloads providers and modules and configures the backend; `plan` refreshes state against the cloud, diffs it against your HCL, and prints proposed creates/updates/replaces/destroys; `apply` walks the dependency graph and executes that diff via provider APIs, then writes back state. `plan` is a safe dry run — the place you catch dangerous `-/+` replacements before they happen. In CI you save the plan (`terraform plan -out=tfplan`) and apply exactly that artifact so what's reviewed is what runs.

**Q3: Why is remote state with locking necessary for teams?**
Local state can't be safely shared, and concurrent applies against one state file corrupt it — two engineers each write a partial state, producing duplicated or orphaned resources. Remote backends (S3 + DynamoDB) serialize applies via a lock: the second `apply` waits until the first releases, and everyone reads the same authoritative state. This is the single most important step to move Terraform from solo use to team use.

**Q4: What's the difference between `count` and `for_each`, and which is safer?**
`count` creates resources indexed by integer (`[0]`, `[1]`), so removing a middle element re-indexes the rest — Terraform destroys and recreates resources that merely shifted position. `for_each` keys instances by a stable map/set key (`["api"]`, `["worker"]`), so adding or removing one leaves the others untouched. Prefer `for_each` for anything where identities matter; reserve `count` for simple "N identical copies" or conditional creation (`count = var.enabled ? 1 : 0`).

**Q5: What does it mean when a plan shows `-/+` (replace), and why is it dangerous?**
`-/+` means Terraform will destroy the existing resource and create a new one because you changed an immutable attribute (an AMI, certain RDS engine settings, a resource name). For stateless resources it's fine; for stateful ones (databases, EBS volumes) it can mean data loss and downtime. Always scrutinize `-/+` lines, protect critical resources with `lifecycle { prevent_destroy = true }`, and use `create_before_destroy` where a replacement must avoid a gap.

**Q6: How do you handle secrets in Terraform given that state stores them in plaintext?**
Any sensitive value passed to a resource is written unencrypted into the state JSON — `sensitive = true` only masks CLI output, not the stored value. So you encrypt state at rest (KMS on the S3 bucket), lock down bucket access, and never commit state to Git. Better, source secrets at apply time from an external store like Vault or AWS Secrets Manager (see [secrets_management](../secrets_management/)) so the secret of record lives outside Terraform, and use dynamic credentials where possible.

**Q7: What is drift and how do you detect and remediate it?**
Drift is when the real cloud resource diverges from what state/HCL describes — usually because someone changed it manually in the console. Detect it with `terraform plan -refresh-only` (shows cloud-vs-state differences without config changes) or a nightly `terraform plan -detailed-exitcode` in CI (exit code 2 means drift). Remediate by either accepting the change into HCL (commit it) or running `apply` to revert the cloud back to the declared state — the discipline of "change infra via PR, not console" prevents it in the first place.

**Q8: How do you bring an existing, manually-created resource under Terraform management?**
Use the declarative `import` block (Terraform 1.5+): write an `import { to = ..., id = ... }` block and a matching `resource` block, then `terraform plan -generate-config-out=...` can scaffold the HCL and `apply` records it in state. The older imperative `terraform import <addr> <id>` still works but doesn't generate config. After import, run a `plan` and reconcile until it shows no changes — proving your HCL matches the live resource exactly.

**Q9: What are Terraform modules and when should you write one?**
A module is a reusable, parameterized bundle of resources with `variable` inputs and `output` outputs — you write a VPC or EKS pattern once and instantiate it per environment. Write a module when you'll repeat a pattern, want to enforce standards (tagging, encryption defaults), or need to hide complexity behind a clean interface. Keep modules focused and composable; avoid the "one giant module that does everything," which becomes as hard to change as the infrastructure it replaced.

**Q10: Workspaces vs directory-per-environment — how do you isolate environments?**
Workspaces give one config multiple state files (`terraform workspace select prod`) — cheap and DRY, but they share the same backend and config, so a bad change can hit all environments and isolation is weak. Directory-per-environment (`envs/dev`, `envs/prod`, each with its own backend, ideally separate AWS accounts) gives strong blast-radius isolation at the cost of some duplication. For serious prod/dev separation, separate accounts/backends win; workspaces suit ephemeral or similar-shaped environments.

**Q11: How does Terraform order operations, and how do you control ordering?**
Terraform builds a directed acyclic graph from references — `aws_subnet.app` referencing `aws_vpc.main.id` creates an implicit dependency, so the VPC is created first; independent nodes run in parallel (default `-parallelism=10`). When there's no reference but a real ordering need (an IAM policy that must exist before a resource uses it indirectly), add explicit `depends_on`. Avoid overusing `depends_on`; prefer real references so the graph stays accurate.

**Q12: What's the purpose of `.terraform.lock.hcl` and should you commit it?**
It's the dependency lockfile recording the exact provider versions and their checksums that were selected, analogous to `package-lock.json`. Commit it so every engineer and CI runner resolves identical provider versions and gets byte-identical plans — without it, a `>= 5.0` constraint could silently pull a newer provider that changes defaults and proposes surprise diffs. Update it deliberately with `terraform init -upgrade` and review the resulting plan.

**Q13: How do `terraform state mv` and `terraform state rm` differ, and when do you use them?**
`state mv` renames or moves a resource within state (e.g., after refactoring `aws_instance.old` to `aws_instance.new`) so Terraform updates the address instead of destroying and recreating the real resource. `state rm` removes a resource from state without deleting it in the cloud — used to hand a resource off to another config or stop managing it. Both are state surgery: always `terraform state pull > backup.tfstate` first, because a mistake can orphan or duplicate real infrastructure.

**Q14: How do you reduce blast radius and plan time as infrastructure grows?**
Split one monolithic state into multiple smaller states by lifecycle/ownership (`network`, `iam`, `data`, `app`), each with its own backend key — this shrinks plan time, limits what any single apply can break, and lets teams own their slice. Share values across states via `terraform_remote_state` data sources or, better, published outputs in a parameter store. The tradeoff is cross-state references add coordination, so split along stable, low-churn boundaries.

**Q15: What changed with the Terraform-to-OpenTofu license fork, and does it affect your code?**
In August 2023 HashiCorp relicensed Terraform from MPL 2.0 to the BUSL (a source-available, non-compete license), prompting the Linux Foundation-backed OpenTofu fork that keeps the MPL and is a drop-in CLI replacement. Your HCL, providers, and workflow are largely identical, so migration is mostly swapping the binary and re-running `init`; the divergence to watch is newer features landing differently in each. Choose based on licensing comfort and the specific features (e.g., state encryption, early variable evaluation) each ships — covered in [terraform_advanced_and_alternatives](../terraform_advanced_and_alternatives/).

---

## 13. Best Practices

- Use a **remote backend with locking** (S3 + DynamoDB or native S3 lock in 1.10+) from day one; encrypt state and lock down bucket access.
- **Pin provider versions** with `~>` and **commit `.terraform.lock.hcl`** for reproducible plans.
- **Always review the plan**, especially `-/+ replace` lines; in CI, `plan -out=tfplan` then `apply tfplan` so reviewed == applied.
- Prefer **`for_each` over `count`** for stable addressing; protect stateful resources with `prevent_destroy`.
- **Never store secrets in HCL/state**; source them from [secrets_management](../secrets_management/) and KMS-encrypt state.
- **Split state by lifecycle/ownership** to bound blast radius and speed up plans.
- Run **`fmt`, `validate`, `tflint`, `tfsec`/`checkov`, and Infracost** in CI on every PR; gate with policy (see [policy_as_code_and_compliance](../policy_as_code_and_compliance/)).
- **Detect drift on a schedule** (`plan -detailed-exitcode`) and treat console changes as incidents to fold back into HCL.

---

## 14. Case Study

### Scenario: A startup outgrows local state and nearly destroys its production database

A 6-person startup runs Terraform from laptops with local state, sharing `terraform.tfstate` over a shared Dropbox. Two engineers apply on the same afternoon; their local states diverge, and the next apply orphans a NAT gateway and double-creates a load balancer. Worse, a junior engineer bumps an RDS `engine_version`, skims past the `-/+` replace line, and `apply` begins destroying the production database before they `Ctrl-C`.

```hcl
# BROKEN: local state, no locking, no protection on the database, secret in plaintext.
terraform {
  # no backend -> local terraform.tfstate, shared via Dropbox (corruption + race)
}
resource "aws_db_instance" "main" {
  engine         = "postgres"
  engine_version = "15.4"          # bumping this forces -/+ replace (data loss)
  username       = "admin"
  password       = "Sup3rSecret!"  # plaintext in HCL AND in state file
  # no lifecycle protection
}
```

```hcl
# FIX: remote locked state, KMS encryption, secret from Secrets Manager, destroy protection.
terraform {
  required_version = ">= 1.6"
  required_providers { aws = { source = "hashicorp/aws", version = "~> 5.40" } }
  backend "s3" {
    bucket         = "acme-tfstate-prod"
    key            = "data/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tf-locks"       # serialize applies -> no corruption
    encrypt        = true             # SSE-KMS on state at rest
  }
}

data "aws_secretsmanager_secret_version" "db" {
  secret_id = "prod/rds/admin"        # secret of record lives OUTSIDE Terraform
}

resource "aws_db_instance" "main" {
  engine         = "postgres"
  engine_version = "15.4"
  username       = "admin"
  password       = jsondecode(data.aws_secretsmanager_secret_version.db.secret_string)["password"]

  lifecycle {
    prevent_destroy = true            # apply errors out instead of destroying the DB
    ignore_changes  = [engine_version]  # version upgrades go through a controlled process
  }
}
```

With remote locking, concurrent applies serialize and state stays consistent. `prevent_destroy` turns the accidental RDS replacement into a hard error rather than a deleted database, and the version upgrade is now a deliberate, reviewed process. The password is pulled from AWS Secrets Manager at apply time, so it no longer sits in HCL (though it still lands in state — hence KMS encryption and locked-down bucket access, with rotation handled per [secrets_management](../secrets_management/)).

**Outcome:** state corruption incidents went to zero (locking), the near-miss database deletion became impossible (`prevent_destroy`), and the secret stopped living in version control. The team added a CI pipeline (`fmt` → `validate` → `tflint` → `plan -out`) with required review on the plan, so a dangerous `-/+` line can never reach `apply` unseen again.

**Discussion questions:**
1. Why does sharing local state over a file drive inevitably corrupt it, and exactly how does DynamoDB locking prevent that?
2. The password still appears in the state file even when sourced from Secrets Manager — what protects it, and what's the residual risk?
3. How would you safely perform the `engine_version` upgrade that `ignore_changes` now suppresses?

---

**Cross-references:** [terraform_advanced_and_alternatives](../terraform_advanced_and_alternatives/) (Terragrunt, Pulumi, OpenTofu, policy), [configuration_management](../configuration_management/) (server config after provisioning), [secrets_management](../secrets_management/) (keeping secrets out of state), [gitops_argocd_flux](../gitops_argocd_flux/) (declarative reconciliation for Kubernetes), [policy_as_code_and_compliance](../policy_as_code_and_compliance/) (Sentinel/OPA gating plans), [disaster_recovery_and_resilience](../disaster_recovery_and_resilience/) (rebuild a region via apply), [cloud_fundamentals_and_aws](../cloud_fundamentals_and_aws/) (the resources Terraform provisions).
