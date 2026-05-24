# Graph Neural Networks (GNNs)

## 1. Concept Overview

Graph Neural Networks extend deep learning to graph-structured data, where entities (nodes) have relationships (edges) that carry structural information. Unlike CNNs (fixed grid) or RNNs (fixed sequence), GNNs operate on irregular, variable-size topologies.

Core task: learn a function that maps a graph G = (V, E, X) — vertices, edges, node features — to node embeddings, edge embeddings, or a single graph-level embedding. These embeddings capture both the local neighborhood structure and the feature content of each node.

Applications span molecule property prediction, social network analysis, recommendation systems, fraud detection, knowledge graph completion, and traffic forecasting.

---

## 2. Intuition

One-line analogy: a GNN is a rumor-spreading machine — each node collects messages from its neighbors, updates its belief, and repeats for L rounds. After L rounds, each node's embedding reflects its L-hop neighborhood.

Mental model: think of node classification as a semi-supervised label propagation. Labels spread across edges weighted by structural similarity. GNNs learn to propagate feature information in a task-specific, differentiable way rather than using a fixed Laplacian.

Why it matters: most real-world data is relational. Tabular ML ignores structure; GNNs exploit it. A transaction node connected to 50 known-fraud nodes is itself suspicious — a GNN captures this; a feedforward network on transaction features alone cannot.

Key insight: the message-passing framework unifies GCN, GraphSAGE, GAT, and GIN under one abstraction. Understanding that abstraction unlocks the entire field.

---

## 3. Core Principles

**Message Passing Neural Networks (MPNN):** the unifying framework. Each layer performs:

```
m_v^(l) = AGGREGATE({MSG(h_u^(l-1), h_v^(l-1), e_uv) : u in N(v)})
h_v^(l) = UPDATE(h_v^(l-1), m_v^(l))
```

Where MSG is a message function (often a linear transform), AGGREGATE is permutation-invariant (sum, mean, max), and UPDATE is typically an MLP or GRU.

**Graph representation:** an undirected graph with N nodes is represented as:
- Node feature matrix X of shape (N, d_in)
- Adjacency matrix A of shape (N, N), often sparse
- A_hat = A + I (self-loops added so a node aggregates its own features)
- D = diagonal degree matrix, D_ii = sum_j A_ij

**Permutation invariance:** the output must not depend on the arbitrary ordering of nodes in the adjacency matrix. Aggregation functions (sum, mean, max) are permutation-invariant by construction.

**Inductive vs. transductive:** transductive GNNs (GCN) require the full graph at training time; inductive GNNs (GraphSAGE) sample neighborhoods and generalize to new nodes/graphs.

**Expressivity — Weisfeiler-Leman (WL) test:** standard GNNs are at most as expressive as the 1-WL graph isomorphism test. Two non-isomorphic graphs that fool the WL test also fool standard GNNs. GIN achieves WL-equivalent expressivity; higher-order GNNs (k-WL) go beyond but are expensive.

---

## 4. Types / Architectures / Strategies

| Architecture | Aggregation | Normalization | Inductive | Expressivity | Best For |
|---|---|---|---|---|---|
| GCN | Weighted mean | Symmetric D^(-1/2) A_hat D^(-1/2) | No (full graph) | < WL | Node classification, small graphs |
| GraphSAGE | Mean / Max / LSTM | Row-wise (D^-1 A) | Yes (sampling) | < WL | Large graphs, new nodes |
| GAT | Learned attention | Softmax over neighbors | Yes | < WL | Heterophily, noisy graphs |
| GIN | Sum + MLP | None (epsilon trick) | Yes | = WL | Graph classification, expressivity |
| MPNN | General | Configurable | Yes | <= WL | Molecular property prediction |
| GraphTransformer | Global attention | Layer norm | Yes | > WL | Small graphs, full attention |

**GCN (Kipf & Welling 2017):**
```
H^(l+1) = sigma(D_hat^(-1/2) A_hat D_hat^(-1/2) H^(l) W^(l))
```
Spectral interpretation: this is a localized first-order approximation of spectral graph convolution. The symmetric normalization prevents scale explosion for high-degree nodes.

**GraphSAGE (Hamilton 2017):**
Sample k neighbors per node (k=25 for hop-1, k=10 for hop-2 in the original paper). Concat own embedding with aggregated neighbor embedding, then apply linear + nonlinearity. Enables mini-batch training on billion-node graphs.

**GAT (Velickovic 2018):**
```
alpha_ij = softmax_j(LeakyReLU(a^T [W h_i || W h_j]))
h_i^(l+1) = sigma(sum_j alpha_ij W h_j)
```
Multi-head attention (K=8 heads typical) stabilizes training. Concatenate heads in hidden layers, average at final layer.

**GIN (Xu 2019):**
```
h_v^(l+1) = MLP((1 + epsilon) * h_v^(l) + sum_{u in N(v)} h_u^(l))
```
Epsilon is a learnable scalar or fixed to 0. Sum aggregation (not mean/max) is critical for injectivity. Theoretically the most expressive standard GNN.

---

## 5. Architecture Diagrams

