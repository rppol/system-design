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

**Problem: Real-time fraud detection in a payment network**

Scale: 50M users, 200M transactions/day, graph has ~250M edges. Goal: classify transactions as fraudulent with >85% precision at >60% recall, inference latency <50ms.

**Architecture:**

```
Offline: Nightly Graph Embedding Refresh
=========================================

  Transaction Graph (Neo4j)
         |
  NeighborSampler (hop-1: 15, hop-2: 10)
         |
  3-layer GraphSAGE (128 -> 64 -> 32)
  Node features: [amount, merchant_category, hour, device_fingerprint,
                  velocity_7d, country_code] (64 dims total)
         |
  Node embeddings stored in Redis (TTL: 24h)
         |
  ANN index (FAISS IVF) for similar-node retrieval


Online: Real-time Inference (<50ms)
=====================================

  Incoming transaction t
         |
  Fetch 15 hop-1 neighbors from Neo4j (indexed, <5ms)
         |
  Fetch their embeddings from Redis (<3ms)
         |
  Single GraphSAGE forward pass (CPU, mini-graph of 16 nodes)
         |
  MLP classifier (embedding + rule features) -> fraud score
         |
  Score > 0.7: block; 0.4-0.7: step-up auth; <0.4: allow
```

**Key Design Decisions:**

1. Offline embedding precomputation: re-running full GraphSAGE online is too slow. Nightly refresh covers 99% of returning users. New users fall back to MLP on raw features only.

2. GraphSAGE (inductive) over GCN (transductive): new accounts created mid-day must get embeddings. GCN requires retraining to incorporate new nodes.

3. Sum aggregation for hop-2, mean for hop-1: hop-2 neighborhood count (how many 2nd-degree fraud contacts) is informative. Hop-1 mean embedding captures average neighbor profile.

4. Temporal edge sampling: only sample edges from the past 30 days — older connections are less predictive of current behavior.

**Results:**

| Metric | Before (XGBoost) | After (GraphSAGE) |
|---|---|---|
| Precision@60% recall | 78% | 87% |
| AUC-ROC | 0.91 | 0.96 |
| P99 inference latency | 12ms | 41ms |
| False positive rate | 2.1% | 1.2% |

The 9% precision gain translated to $4.2M/year reduction in fraud losses. The 29ms latency increase was acceptable given the improvement in precision.