```
Message Passing — Single Layer
==============================

        Node A (h_A)
       /             \
 (e_CA)               (e_AB)
     /                   \
Node C (h_C)         Node B (h_B)
     \                   /
 (e_CD)               (e_BD)
       \             /
        Node D (h_D)

Step 1: AGGREGATE messages from neighbors of A:
  m_A = AGG({MSG(h_B, e_AB), MSG(h_C, e_CA)})

Step 2: UPDATE node A:
  h_A' = UPDATE(h_A, m_A)


GCN Layer — Matrix Form
========================

  Input H^(l): [N x d_in]
  Weight W^(l): [d_in x d_out]
  A_hat = A + I (self-loops)
  D_hat = degree of A_hat

  A_norm = D_hat^(-1/2) * A_hat * D_hat^(-1/2)   [N x N sparse]
  H^(l+1) = sigma( A_norm * H^(l) * W^(l) )       [N x d_out]


GraphSAGE Mini-Batch — 2-Hop Sampling
======================================

  Target node v
       |
  Sample k1=25 hop-1 neighbors
       |
  For each hop-1 neighbor, sample k2=10 hop-2 neighbors
       |
  Forward pass (bottom-up):
    hop-2 embeddings -> aggregate -> hop-1 embeddings
    hop-1 embeddings -> aggregate -> target embedding


GAT Attention Mechanism
========================

  Node i: h_i  ---\
                    [concat] -> linear (a) -> LeakyReLU -> exp
  Node j: h_j  ---/
                           |
                        alpha_ij (unnormalized)
                           |
                    softmax over all j in N(i)
                           |
                    weighted sum of W*h_j


Graph-Level Readout (Pooling)
==============================

  Node embeddings {h_1, ..., h_N}
         |
  [Global Mean Pool / Sum Pool / Max Pool / DiffPool / SAGPool]
         |
  Graph embedding g  [1 x d]
         |
  MLP -> graph-level prediction
```

---

## 6. How It Works — Detailed Mechanics

```python
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch_geometric.nn import GCNConv, GATConv, SAGEConv, GINConv
from torch_geometric.nn import global_mean_pool, global_add_pool
from torch_geometric.data import Data, DataLoader
from torch_geometric.utils import add_self_loops, degree
from typing import Optional, Tuple
import numpy as np


# ── Minimal GCN from scratch (educational) ──────────────────────────────────

class GCNLayerFromScratch(nn.Module):
    """
    GCN: H^(l+1) = sigma(D^(-1/2) A_hat D^(-1/2) H^(l) W^(l))
    """
    def __init__(self, in_channels: int, out_channels: int) -> None:
        super().__init__()
        self.linear = nn.Linear(in_channels, out_channels, bias=False)
        nn.init.xavier_uniform_(self.linear.weight)

    def forward(
        self,
        x: torch.Tensor,           # [N, in_channels]
        edge_index: torch.Tensor,  # [2, E]  — COO format
        num_nodes: int,
    ) -> torch.Tensor:
        # Add self-loops
        edge_index, _ = add_self_loops(edge_index, num_nodes=num_nodes)

        # Compute degree for normalization
        row, col = edge_index
        deg = degree(col, num_nodes=num_nodes, dtype=x.dtype)  # [N]
        deg_inv_sqrt = deg.pow(-0.5)
        deg_inv_sqrt[deg_inv_sqrt == float('inf')] = 0.0        # isolated nodes

        # Symmetric normalization: D^(-1/2) A_hat D^(-1/2)
        norm = deg_inv_sqrt[row] * deg_inv_sqrt[col]            # [E]

        # Aggregate: for each target node, sum normalized source features
        out = torch.zeros(num_nodes, x.size(1), device=x.device)
        out.scatter_add_(0, col.unsqueeze(1).expand(-1, x.size(1)),
                         x[row] * norm.unsqueeze(1))

        return F.relu(self.linear(out))


# ── Two-layer GCN (node classification) ─────────────────────────────────────

class GCN(nn.Module):
    def __init__(self, in_channels: int, hidden: int, out_channels: int,
                 dropout: float = 0.5) -> None:
        super().__init__()
        self.conv1 = GCNConv(in_channels, hidden)
        self.conv2 = GCNConv(hidden, out_channels)
        self.dropout = dropout

    def forward(self, x: torch.Tensor, edge_index: torch.Tensor) -> torch.Tensor:
        x = F.relu(self.conv1(x, edge_index))
        x = F.dropout(x, p=self.dropout, training=self.training)
        x = self.conv2(x, edge_index)
        return F.log_softmax(x, dim=1)   # node-level class logits


# ── GAT (Graph Attention Network) ───────────────────────────────────────────

class GAT(nn.Module):
    def __init__(self, in_channels: int, hidden: int, out_channels: int,
                 heads: int = 8, dropout: float = 0.6) -> None:
        super().__init__()
        # Hidden: multi-head concat -> hidden*heads features
        self.conv1 = GATConv(in_channels, hidden, heads=heads,
                             dropout=dropout, concat=True)
        # Output: average heads
        self.conv2 = GATConv(hidden * heads, out_channels, heads=1,
                             dropout=dropout, concat=False)
        self.dropout = dropout

    def forward(self, x: torch.Tensor, edge_index: torch.Tensor) -> torch.Tensor:
        x = F.dropout(x, p=self.dropout, training=self.training)
        x = F.elu(self.conv1(x, edge_index))
        x = F.dropout(x, p=self.dropout, training=self.training)
        x = self.conv2(x, edge_index)
        return F.log_softmax(x, dim=1)


# ── GIN for graph classification ─────────────────────────────────────────────

class GIN(nn.Module):
    def __init__(self, in_channels: int, hidden: int, out_channels: int,
                 num_layers: int = 5) -> None:
        super().__init__()
        self.convs = nn.ModuleList()
        self.bns = nn.ModuleList()

        for i in range(num_layers):
            in_c = in_channels if i == 0 else hidden
            mlp = nn.Sequential(
                nn.Linear(in_c, hidden),
                nn.BatchNorm1d(hidden),
                nn.ReLU(),
                nn.Linear(hidden, hidden),
            )
            self.convs.append(GINConv(mlp, train_eps=True))
            self.bns.append(nn.BatchNorm1d(hidden))

        self.head = nn.Sequential(
            nn.Linear(hidden, hidden),
            nn.ReLU(),
            nn.Dropout(0.5),
            nn.Linear(hidden, out_channels),
        )

    def forward(self, x: torch.Tensor, edge_index: torch.Tensor,
                batch: torch.Tensor) -> torch.Tensor:
        for conv, bn in zip(self.convs, self.bns):
            x = F.relu(bn(conv(x, edge_index)))

        # Graph-level readout: sum pooling (GIN paper uses sum)
        x = global_add_pool(x, batch)  # [num_graphs, hidden]
        return self.head(x)


# ── GraphSAGE for large-scale inductive learning ────────────────────────────

class GraphSAGE(nn.Module):
    def __init__(self, in_channels: int, hidden: int, out_channels: int,
                 num_layers: int = 3, aggr: str = 'mean') -> None:
        super().__init__()
        self.convs = nn.ModuleList()
        for i in range(num_layers):
            in_c = in_channels if i == 0 else hidden
            out_c = out_channels if i == num_layers - 1 else hidden
            self.convs.append(SAGEConv(in_c, out_c, aggr=aggr))

    def forward(self, x: torch.Tensor, edge_index: torch.Tensor) -> torch.Tensor:
        for i, conv in enumerate(self.convs):
            x = conv(x, edge_index)
            if i < len(self.convs) - 1:
                x = F.relu(x)
                x = F.dropout(x, p=0.5, training=self.training)
        return x


# ── Mini-batch training with NeighborSampler ─────────────────────────────────

def train_with_neighbor_sampling(
    model: nn.Module,
    data: Data,
    epochs: int = 200,
    num_neighbors: list[int] = [25, 10],   # hop-1: 25, hop-2: 10
) -> None:
    from torch_geometric.loader import NeighborLoader

    train_loader = NeighborLoader(
        data,
        num_neighbors=num_neighbors,
        batch_size=1024,
        input_nodes=data.train_mask,
        shuffle=True,
    )

    optimizer = torch.optim.Adam(model.parameters(), lr=0.01, weight_decay=5e-4)

    model.train()
    for epoch in range(epochs):
        total_loss = 0.0
        for batch in train_loader:
            optimizer.zero_grad()
            out = model(batch.x, batch.edge_index)
            # Only compute loss on seed nodes (first batch_size nodes)
            loss = F.nll_loss(out[:batch.batch_size],
                              batch.y[:batch.batch_size])
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        if epoch % 20 == 0:
            print(f"Epoch {epoch:03d}, Loss: {total_loss/len(train_loader):.4f}")


# ── Oversmoothing demo — Jumping Knowledge Networks ──────────────────────────

class JKNet(nn.Module):
    """Jumping Knowledge: concatenate all layer representations."""
    def __init__(self, in_channels: int, hidden: int, out_channels: int,
                 num_layers: int = 6) -> None:
        super().__init__()
        self.convs = nn.ModuleList()
        for i in range(num_layers):
            in_c = in_channels if i == 0 else hidden
            self.convs.append(GCNConv(in_c, hidden))

        # Aggregate across all layers: concat -> project
        self.head = nn.Linear(hidden * num_layers, out_channels)

    def forward(self, x: torch.Tensor, edge_index: torch.Tensor) -> torch.Tensor:
        layer_outputs: list[torch.Tensor] = []
        for conv in self.convs:
            x = F.relu(conv(x, edge_index))
            layer_outputs.append(x)

        # JK aggregation: concatenate all intermediate representations
        x = torch.cat(layer_outputs, dim=-1)  # [N, hidden * num_layers]
        return F.log_softmax(self.head(x), dim=1)
```

**Training recipe for node classification:**
- Adam, lr=0.01, weight_decay=5e-4
- 2-layer GCN converges in ~200 epochs on Cora
- Dropout(0.5) between layers is critical (prevents overfit on small graphs)
- Normalize features to zero mean unit variance before training

**Training recipe for graph classification (GIN):**
- Adam, lr=0.01, decay 0.5 every 50 epochs
- 5-layer GIN with hidden=64 or 128
- Sum pooling outperforms mean pooling for count-sensitive tasks
- Train/val/test split: 80/10/10 across graphs

---

## 7. Real-World Examples

**Pinterest PinSage (2018) — recommendation at scale:**
Graph of 3 billion nodes (pins + boards), 18 billion edges. GraphSAGE-style with importance-based neighbor sampling (random walk visits, not uniform). Trained on 16 GPUs. Offline: +30% hit rate vs. visual features alone. Deployed: 98th percentile latency <100ms via precomputed embeddings refreshed daily.

**Alibaba fraud detection:**
Transaction graph where nodes are accounts, edges are transactions. GAT to weight suspicious neighbors higher. Positive-unlabeled learning because most fraud is unlabeled. Result: 12% improvement in fraud recall vs. gradient boosted trees.

**DeepMind AlphaFold2 (partial):**
Evoformer block is a form of graph attention on residue pairs. Captures inter-residue contact information. Solved protein structure prediction with atomic accuracy.

**Drug discovery — MolBERT / MPNN:**
Atoms as nodes, bonds as edges, predict solubility/toxicity/binding affinity. Graph-level regression using MPNN. Outperforms fingerprint-based models by 5-15% RMSE on benchmark datasets.

**Traffic forecasting — DCRNN:**
Road network as directed graph. Node = road segment, edge = connectivity. Diffusion convolution + seq2seq. Used by Grab and DiDi for ETA prediction.

---

## 8. Tradeoffs

| Dimension | GCN | GraphSAGE | GAT | GIN |
|---|---|---|---|---|
| Scalability | Poor (full graph) | Excellent (sampling) | Good (sampling-compatible) | Good |
| Expressivity | < WL | < WL | < WL | = WL |
| Handles heterophily | No | Partial | Yes (attention filters) | No |
| Inductive (new nodes) | No | Yes | Yes | Yes |
| Interpretability | Low | Low | Medium (attention weights) | Low |
| Training cost | O(E*d) | O(k^L * d) per node | O(E*d*K) (K heads) | O(E*d) |
| Memory | O(N*d) | O(B*k^L) mini-batch | O(N*d + E*K) | O(N*d) |

**Oversmoothing vs. depth:** adding more layers hurts after L=2–4 on most benchmarks. Deep GNNs push node representations toward the graph's dominant eigenvector, destroying discriminative information.

**Heterophily:** GCN assumes similar nodes connect (homophily). Social networks of friends satisfy this; fraud graphs do not (fraudsters connect to legitimate nodes). Use GAT or heterophily-specific models (H2GCN, FAGCN) when homophily ratio < 0.3.

---

## 9. When to Use / When NOT to Use

**Use GNNs when:**
- Data has explicit relational structure (social graphs, molecular graphs, knowledge graphs, road networks)
- Structural patterns are predictive (fraud rings, molecular substructures)
- Entity features alone are insufficient (neighborhood context matters)
- You have graph-level labels (molecule classification)

**Do NOT use GNNs when:**
- Data is tabular with no meaningful relational structure (most business ML)
- Graph must be constructed artificially (k-NN graph on tabular data) — usually hurts vs. XGBoost
- You have very few labeled nodes (<50) — Gaussian processes or label propagation may suffice
- Latency is critical (<5ms) and graph has millions of nodes — precomputation required
- Graph changes frequently (real-time edges) — streaming GNN infrastructure is immature

**Scale considerations:**
- <100K nodes: full-batch GCN fine
- 100K–10M nodes: NeighborSampler or ClusterGCN
- >10M nodes: GraphSAGE with importance sampling + distributed training (PyG + DDP)

---

## 10. Common Pitfalls

**Pitfall 1 — Data leakage via edges:**
A team built a fraud detection GNN. Edges represented "same device used." They split nodes (transactions) into train/test randomly. Test nodes had edges into training nodes, so the GNN could see test labels indirectly via message passing. Result: reported AUC 0.97 on test, deployed AUC 0.71. Fix: always do inductive split — test nodes must be completely unseen during training. Use `train_mask` that disconnects from future timestamps.

**Pitfall 2 — Forgetting to add self-loops:**
GCN without self-loops: a node does not aggregate its own features. For isolated nodes this produces zero embeddings. Many real graphs have sinks (nodes with no incoming edges). Fix: always call `add_self_loops` before normalization, or use `GCNConv(add_self_loops=True)` which is the default.

**Pitfall 3 — Mean aggregation destroys count information:**
Team used GraphSAGE with mean aggregation for molecular property prediction. Two molecules — one with 3 carbon rings, one with 6 — produced identical embeddings because mean normalized counts away. Fix: use sum aggregation (GIN) for tasks where the number of structural patterns matters.

**Pitfall 4 — Oversmoothing in deep GNNs:**
Stacking 8 GCN layers: training accuracy 62%, same as random. Node embeddings had cosine similarity >0.99 after layer 6. Fix: limit to 2–3 layers for most tasks. If depth is needed, use residual connections, initial residual (GCNII: `H^(l) = (1-alpha)*H^(l-1)*W + alpha*H^(0)*W`), or Jumping Knowledge Networks.

**Pitfall 5 — Treating attention weights as explanations:**
A team showed business stakeholders GAT attention weights as "feature importance." But attention weights are input-dependent, not globally interpretable, and can be adversarially manipulated. Fix: use GNNExplainer or subgraph-based explanations for post-hoc interpretability.

**Pitfall 6 — Ignoring edge features:**
A knowledge graph GNN ignored relation types on edges (is-a, works-for, located-in treated identically). Performance on link prediction was 15% below a relation-aware model (R-GCN). Fix: use edge-conditioned convolutions or RGCN when edge types carry semantic information.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|---|---|---|
| PyTorch Geometric (PyG) | GNN layers, datasets, samplers | Industry standard; 20k+ GitHub stars |
| DGL (Deep Graph Library) | Alternative to PyG, MXNet/PyTorch | Better for dynamic graphs |
| NetworkX | Graph construction, analysis | CPU-only; preprocessing |
| OGB (Open Graph Benchmark) | Benchmark datasets (ogbn-arxiv, ogbg-molhiv) | Standard eval protocol |
| GraphBolt | PyG 2.4+ large-scale data loading | Replaces NeighborLoader for TB-scale |
| Spektral | Keras/TF GNN library | TensorFlow ecosystem |
| Stellargraph | Enterprise GNN library | Built on TF/Keras |
| ArangoDB / Neo4j | Graph databases for serving | Store edges for real-time GNN inference |

**Hardware:** single A100 handles graphs up to ~5M nodes full-batch. Beyond that, multi-GPU with DDP + NeighborSampler. Graph partitioning via METIS for ClusterGCN.

---

## 12. Interview Questions with Answers

**Q: What is the message passing framework and how does it unify different GNN architectures?**
Message passing defines GNN computation as: aggregate messages from neighbors, then update the node's own state. GCN uses symmetric-normalized weighted mean aggregation with a shared weight matrix. GraphSAGE samples neighborhoods and uses mean/max/LSTM aggregation with concatenation. GAT learns per-neighbor attention weights. GIN uses sum aggregation with an MLP. All are special cases of the MPNN formulation from Gilmer et al. 2017.

**Q: Why does GCN use symmetric normalization D^(-1/2) A_hat D^(-1/2) instead of row normalization D^-1 A?**
Symmetric normalization preserves the graph's spectral properties and produces a symmetric, PSD normalized adjacency. Row normalization (D^-1 A) is asymmetric — it treats each neighbor equally regardless of their degree. Symmetric normalization downweights contributions from high-degree nodes both as sources and targets, preventing hub nodes from dominating all embeddings. Spectral interpretation: it is the first-order approximation of spectral graph convolution with Chebyshev polynomials.

**Q: What is oversmoothing and how do you fix it?**
Oversmoothing occurs when stacking many GNN layers causes all node embeddings to converge to the same vector — the dominant eigenvector of the graph's normalized Laplacian. After 6+ layers, cosine similarity between node pairs approaches 1.0, destroying discriminative information. Fixes: (1) limit depth to 2–4 layers (most effective), (2) residual connections (add H^(l-1) to H^(l)), (3) Jumping Knowledge Networks (concatenate all layer outputs), (4) GCNII (initial residual — blend with H^0 at every layer), (5) DropEdge (randomly drop edges during training).

**Q: Why is GIN more expressive than GCN or GraphSAGE?**
GIN achieves the same discriminative power as the Weisfeiler-Leman (WL) graph isomorphism test — the theoretical upper bound for standard MPNNs. The key is sum aggregation: sum is injective over multisets (it preserves count information), while mean and max are not. If two neighborhood multisets differ, sum produces different aggregated values, while mean could collapse them. The (1+epsilon) factor ensures the center node is counted distinctly from its neighbors, making the full update injective.

**Q: How does GAT compute attention weights and why does multi-head attention help?**
GAT computes attention as: e_ij = LeakyReLU(a^T [W h_i || W h_j]), then alpha_ij = softmax_j(e_ij). The shared weight vector a and matrix W are learned end-to-end. Multi-head attention (K=8 heads typically) runs K independent attention mechanisms and concatenates (hidden layers) or averages (output layer) results. This stabilizes training — a single attention head can get stuck in degenerate solutions (all weight on one neighbor). Different heads specialize in different structural patterns.

**Q: What is the difference between transductive and inductive GNN settings?**
Transductive: the model sees all nodes (including test nodes) during training — just without their labels. GCN is transductive because it needs the full adjacency matrix for normalization. New nodes cannot be handled without retraining. Inductive: the model learns a function mapping node neighborhoods to embeddings, generalizing to unseen nodes and graphs. GraphSAGE and GAT are inductive — they sample and aggregate from a node's local neighborhood, which can be computed for any new node at inference time.

**Q: How would you scale GNN training to a graph with 1 billion nodes?**
Full-batch GCN is infeasible — the adjacency matrix alone is TBs. Use neighborhood sampling (GraphSAGE/NeighborSampler): sample k1=15 hop-1 and k2=5 hop-2 neighbors per batch node, giving fixed computation per mini-batch regardless of graph size. For better cluster locality, use ClusterGCN (partition graph into 1000+ clusters with METIS, sample mini-batches within clusters to reduce cross-partition edges). Distributed: partition graph across machines with PyG + torch.distributed. Pinterest uses this to train on 3B nodes.

**Q: What is the WL test and why does it matter for GNNs?**
The Weisfeiler-Leman (1-WL) graph isomorphism test iteratively colors nodes based on their neighbor multisets. If two graphs produce different colorings, they are non-isomorphic. Standard GNNs are at most as powerful as 1-WL — two non-isomorphic graphs that fool 1-WL (e.g., regular graphs with same degree sequence) also fool GCNs and GraphSAGEs. GIN achieves 1-WL expressivity. Higher-order GNNs (k-WL, k=3) can distinguish more graphs but have O(N^k) complexity.

**Q: How do you handle heterogeneous graphs (multiple node/edge types)?**
Use Relational GCN (R-GCN): separate weight matrices W_r per relation type r, aggregate as sum_r sum_{u in N_r(v)} (1/|N_r(v)|) W_r h_u. For many relation types, use basis decomposition (W_r = sum_b a_{rb} V_b) to reduce parameters. PyG supports heterogeneous graphs via `HeteroData` and `to_hetero()` wrapper. Knowledge graph link prediction (TransE, RotatE) is a simpler alternative when you only need entity/relation embeddings without neighborhood aggregation.

**Q: What are common approaches for link prediction with GNNs?**
After computing node embeddings h_u and h_v, score an edge as: (1) dot product h_u^T h_v — fast but limited, (2) MLP([h_u || h_v]) — more expressive, (3) Hadamard product h_u * h_v passed through MLP. Training: positive edges from the graph, negative edges sampled uniformly (random negative sampling) or hard negatives (nodes close in embedding space). Loss: binary cross-entropy or margin-based hinge loss. Key pitfall: negative sampling strategy heavily impacts performance — hard negatives (~5x positive count) typically outperform random negatives.

**Q: How does PinSage differ from standard GraphSAGE?**
PinSage uses importance-based neighbor sampling: instead of uniform random sampling, run short random walks from a node, accumulate visit counts, sample the top-T most-visited neighbors. This focuses computation on the most relevant neighbors. It also uses curriculum learning (progressively harder negatives during training) and production engineering (map-reduce for offline embedding generation, approximate nearest neighbor for online retrieval). These changes translate a research algorithm into a system serving billions of recommendations daily.

---

## 13. Best Practices

- Start with a 2-layer GCN baseline before trying GAT or GIN. Depth rarely helps beyond 3 layers.
- Always add self-loops to the adjacency matrix (so a node can use its own features).
- Normalize node features before training — zero mean, unit variance per feature.
- Use dropout (0.5) between GNN layers. For GAT, apply dropout to both input features and attention coefficients.
- For large graphs (>100K nodes), use NeighborSampler or ClusterGCN — never full-batch.
- Use sum pooling for graph classification when count information matters (molecular fingerprints). Use mean pooling when size-invariant representations are needed.
- Monitor node embedding similarity distributions across layers to detect oversmoothing early.
- For class imbalance (fraud: 0.1% positive), use focal loss or oversample positive nodes in mini-batches.
- Use OGB benchmark datasets (ogbn-arxiv, ogbg-molhiv) with standard splits — avoid custom splits that inflate numbers.
- Always do inductive evaluation for production: train on graph G_train, evaluate on G_test with no shared nodes.

---

## 14. Case Study

**Scenario:** A professional network (950M users, 70M companies) faces a surge in fraud rings - coordinated groups of fake accounts that endorse each other, apply for jobs in bulk, and scrape recruiter contact data. Traditional ML detects isolated fake accounts (precision 0.71, recall 0.58) but misses ring structures where individual node features appear legitimate. The goal: detect fraud rings of 5-500 accounts using GraphSAGE, achieving precision >= 0.88, recall >= 0.82, with batch scoring of 5M flagged candidate accounts within 6 hours, and real-time single-node scoring in under 80ms (p99).

**Architecture:**
```
Raw Account Data (950M nodes)
  - Node features: profile completeness, account age, connection velocity,
    post frequency, endorsement given/received ratio (128 features)
  - Edge types: connection, endorsement, message_sent, shared_ip, co-applied_job
         |
         v
Graph Construction (PyG + NetworkX)
  Heterogeneous graph:
    nodes: accounts (950M), companies (70M)
    edges: connection (5.8B), endorsement (1.2B), shared_ip (200M)
         |
         v
Mini-batch GraphSAGE Training
  2-hop neighbourhood sampling (fanout [25, 10])
  Hidden dim: 256, 3 layers, mean aggregator
  Node classification: fraud (1) / legitimate (0)
  Trained on 500K labelled nodes (abuse team labels)
         |
         v
Ring Detection Post-processing
  Extract fraud-score > 0.7 subgraph
  Connected components -> candidate rings
  Ring features: size, density, avg_score, formation_age
  Ring classifier (GBM) -> confirmed ring / false positive
         |
         v
Action Engine
  score > 0.9  -> auto-restrict
  0.7-0.9      -> human review queue
  ring detected -> coordinated action (restrict all members)
```

**Step-by-step implementation:**

```python
from __future__ import annotations
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch_geometric.nn import SAGEConv
from torch_geometric.data import HeteroData
from torch_geometric.loader import NeighborLoader
import numpy as np

class FraudGraphSAGE(nn.Module):
    def __init__(
        self,
        in_channels: int,
        hidden_channels: int = 256,
        out_channels: int = 2,
        num_layers: int = 3,
        dropout: float = 0.3,
    ) -> None:
        super().__init__()
        self.convs = nn.ModuleList()
        self.bns = nn.ModuleList()
        self.dropout = dropout

        self.convs.append(SAGEConv(in_channels, hidden_channels, aggr="mean"))
        self.bns.append(nn.BatchNorm1d(hidden_channels))
        for _ in range(num_layers - 2):
            self.convs.append(SAGEConv(hidden_channels, hidden_channels, aggr="mean"))
            self.bns.append(nn.BatchNorm1d(hidden_channels))
        self.convs.append(SAGEConv(hidden_channels, out_channels, aggr="mean"))

    def forward(
        self, x: torch.Tensor, edge_index: torch.Tensor
    ) -> torch.Tensor:
        for i, (conv, bn) in enumerate(zip(self.convs[:-1], self.bns)):
            x = conv(x, edge_index)
            x = bn(x)
            x = F.relu(x)
            x = F.dropout(x, p=self.dropout, training=self.training)
        return self.convs[-1](x, edge_index)

def build_neighbor_loader(
    data: HeteroData,
    node_indices: torch.Tensor,
    num_neighbors: list[int] = [25, 10, 5],
    batch_size: int = 1024,
    shuffle: bool = True,
) -> NeighborLoader:
    return NeighborLoader(
        data,
        num_neighbors=num_neighbors,
        batch_size=batch_size,
        input_nodes=("account", node_indices),
        shuffle=shuffle,
        num_workers=4,
        persistent_workers=True,
    )
```

```python
from torch_geometric.utils import to_networkx
import networkx as nx
from sklearn.ensemble import GradientBoostingClassifier

def train_graphsage(
    model: FraudGraphSAGE,
    loader: NeighborLoader,
    optimizer: torch.optim.Optimizer,
    device: torch.device,
    class_weight_fraud: float = 10.0,   # fraud is rare: ~0.2% of accounts
) -> float:
    model.train()
    total_loss = 0.0
    weights = torch.tensor([1.0, class_weight_fraud], device=device)
    criterion = nn.CrossEntropyLoss(weight=weights)

    for batch in loader:
        batch = batch.to(device)
        optimizer.zero_grad()
        out = model(batch["account"].x, batch["account", "connection", "account"].edge_index)
        # Only compute loss on seed nodes (first batch_size nodes in mini-batch)
        out = out[:batch["account"].batch_size]
        y = batch["account"].y[:batch["account"].batch_size]
        loss = criterion(out, y)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()
        total_loss += float(loss) * batch["account"].batch_size

    return total_loss / len(loader.dataset)

@torch.no_grad()
def score_nodes(
    model: FraudGraphSAGE,
    loader: NeighborLoader,
    device: torch.device,
) -> tuple[np.ndarray, np.ndarray]:
    model.eval()
    all_probs: list[torch.Tensor] = []
    all_labels: list[torch.Tensor] = []

    for batch in loader:
        batch = batch.to(device)
        out = model(batch["account"].x, batch["account", "connection", "account"].edge_index)
        out = out[:batch["account"].batch_size]
        probs = F.softmax(out, dim=1)[:, 1]
        all_probs.append(probs.cpu())
        all_labels.append(batch["account"].y[:batch["account"].batch_size].cpu())

    return torch.cat(all_probs).numpy(), torch.cat(all_labels).numpy()
```

```python
def detect_fraud_rings(
    node_ids: np.ndarray,
    fraud_scores: np.ndarray,
    edge_list: list[tuple[int, int]],
    score_threshold: float = 0.7,
    min_ring_size: int = 5,
    max_ring_size: int = 500,
) -> list[dict]:
    # Build subgraph of high-score nodes
    high_score_mask = fraud_scores >= score_threshold
    high_score_ids = set(node_ids[high_score_mask].tolist())

    G = nx.Graph()
    G.add_nodes_from(high_score_ids)
    for u, v in edge_list:
        if u in high_score_ids and v in high_score_ids:
            G.add_edge(u, v)

    rings: list[dict] = []
    for component in nx.connected_components(G):
        if not (min_ring_size <= len(component) <= max_ring_size):
            continue
        subgraph = G.subgraph(component)
        member_scores = fraud_scores[np.isin(node_ids, list(component))]
        rings.append({
            "member_ids": list(component),
            "size": len(component),
            "density": nx.density(subgraph),
            "avg_fraud_score": float(member_scores.mean()),
            "max_fraud_score": float(member_scores.max()),
            "internal_edges": subgraph.number_of_edges(),
        })

    return sorted(rings, key=lambda r: r["avg_fraud_score"], reverse=True)
```

**Key pitfalls (3 with BROKEN->FIX):**

**Pitfall 1 - Neighbour explosion when sampling 3-hop neighbourhoods for hub nodes:**
```python
# BROKEN: fixed large fanout causes memory OOM for hub nodes with 50K+ connections
loader = NeighborLoader(data, num_neighbors=[50, 50, 50])  # 50^3 = 125K nodes in worst case
# Hub account (influencer) with 50K connections samples 50K * 50 * 50 = 125M neighbours -> OOM

# FIX: cap fanout and add node degree normalization in features
loader = NeighborLoader(data, num_neighbors=[25, 10, 5])   # max 1250 sampled neighbours
# Additionally, cap edge weights by log(1 + degree) so hubs don't dominate aggregation
x[:, degree_feature_idx] = torch.log1p(x[:, degree_feature_idx])
```

**Pitfall 2 - Training on temporally mixed data leaks future graph structure:**
```python
# BROKEN: edge_index includes connections formed after the fraud label was assigned
# The model sees "A connected to known-fraud B after B was flagged" -> trivial pattern
data = build_graph(all_edges, labels)   # uses all historical edges
train_loader = NeighborLoader(data, input_nodes=train_mask)  # future edges included

# FIX: construct graph using only edges that existed before the label timestamp
def build_temporal_graph(edges_df, labels_df, snapshot_date):
    edges_before = edges_df[edges_df["created_at"] < snapshot_date]
    return build_graph(edges_before, labels_df)
snapshot = labels_df["flagged_at"].min()   # use earliest label date as cutoff
data = build_temporal_graph(edges_df, labels_df, snapshot)
```

**Pitfall 3 - Using accuracy or cross-entropy without class weighting on 0.2% fraud rate:**
```python
# BROKEN: model predicts all-legitimate; accuracy 99.8%, loss minimal, recall 0%
model = FraudGraphSAGE(in_channels=128)
criterion = nn.CrossEntropyLoss()   # no class weights
# Training converges to all-negative predictions in 2 epochs

# FIX: compute class weights from training set; use focal loss as alternative
n_fraud = int(train_labels.sum())
n_legit = len(train_labels) - n_fraud
weight_fraud = n_legit / n_fraud   # ~500x weight for fraud class
weights = torch.tensor([1.0, weight_fraud])
criterion = nn.CrossEntropyLoss(weight=weights)
# Alternative: focal loss with gamma=2.0 downweights easy negatives automatically
```

**Metrics and results:**

| Metric | Traditional ML (node-only) | GraphSAGE + ring detection |
|---|---|---|
| Precision (node-level) | 0.71 | 0.89 |
| Recall (node-level) | 0.58 | 0.83 |
| F1 (node-level) | 0.64 | 0.86 |
| Ring detection precision | N/A | 0.91 |
| Ring detection recall | N/A | 0.79 |
| Fraud accounts actioned/day | 12,000 | 34,000 |
| False positive rate | 1.2% | 0.38% |
| Batch scoring time (5M nodes) | 45 min | 5.2 hr |
| Real-time inference p99 | 12ms | 74ms |
| Model size | 180 MB | 2.4 GB |
| GPU training time | N/A | 11 hr (4xA100) |

**Interview discussion points:**

**Why does GraphSAGE outperform a node-only model for fraud ring detection despite individual nodes appearing legitimate?** Fraud ring members are deliberately crafted to have individually normal features: real profile photos, connections to legitimate users, and normal posting frequency. The ring's signature is structural: members endorse each other in circular patterns, share IP address clusters, and have synchronised account creation timestamps. GraphSAGE aggregates 2-hop neighbourhood information, allowing the model to learn that a node surrounded by mutually-endorsing peers with no external connections is suspicious even if its own features are clean, capturing relational fraud signals invisible to node-only models.

**What is the difference between inductive and transductive graph learning and why does LinkedIn need inductive?** Transductive methods (spectral GCN) learn embeddings for a fixed set of nodes in a single graph; they cannot embed new nodes without retraining the entire model. Inductive methods (GraphSAGE) learn a function that aggregates neighbourhood features, allowing new nodes to be embedded at inference time by aggregating their neighbours' features. At LinkedIn scale, 500K new accounts are created daily; retraining a transductive model daily would take 40 hours per cycle. GraphSAGE embeds new nodes in 74ms using the fixed trained aggregation function.

**How does mini-batch training with NeighborLoader scale to 950M nodes when the full graph does not fit in GPU memory?** NeighborLoader samples a fixed-size neighbourhood subgraph for each seed node in the batch, loading only those nodes and edges into GPU memory. For a batch of 1024 seed nodes with fanout [25, 10, 5], the maximum subgraph contains 1024 * 25 * 10 * 5 = 1.28M nodes, requiring approximately 650 MB of GPU memory versus 480 GB for the full graph. The tradeoff is that sampled neighbourhoods introduce variance in gradient estimates, mitigated by using 25 neighbours at the first hop and decreasing fanout at deeper hops where exponential growth is most severe.

**Why is temporal graph construction critical for avoiding label leakage in fraud detection?** Fraud labels are assigned retroactively by the abuse team, often weeks after account creation. If edges formed after the flagging date are included in training, the model learns to predict based on "was this node connected to known fraud after it was already flagged?" - a trivially easy pattern that does not generalise to real-time scoring of unflagged accounts. Using only edges existing before the earliest label assignment date in the training set ensures the model learns structural signals that precede detection, achieving 0.83 recall on future unseen fraud rings versus 0.94 with leakage (12% inflated).

**How would you serve real-time GraphSAGE scores at 80ms p99 for a single account?** The critical path is: (1) retrieve the account's 2-hop neighbourhood from a graph database (Neptune or TigerGraph), targeting < 30ms for a 25-node sample via an online index; (2) load pre-computed node embeddings for neighbours from Redis (2ms); (3) run one GraphSAGE aggregation layer using the cached neighbour embeddings plus the target node's raw features (20ms on CPU); (4) return the fraud score. Pre-computing and caching 1-hop neighbour embeddings nightly for all active nodes reduces the real-time computation to a single aggregation layer rather than 3 layers, achieving p99 of 68ms versus 210ms for full 3-hop inference.

**What graph topology features distinguish a fraud ring from a legitimate community cluster?** Fraud rings exhibit: (1) high internal density (>0.8 edges / possible edges within the ring) versus legitimate communities (0.1-0.3 density); (2) low external connectivity (few edges leaving the ring per member) versus legitimate clusters with 15-40 external edges per member; (3) synchronised formation age (90% of ring edges formed within a 72-hour window) versus organic communities forming over months; (4) reciprocal endorsement loops (A endorses B, B endorses A, C endorses A and B) absent in legitimate skill validation. These features feed the ring-level GBM classifier achieving 0.91 precision at the ring level, above the 0.89 node-level precision.
