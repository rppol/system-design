# Offline Reinforcement Learning

> Papers, library features and benchmark numbers on this page were checked against arXiv, the IQL paper's Table 1 and the Pearl, D4RL and Minari repositories on 2026-11-25.

---

## 1. Concept Overview

Offline RL (also called batch RL) learns a policy entirely from a fixed, previously-collected dataset of logged interactions — no environment, no simulator, no further data collection during training. The agent never gets to try an action and see what happens; it only gets to reason about what *would* have happened, using transitions someone else's policy already generated.

This is the form RL actually takes in most industry deployments. [`reinforcement_learning.md`](reinforcement_learning.md) covers the online agent-environment loop — an agent that explores, observes a reward, and updates — which is the right model for a game, a simulator, or a robot in a lab. It is the wrong model for pricing, recommendation, or marketplace allocation, because "explore" there means showing a real customer a bad price or a real driver a bad dispatch. Offline RL exists because the online loop's core assumption — cheap, safe exploration against the true environment — is false in exactly the settings where RL is most tempting to apply: money, users, and irreversible actions are all on the line. The trade a team makes is: give up live exploration, keep everything else — sequential decisions, delayed reward, long-horizon credit assignment — and solve it from history instead.

---

## 2. Intuition

One-line analogy: it is the difference between learning to drive by sitting behind the wheel versus learning to drive by watching ten thousand hours of other people's dashcam footage. You never get to try the turn you're unsure about — you only get to reason, from footage of turns other drivers actually took, about how good your own turn would probably have been.

Mental model: every value function you train offline is being asked a question the data cannot answer — "what if I'd done something other than what's in the log?" — and it will answer that question whether or not it has any right to. A neural network queried at an input far from its training distribution does not raise an error; it returns a number, confidently, and that number is frequently wrong in the optimistic direction. Nothing in supervised learning punishes this, because supervised learning is never asked to *act* on its own extrapolation. RL is, every single training step, via `max_a' Q(s', a')`.

Why it matters: this single failure mode — extrapolation error — is the entire subject of offline RL. Every algorithm below (BCQ, TD3+BC, CQL, IQL, Decision Transformer) is a different answer to the same one question: how do you get the benefits of value-based reasoning (stitching together good pieces of many mediocre trajectories, per §12) without ever trusting the value function where the data can't check its work?

Key insight: in the online loop, a bad estimate corrects itself — the agent tries the overvalued action, the environment reports the true reward, and the error is gone in one step. Offline, there is no environment to correct it. A confidently wrong Q-value can survive the entire training run unchallenged, and the policy that chases it ships to production still wrong.

---

## 3. Core Principles

1. **Distributional shift is the root cause.** The learned policy `pi(a|s)` diverges from the behavior policy `pi_b(a|s)` that generated the data as training progresses — greedy policy improvement pulls the policy toward whatever action currently has the highest estimated Q-value, and nothing forces that action to be one the data actually demonstrated.
2. **Extrapolation error is what distributional shift produces in the Q-function.** `Q(s, a)` for an out-of-distribution `(s, a)` pair is a function-approximator guess with no data to ground it. Bootstrapping (`r + gamma * max_a' Q(s', a')`) then compounds that guess into every earlier state's target, and the `max` operator systematically prefers whichever action's guess happens to be an overestimate — an unconstrained TD update has no mechanism to distinguish "genuinely great action" from "action the network hallucinated a high value for."
3. **There is no environment to correct the error.** Online, a bad estimate is falsified the next time the agent tries that action. Offline, the dataset is frozen — an inflated Q-value for an unseen action is never contradicted, because it is never tested.
4. **The whole field is a spectrum of "how hard do you constrain the policy to the data."** Pure behavior cloning constrains completely (and cannot improve on the data). Unconstrained off-policy Q-learning constrains not at all (and diverges, per above). Every practical algorithm sits between these: stay close enough to the data that extrapolation error can't run away, while still improving on the average behavior in the logs.
5. **Offline RL is not the same problem as off-policy evaluation, but they share the same disease.** Training a policy from logs and *scoring* a policy from logs (§6) both fail for the same underlying reason: neither can check its answer against a real environment, so both need a way to know when the data does and does not support a conclusion.
6. **The logging requirement decides whether any of this is even possible.** You must record the *propensity* — the probability the behavior policy actually assigned to the action it took, `P_b(a|s)` — at decision time. Without it, you cannot compute an importance-sampling correction, you cannot build an unbiased off-policy estimate, and a deterministic production policy with zero randomization produces logs from which nothing can be learned or safely evaluated at all. This is the most common way real offline RL projects fail, and it fails at the logging layer, months before anyone runs CQL.
7. **Coverage is measured, not assumed.** An action is "supported" at a state only if `P_b(a|s) > 0` and the log holds enough rows of it to fit a value. The quantity that bounds every guarantee in this field is a ratio of the learned policy's state-action distribution to the logged one (the *concentrability coefficient*): when the learned policy puts mass where the log has almost none, that ratio blows up, and so does any error bound built on it. In practice you audit it directly, as a count of logged rows per `(state bucket, action)` cell.
8. **Pessimism is the shared design rule.** When an estimate cannot be checked, act as if it is lower than it looks. CQL builds pessimism into the Q-function, policy-constraint methods build it into the action set, and IQL builds it into what the target is allowed to query. Online RL uses the opposite rule (optimism in the face of uncertainty) because exploration will test the optimistic guess; offline, nothing will.

### Worked example: why `max` over noisy guesses is biased upward

Take one state with three actions whose true values are all 0, and suppose the Q-network's error at each is independent Gaussian noise with standard deviation `sigma = 1`. The TD target uses `max_a Q(s, a)`. The expected maximum of three independent standard normals is about `0.85`, so the target is inflated by `0.85 sigma` even though every true value is 0. Bootstrapping then copies that inflation one step back each iteration, discounted by `gamma = 0.95`, so a state `k` steps upstream inherits roughly `0.95^k x 0.85`, plus its own new maximization bias. Online, the agent would try the inflated action and the environment would cancel the error. Offline, the inflation stays.

```python
import numpy as np


def max_bias(n_actions: int, sigma: float = 1.0, n_trials: int = 200_000, seed: int = 0) -> float:
    """Expected value of max_a Q(s, a) when every true Q is 0 and errors are N(0, sigma^2)."""
    rng = np.random.default_rng(seed)
    noisy_q = rng.normal(0.0, sigma, size=(n_trials, n_actions))
    return float(noisy_q.max(axis=1).mean())


if __name__ == "__main__":
    for k in (1, 3, 10, 100):
        print(k, round(max_bias(k), 3))
    # exact expected maxima: 0.0, 0.846, 1.539, 2.508; the simulation lands within 0.01
```

The bias grows with the number of actions: 0.85 sigma for 3 actions, 1.54 sigma for 10, 2.51 sigma for 100. A pricing grid with 100 price points therefore starts with a built-in overestimate of about 2.5 standard deviations of the Q-network's error at every under-sampled state, which is why continuous or large discrete action spaces are where unconstrained offline Q-learning fails first.

---

## 4. Types / Architectures / Strategies

### 4.1 The progression, from "trust the data completely" to "trust the data selectively"

```
Behavior      Policy-          Value-              In-Sample    Sequence
Cloning  ──>  Constraint  ──>  Regularization ──>  Methods  ──> Modeling
(BC)          (BCQ, TD3+BC)    (CQL)               (IQL)        (Decision Transformer)

no reward     stay near the    push Q down on      never query  predict actions from
used; copy    data's own       unseen actions,     an unseen    return + history;
the data;     actions, via a   so the learned Q    action at    a supervised
cannot beat   generative       is a provable       all — the    objective with no
the logging   model or a       LOWER bound on      value fn is  TD target at all
policy        BC penalty       the true value      built only
                                                   from actions
                                                   in the data
```

### 4.2 Method families

| Family | Representative methods | Core mechanism | What it costs |
|--------|------------------------|-----------------|----------------|
| Behavior cloning | BC | Supervised regression of `pi(a\|s)` onto the logged actions | No reward signal — cannot exceed the behavior policy's quality, even when the data contains better sub-trajectories to stitch together |
| Policy-constraint | BCQ (Fujimoto, Meger & Precup, [arXiv:1812.02900](https://arxiv.org/abs/1812.02900), ICML 2019), TD3+BC (Fujimoto & Gu, [arXiv:2106.06860](https://arxiv.org/abs/2106.06860), NeurIPS 2021) | Restrict the action set the policy can select (BCQ: only actions a generative model judges plausible) or add a behavior-cloning term to the policy loss (TD3+BC) | Constraint strength is a hyperparameter; too loose reopens extrapolation error, too tight collapses to BC |
| Value-regularization | CQL (Kumar, Zhou, Tucker & Levine, [arXiv:2006.04779](https://arxiv.org/abs/2006.04779), NeurIPS 2020) | Add a regularizer that pushes `Q(s,a)` down for actions not in the data and up for the logged action, so the learned Q is a provable **lower bound** on the true value | Conservatism trades away some achievable value in exchange for the lower-bound safety guarantee — visible directly in §6's run |
| In-sample learning | IQL (Kostrikov, Nair & Levine, [arXiv:2110.06169](https://arxiv.org/abs/2110.06169), ICLR 2022) | Fit `V(s)` as an **expectile regression** of `Q(s, a_logged)` — never evaluates `Q` at any action outside the dataset, so there is no `max_a'` to hallucinate over | Expectile `tau` trades bias (low tau, closer to the mean/behavior policy) against how aggressively it estimates the best in-sample action (high tau) |
| Sequence modeling | Decision Transformer (Chen et al., [arXiv:2106.01345](https://arxiv.org/abs/2106.01345), NeurIPS 2021) | Train a causal transformer to autoregressively predict the next action conditioned on past states, actions, and a **target return-to-go** — no value function, no Bellman backup, ordinary supervised sequence loss | Cannot reliably *stitch*: it reproduces trajectories shaped like the ones it saw, and struggles to compose the best segments of several mediocre trajectories into one better than any of them (§12) |

### 4.3 Off-policy evaluation sits alongside training, not after it

OPE (§6) is not a training method — it is how you score *any* candidate policy (one you trained offline, one a human proposed, a modified version of the current production policy) against the same logs, before it ever sees a real decision. Every serious offline RL pipeline runs training and OPE together: train a candidate, score it offline, and only pass the ones that clear an OPE bar to a real A/B test.

---

## 5. Architecture Diagrams

### Offline Pipeline vs. the Online Loop

```mermaid
%%{init: {'flowchart': {'curve': 'basis', 'nodeSpacing': 45, 'rankSpacing': 55}}}%%
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph onlineLoop["Online RL — needs a live environment"]
        direction LR
        pol(["Policy pi"]) -->|"action a"| envr["Real environment<br/>real users, real dollars"]
        envr -->|"reward r, next state"| pol
    end

    subgraph offlinePipe["Offline RL — the industry default"]
        direction LR
        logs(["Logged interactions<br/>s, a, r, propensity P_b(a|s)"]) --> dataset["Static dataset D<br/>frozen, no new collection"]
        dataset --> constrain["Constrained training<br/>CQL / IQL / TD3+BC"]
        constrain --> gate{"OPE gate<br/>DR estimate clears bar?"}
        gate -->|"pass"| canary["Canary + A/B test"]
        gate -->|"fail, retrain"| constrain
    end

    class pol train
    class envr frozen
    class logs io
    class dataset base
    class constrain train
    class gate req
    class canary lossN
```

The online loop touches the real environment on every single step — that is exactly the exposure offline RL is built to avoid. The offline pipeline never touches production until the OPE gate passes: everything left of the gate runs against a frozen dataset, and the gate is the one place a bad candidate is stopped before it can cost a real customer anything.

### Extrapolation Error — the Q-Spike Outside the Data Support

```mermaid
xychart-beta
    title "Learned Q vs true value across the action range — behavior policy concentrated near 0"
    x-axis "Action (e.g. price offset)" [-1.0, -0.6, -0.2, 0.2, 0.6, 1.0]
    y-axis "Value" -2 --> 12
    line [1.0, 3.5, 5.8, 6.2, 3.0, 0.5]
    line [9.5, 4.0, 5.9, 6.3, 3.9, 11.0]
```

The flat line is the true value of each action; it peaks near 0.2 and tapers off, exactly what a sane reward surface looks like. The spiking line is an *unconstrained* Q-network trained on logs from a behavior policy that rarely visited the extremes (std ~0.3 around 0) — at the two actions the data barely covers, `-1.0` and `1.0`, the network's own generalization error becomes a confident, wrong peak. Nothing in ordinary TD learning tells the network these two numbers are less trustworthy than the well-supported middle three; greedy policy improvement reads the spike as "this is clearly the best action" and ships a policy that has never once been validated there. CQL's regularizer and IQL's in-sample constraint (§4.2, §6) exist specifically to flatten spikes like these back toward the data-supported line.

---

## 6. How It Works — Detailed Mechanics

### CQL and IQL on a small logged dataset

The setup below is a 5-state chain (state 4 is the goal) logged by a **weak, already-deployed policy** that mostly plays "stay" (75%), sometimes "forward" (20%), and almost never "back" (5%) — realistic production logging, where one action per state is chronically under-sampled. Two Q-functions are trained on the identical logs: plain off-policy Q-learning (`alpha_cql = 0`, i.e. no conservatism) and CQL (`alpha_cql = 1.0`). A separate IQL run fits an expectile value function that never queries an unlogged action at all.

```python
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim

N_STATES, N_ACTIONS, GAMMA = 5, 3, 0.95  # actions: 0=forward, 1=stay, 2=back
np.random.seed(0)
torch.manual_seed(0)

def env_step(s: int, a: int) -> tuple[int, float, bool]:
    s2 = min(s + 1, N_STATES - 1) if a == 0 else s if a == 1 else max(s - 1, 0)
    reached_goal = s2 == N_STATES - 1 and s != N_STATES - 1
    return s2, (10.0 if reached_goal else -0.1), s2 == N_STATES - 1

# Logged dataset from a weak, deployed behavior policy -- "back" has 5% coverage,
# which is exactly the condition that produces extrapolation error below.
BEHAVIOR_PROBS = np.array([0.20, 0.75, 0.05])
N_LOGGED = 6000
logged_s, logged_a, logged_r, logged_s2, logged_done, logged_prop = [], [], [], [], [], []
for _ in range(N_LOGGED):
    s = np.random.randint(0, N_STATES - 1)
    a = np.random.choice(N_ACTIONS, p=BEHAVIOR_PROBS)
    s2, r, done = env_step(s, a)
    logged_s.append(s); logged_a.append(a); logged_r.append(r)
    logged_s2.append(s2); logged_done.append(done); logged_prop.append(BEHAVIOR_PROBS[a])
logged_s, logged_a = torch.tensor(logged_s), torch.tensor(logged_a)
logged_r, logged_s2 = torch.tensor(logged_r, dtype=torch.float32), torch.tensor(logged_s2)
logged_done = torch.tensor(logged_done, dtype=torch.float32)
logged_prop = torch.tensor(logged_prop, dtype=torch.float32)

class QNet(nn.Module):
    def __init__(self, n_states: int, n_actions: int, hidden: int = 32) -> None:
        super().__init__()
        self.embed = nn.Embedding(n_states, hidden)
        self.net = nn.Sequential(nn.Linear(hidden, hidden), nn.ReLU(), nn.Linear(hidden, n_actions))
    def forward(self, s: torch.Tensor) -> torch.Tensor:
        return self.net(self.embed(s))

def train_cql(alpha_cql: float, n_epochs: int = 400) -> QNet:
    q = QNet(N_STATES, N_ACTIONS)
    q_target = QNet(N_STATES, N_ACTIONS); q_target.load_state_dict(q.state_dict())
    opt = optim.Adam(q.parameters(), lr=1e-2)
    for epoch in range(n_epochs):
        with torch.no_grad():
            td_target = logged_r + GAMMA * q_target(logged_s2).max(dim=1).values * (1 - logged_done)
        q_all = q(logged_s)
        q_taken = q_all.gather(1, logged_a.unsqueeze(1)).squeeze(1)
        td_loss = nn.functional.mse_loss(q_taken, td_target)
        # CQL(H) penalty: minimize logsumexp_a Q(s,a) [push ALL actions down],
        # maximize Q(s, a_logged) [pull the observed action back up]. Net effect:
        # Q is suppressed everywhere except where the data actually supports it.
        cql_penalty = (torch.logsumexp(q_all, dim=1) - q_taken).mean()
        loss = td_loss + alpha_cql * cql_penalty
        opt.zero_grad(); loss.backward(); opt.step()
        if epoch % 100 == 0:
            q_target.load_state_dict(q.state_dict())
    return q

q_unconstrained = train_cql(alpha_cql=0.0)
q_cql = train_cql(alpha_cql=1.0)

counts = torch.bincount(logged_a, minlength=N_ACTIONS).tolist()
print(f"Logged transitions: {N_LOGGED}")
print(f"Action counts in logs -- forward: {counts[0]}, stay: {counts[1]}, back: {counts[2]}")
with torch.no_grad():
    s0 = torch.tensor([0])
    print("plain Q-learning (alpha=0):", q_unconstrained(s0).numpy().round(2))
    print("CQL (alpha=1.0):           ", q_cql(s0).numpy().round(2))
```

**Representative output** (one seeded CPU run; the exact digits move with the PyTorch and NumPy versions, the shape of the result does not):

```
Logged transitions: 6000
Action counts in logs -- forward: 1223, stay: 4498, back: 279

Q(s=0, .) after training [forward, stay, back] -- 'back' has 5% log coverage:
  plain Q-learning (alpha=0): [ 8.26 -0.10 -0.08]
  CQL (alpha=1.0):            [ 2.01  0.76  0.03]
```

**Check the 8.26 by hand.** The best path from state 0 is four "forward" steps: three steps
at `-0.1` and the goal step at `+10`. Its discounted value is
`-0.1 - 0.95 x 0.1 - 0.95^2 x 0.1 + 0.95^3 x 10 = -0.1 - 0.095 - 0.090 + 8.574 = 8.29`, so the
network found the true optimal `Q(0, forward)` to within 0.03. The `-0.10` for "stay" is not a
true value. The target network is synced every 100 epochs, so 400 epochs give four Bellman
backups, exactly enough to carry the goal reward four steps back to `Q(0, forward)`. The true
`Q(0, stay) = -0.1 + 0.95 x 8.29 = 7.78` needs one more backup, because its target is the
converged `Q(0, forward)`. So the run is also a warning: a Q-table read before value iteration
has converged looks like a confident verdict ("stay is worthless") and is only an artifact of
the training schedule.

The alpha=0 run is ordinary off-policy Q-learning on the logs and recovers a large, roughly correct value for "forward" — but this is exactly the setting where extrapolation error is a live risk (compare §5's spike: nothing here would have flagged a spurious estimate as spurious). CQL compresses every value, including the well-supported one, toward a smaller number — visible proof of the paper's central claim: the CQL Q-function is a deliberately **conservative lower bound**, not a best-effort point estimate, and that conservatism is the price paid for the safety guarantee.

Now IQL, trained on the identical logs, which never evaluates `Q` at any action the behavior policy didn't take:

```python
class VNet(nn.Module):
    def __init__(self, n_states: int, hidden: int = 32) -> None:
        super().__init__()
        self.embed = nn.Embedding(n_states, hidden)
        self.net = nn.Sequential(nn.Linear(hidden, hidden), nn.ReLU(), nn.Linear(hidden, 1))
    def forward(self, s: torch.Tensor) -> torch.Tensor:
        return self.net(self.embed(s)).squeeze(-1)

def expectile_loss(diff: torch.Tensor, tau: float) -> torch.Tensor:
    weight = torch.where(diff > 0, tau, 1 - tau)
    return (weight * diff.pow(2)).mean()

def train_iql(tau: float = 0.8, n_epochs: int = 400) -> tuple[QNet, VNet]:
    q = QNet(N_STATES, N_ACTIONS)
    q_target = QNet(N_STATES, N_ACTIONS); q_target.load_state_dict(q.state_dict())
    v = VNet(N_STATES)
    opt_q, opt_v = optim.Adam(q.parameters(), lr=1e-2), optim.Adam(v.parameters(), lr=1e-2)
    for epoch in range(n_epochs):
        # V-step: expectile regression toward Q(s, a_logged) ONLY -- no max,
        # no argmax, no query of any action outside the batch.
        with torch.no_grad():
            q_sa = q_target(logged_s).gather(1, logged_a.unsqueeze(1)).squeeze(1)
        loss_v = expectile_loss(q_sa - v(logged_s), tau)
        opt_v.zero_grad(); loss_v.backward(); opt_v.step()
        # Q-step: bootstrap off V(s') instead of max_a' Q(s',a') -- V(s') IS
        # the in-sample estimate of the best reachable value, so this touches
        # no out-of-distribution action either.
        with torch.no_grad():
            td_target = logged_r + GAMMA * v(logged_s2) * (1 - logged_done)
        q_taken = q(logged_s).gather(1, logged_a.unsqueeze(1)).squeeze(1)
        loss_q = nn.functional.mse_loss(q_taken, td_target)
        opt_q.zero_grad(); loss_q.backward(); opt_q.step()
        if epoch % 100 == 0:
            q_target.load_state_dict(q.state_dict())
    return q, v

q_iql, v_iql = train_iql(tau=0.8)
with torch.no_grad():
    print("IQL V(s) estimates, s=0..4:", v_iql(torch.arange(N_STATES)).numpy().round(2))
    print("IQL Q(s=0, .) [forward, stay, back]:", q_iql(torch.tensor([0])).numpy().round(2))
```

**Representative output:**

```
IQL V(s) estimates, s=0..4: [-0.01  1.16  4.56  8.61  1.87]
IQL Q(s=0, .) [forward, stay, back]: [ 0.98 -0.08 -0.08]
```

`V(s)` rises monotonically toward the goal (state 3, one step from the terminal state 4, correctly the highest) and drops at state 4 itself (post-terminal bookkeeping, not a real decision point) — a sane value function built without a single query outside the logged actions.

### Off-policy evaluation: IPS vs. Doubly Robust, and the horizon collapse

```python
def evaluate_candidate_policy() -> None:
    # candidate policy pi_e: deterministic "always forward" (a=0)
    pi_e_prob = (logged_a == 0).float()
    weights = pi_e_prob / logged_prop  # importance ratio pi_e(a|s) / pi_b(a|s)

    ips_estimate = (weights * logged_r).mean().item()

    with torch.no_grad():
        q_all = q_cql(logged_s)
        v_hat_candidate = q_all[:, 0]                               # direct-method term
        q_hat_taken = q_all.gather(1, logged_a.unsqueeze(1)).squeeze(1)
    # Doubly robust = direct-method baseline + IPS-weighted residual correction.
    # Unbiased if EITHER q_cql or the propensities are correct -- "doubly" robust.
    dr_terms = v_hat_candidate + weights * (logged_r - q_hat_taken)
    dr_estimate = dr_terms.mean().item()
    support = (logged_a == 0).float().mean().item()
    print(f"Candidate policy = 'always forward' (a=0); logged support for a=0: {support:.1%} of rows")
    print(f"IPS estimate:  {ips_estimate:.3f}  (variance: {(weights * logged_r).var().item():.3f})")
    print(f"DR estimate:   {dr_estimate:.3f}  (variance: {dr_terms.var().item():.3f})")

evaluate_candidate_policy()
```

**Representative output:**

```
Candidate policy = 'always forward' (a=0); logged support for a=0: 20.4% of rows
IPS estimate:  2.734  (variance: 132.998)
DR estimate:   2.486  (variance: 105.408)
```

**Know the true answer before trusting either number.** This is a one-step estimate of the
expected *immediate* reward under "always forward", over logged start states drawn uniformly
from 0-3. Only state 3 pays `+10` for "forward"; the other three pay `-0.1`. The true value is
therefore `0.25 x 10 + 0.75 x (-0.1) = 2.425`. DR lands 0.06 away from it, IPS 0.31 away, which
matches the variance ordering. The same check also predicts the IPS variance: each "forward"
row carries weight `1 / 0.2 = 5`, so a state-3 row contributes `50` and the others `-0.5`, and
`0.2 x (0.25 x 2500 + 0.75 x 0.25) - 2.425^2 = 125.0 - 5.9 = 119`, close to the sampled 133.
Build a toy check like this into any OPE pipeline: a synthetic policy whose value you can
compute exactly is the only way to see estimator bias, because real logs never tell you the
truth.

Both estimators only use the 20.4% of logged rows where the behavior policy happened to also play "forward" — every other row contributes zero to IPS and only its direct-method term to DR. That's already a 21% variance reduction from the same logs, from a single Q-function used purely as a control variate; the two estimators agree to within 10% of each other, which is the everyday sanity check for "does the direct-method component look consistent with the data."

Now the horizon effect referenced in §3 and §5 — per-step importance ratios (mean 1, moderate spread) multiplied across a trajectory of length `H`:

```python
def ess_vs_horizon(horizons: list[int], n_traj: int = 20_000, ratio_std: float = 0.5) -> None:
    rng = np.random.default_rng(1)
    for h in horizons:
        sigma = np.log(1 + ratio_std ** 2) ** 0.5
        mu = -0.5 * sigma ** 2
        W = rng.lognormal(mean=mu, sigma=sigma, size=(n_traj, h)).prod(axis=1)
        ess = (W.sum() ** 2) / (W ** 2).sum()
        print(f"H={h:>3}  mean(W)={W.mean():.3f}  var(W)={W.var():.3f}  ESS={ess:.1f}  ESS/N={ess/n_traj:.4f}")

ess_vs_horizon([1, 5, 10, 20, 50, 100])
```

**Output** (of 20,000 simulated logged trajectories, `default_rng(1)`):

```
H=  1  mean(W)=0.994  var(W)=0.247   ESS=16003.2  ESS/N=0.8002
H=  5  mean(W)=0.997  var(W)=2.044   ESS= 6542.4  ESS/N=0.3271
H= 10  mean(W)=0.967  var(W)=6.587   ESS= 2486.7  ESS/N=0.1243
H= 20  mean(W)=0.985  var(W)=30.280  ESS=  621.1  ESS/N=0.0311
H= 50  mean(W)=0.842  var(W)=389.234 ESS=   36.4  ESS/N=0.0018
H=100  mean(W)=0.282  var(W)=148.697 ESS=   10.7  ESS/N=0.0005
```

At `H=1`, 20,000 logged trajectories are worth 16,003 real samples — a mild, tolerable loss. By `H=100`, the same 20,000 trajectories are worth **10.7** — the entire batch's estimate is effectively decided by a handful of trajectories whose cumulative importance weight happened not to collapse toward zero.

**The closed form behind the table.** Each per-step ratio has mean 1 and variance `0.5^2 = 0.25`, so its second moment is `1.25`. The product of `H` independent ratios has second moment `1.25^H`, and the effective sample size fraction is about `E[W]^2 / E[W^2] = 1.25^-H`. That gives 0.80 at `H=1`, 0.33 at `H=5`, 0.11 at `H=10` and 0.012 at `H=20`, the same collapse the simulation shows. From `H=10` on, the sampled ESS sits *above* the formula (0.031 against 0.012 at `H=20`, 0.0018 against 1.4e-5 at `H=50`), and the sampled `mean(W)` falls below 1: 20,000 draws no longer reach the rare huge weights that carry the true mean, so the sample understates the variance. That is the worst property of long-horizon IS: the estimate looks calmer than it is exactly when it is least reliable. To keep ESS above 10% of the batch, solve `1.25^-H >= 0.1`, which gives `H <= 10`.

This is the concrete reason plain importance sampling is treated as unusable past a horizon of roughly 10-20 steps in practice, and why doubly robust and model-based (fitted-Q evaluation) estimators dominate long-horizon OPE — they don't eliminate the horizon problem, but the direct-method term keeps contributing even where the IS weight has collapsed to noise.

---

## 7. Real-World Examples

**Meta's Pearl (production-ready RL library).** Pearl ([arXiv:2312.03814](https://arxiv.org/abs/2312.03814), Meta's Applied Reinforcement Learning team) ships offline RL as a first-class feature beside its online actor-critic and bandit modules (already listed in [`reinforcement_learning.md` §11](reinforcement_learning.md)): `ImplicitQLearning` for IQL, a `TD3BC` policy learner, and CQL as a flag on its deep Q-learner (`DeepQLearning(is_conservative=True, conservative_alpha=2.0)`). Its README lists recommender systems, auction bidding and creative selection as the real-world applications it is being adopted for. A library that puts offline RL next to bandits, rather than in a research appendix, reflects how industry mostly consumes RL: from logs, rarely from a live loop.

**Benchmarks that look like production logs.** D4RL ([arXiv:2004.07219](https://arxiv.org/abs/2004.07219)) built datasets from mixtures of policies of varying quality, which is what production logs look like. Its AntMaze tasks are the clearest real-world proxy for "the log has no single good trajectory, only good pieces": an ant robot must reach a goal by recombining segments of undirected, mediocre runs. The IQL paper's Table 1 (Kostrikov et al., [arXiv:2110.06169](https://arxiv.org/abs/2110.06169)) shows how sharply method choice matters there. On `antmaze-medium-play`, behavior cloning and Decision Transformer both score 0.0, TD3+BC 10.6, CQL 61.2 and IQL 71.2; on `antmaze-large-diverse`, CQL scores 14.9 and IQL 47.5. On the nine locomotion datasets, where logs contain whole good trajectories, DT, TD3+BC, CQL and IQL total between 672.6 and 698.5, within 4% of each other. The lesson transfers to pricing and dispatch: if no logged policy was good end to end, you need a method that stitches.

**Slate recommendation — logging, not just training.** [`reinforcement_learning.md` §7](reinforcement_learning.md) already covers YouTube's production REINFORCE recommender (Chen et al., WSDM 2019) and its off-policy correction; that system is continually retraining against fresh logs rather than training once from a frozen dataset, so it sits adjacent to — not squarely inside — the offline RL setting described here. The distinction matters in an interview: "off-policy" (learns from data collected by a different policy) and "offline" (learns from a dataset with zero further collection) are not synonyms, and a system can be the former without being the latter.

### Applied mapping — what state, action, and reward actually are

| Domain | State | Action | Reward | Delayed-reward problem |
|--------|-------|--------|--------|--------------------------|
| Slate recommendation | User history, session context, candidate slate features | Which item(s) to place in the slate | Click / watch time / conversion | Today's click is easy to attribute; the effect of today's slate on *next week's* retention is not — see [`online_learning_and_bandits.md`](../recommender_systems/online_learning_and_bandits.md) for the single-step contextual-bandit simplification |
| Dynamic pricing | SKU, competitor prices, inventory level, demand signal | Price (continuous or a discrete grid) | Revenue or margin from the resulting sale (or non-sale) | A price today shapes tomorrow's demand curve (reference-price effects); see [`design_dynamic_pricing.md`](../case_studies/design_dynamic_pricing.md) §4, which already logs propensities for exactly this reason |
| Marketplace / dispatch allocation | Driver location, idle time, nearby demand, surge state | Which rider to match, or where to pre-position an idle driver | Trip revenue, or platform-level utilization over the shift | A dispatch decision now trades off an immediate match against ending the trip in a low-demand zone later — the pre-positioning problem in [`design_marketplace_matching.md`](../case_studies/design_marketplace_matching.md) §11 |

All three share the same credit-assignment shape: the reward that matters (retention, lifetime value, driver utilization) resolves on a much longer horizon than the reward that's easy to log (click, this sale, this trip), and §10's second pitfall is exactly what happens when the offline RL horizon is set to the easy one.

---

## 8. Tradeoffs

| Method | Data requirement | Hyperparameter sensitivity | Can stitch sub-optimal trajectories? | Ease of pre-deployment evaluation |
|--------|-------------------|------------------------------|----------------------------------------|--------------------------------------|
| Behavior cloning | Works even on small, narrow datasets; quality ceiling = behavior policy | Low — effectively none | No — by construction, it only reproduces what it saw | Trivial: compare predicted action distribution to held-out logs |
| TD3+BC | Moderate; needs enough coverage for the critic to be meaningful | Low-moderate — one BC-weight coefficient (`alpha` in the paper) | Partial — the critic term can favor a better action than BC alone, within the constraint | Standard OPE (§6); critic doubles as a DM component |
| CQL | Moderate-to-large; the conservatism helps most exactly where data is thin | Moderate — `alpha_cql` trades safety against achievable value (see §6's compression) | Yes — value-based methods can in principle recombine good segments of different trajectories | Standard OPE; the learned Q is itself usable as the DR direct-method term |
| IQL | Moderate; benefits from reasonably diverse logged actions per state | Moderate — expectile `tau` controls how aggressively it estimates the best in-sample action | Yes, and typically the most robust of the value-based family — never touches an OOD action to begin with | Standard OPE |
| Decision Transformer | Large; sequence models want more data than tabular-scale value methods | High — target return-to-go must be chosen at inference time, and generalizes poorly outside the logged return range | **No** — known to struggle at combining pieces of different sub-optimal trajectories into a better one (§12) | Awkward — no explicit value function to plug into DR; typically evaluated by rollout in a simulator or by DM models built on top |
| Contextual bandit (for comparison) | Smallest — one-step, no bootstrapping | Low | N/A — no multi-step credit assignment exists | Easiest of all: single-step IPS/DR, no horizon-collapse problem (§6) |

### Measured cost of choosing wrong (IQL paper, Table 1, D4RL normalized scores)

| Method | Locomotion total (9 datasets) | AntMaze total (6 datasets) | Reported runtime |
|--------|-------------------------------|----------------------------|------------------|
| BC | 466.7 | 100.2 | 10 min |
| Decision Transformer | 672.6 | 112.2 | 960 min |
| TD3+BC | 677.4 | 163.8 | 20 min |
| CQL | 698.5 | 303.6 | 80 min |
| IQL | 692.4 | 378.0 | 20 min |

Read the two columns together. On locomotion the four non-BC methods are within 4% of each other, so the cheapest one to train and tune wins. On AntMaze, which needs stitching, IQL scores 3.4x Decision Transformer and 1.25x CQL, at a quarter of CQL's runtime and a forty-eighth of DT's. Run your own version of this table on a slice of your logs before you commit to a method: the ranking depends on whether your data contains good end-to-end trajectories.

### Off-policy evaluation estimators

| Estimator | Bias | Variance | Needs a model? | Horizon behavior | Use it when |
|-----------|------|----------|----------------|------------------|-------------|
| IPS (trajectory-level) | Unbiased if propensities are right | Highest; grows like `(1 + var(ratio))^H` | No | Unusable past about 10-20 steps (§6) | One-step decisions with good overlap |
| Self-normalized IPS (SNIPS) | Small bias, vanishes as data grows | Lower than IPS; bounded by the reward range | No | Same collapse, a little later | Default replacement for plain IPS |
| Per-decision IS | Unbiased | Lower than trajectory IS; each reward uses only the ratios up to its own step | No | Better than trajectory IS, still exponential | Multi-step with short horizons |
| Direct method / FQE | Biased by model error, invisible offline | Lowest | Yes | Does not collapse | Long horizons, as one input to DR |
| Doubly robust | Unbiased if either the model or the propensities are right | Between DM and IPS (§6: 21% below IPS) | Yes | Degrades more slowly than IS | The default for a ship gate |

---

## 9. When to Use / When NOT to Use

**Use offline RL when:**
- Real environment interaction is expensive, dangerous, or ethically off-limits (pricing a live customer, dispatching a live driver, treating a live patient)
- A moderate-to-large log of past interactions already exists **with logged propensities** — without them, skip straight to §10's first pitfall
- The decision genuinely has multi-step consequences your team can point to (today's price affects tomorrow's demand; today's match affects tonight's utilization) — otherwise a contextual bandit is simpler, lower-variance, and easier to evaluate (§8)
- You can build an OPE gate and a staged rollout, because no offline estimate alone is grounds to ship (§10)

**Do NOT use offline RL when:**
- The production logging policy is deterministic with no randomization — there is no propensity to log even if you wanted to, and neither training nor evaluation has a foundation (§10, Pitfall 1)
- The reward is genuinely single-step — reformulating a one-shot decision as a multi-step MDP adds variance and complexity for no credit-assignment benefit a bandit doesn't already capture
- Exploration in the real environment is actually cheap and safe (a game, a simulator with a good sim-to-real match) — use the online loop in [`reinforcement_learning.md`](reinforcement_learning.md) instead; offline RL's conservatism costs real achievable value that online exploration would recover for free
- Your logged data covers only a narrow slice of the action space with no diversity at all — there is nothing to learn beyond what the current policy already does, and even conservative methods cannot manufacture information the logs never captured

---

## 10. Common Pitfalls

### Pitfall 1: training and evaluating on logs from a deterministic policy with no propensities

```
# BROKEN: production policy always prices at the model's point estimate, zero
# randomization, and the team assumed a uniform propensity when computing OPE.
logged_prop_assumed = np.full(N, 1.0 / N_ACTIONS)          # WRONG: not the real P_b(a|s)
weights = pi_e_prob / logged_prop_assumed                   # importance ratio is fabricated

# What actually happens: for any candidate action the deterministic policy
# never took, the TRUE P_b(a|s) is 0 -- the honest importance ratio is
# pi_e(a|s) / 0, undefined. Using an assumed uniform propensity instead just
# hides the undefined-ness behind a number that looks plausible and is not:
# the OPE estimate is now a measurement of the assumed distribution, not of
# the logging policy, and neither training nor evaluation has any real basis.
```

This is the single most common way real offline RL projects fail, and it fails upstream of any algorithm choice: recall from §6 that even a *moderate* coverage gap (20.4% support for "always forward") already produced an IPS variance of 133 against a DR variance of 105 — both usably finite because the true propensity was nonzero and known. Push that coverage to zero and both estimators stop being finite-variance estimators of anything.

**Fix:** inject deliberate randomization into the production policy before logging — epsilon-exploration, or Thompson-sampling-style logging as in [`online_learning_and_bandits.md`](../recommender_systems/online_learning_and_bandits.md) — and log the *actual* probability assigned to the action taken, not a value assumed after the fact. Where per-request randomization is politically or operationally impossible, use a switch-back or staggered geographic/cohort rollout design instead of trying to retrofit propensities onto a deterministic log. If neither is available, do not run offline RL or OPE on that data; reformulate the problem as an online bandit with mandatory exploration budget going forward.

### Pitfall 2: a reward tied to a delayed business metric the logged horizon cannot cover

```
# BROKEN: dynamic-pricing trajectories are logged per-session (a few minutes),
# but the metric the business actually cares about is 90-day customer LTV.
# gamma=0.99 "should" look about 100 steps ahead (see reinforcement_learning.md
# section 5's discount-horizon table) -- but each logged episode terminates
# at checkout, so there ARE no more steps for gamma to discount over.
reward = immediate_margin_on_this_sale                       # the only signal actually logged
# The value function bootstraps correctly within the session and then simply
# stops -- it has no way to represent "this price change quietly increased
# 90-day churn," because that signal was never in the data to begin with.
```

The agent looks like it is optimizing long-horizon value (gamma is close to 1, the Bellman backups are correct, the code is right) while actually optimizing a myopic proxy, because the *logging boundary*, not gamma, is what determines how far credit assignment can reach. This is silent: nothing in training breaks, the loss curves look normal, and the policy that ships is confidently optimizing the wrong horizon.

**Fix:** either extend the logged trajectory to actually span the metric you care about (session-level episodes for an LTV objective is a category error, not a tuning problem), or explicitly train against a shorter-horizon proxy metric that has been validated to correlate with the true delayed outcome — and validate that correlation with a held-out delayed-outcome model, not by assumption. [`design_dynamic_pricing.md`](../case_studies/design_dynamic_pricing.md) §9 documents a version of this failure mode for the same domain used above.

### Pitfall 3: treating a time-limit cut as a real terminal state

Logs are cut at operational boundaries: a dispatch shift ends at 18:00, a pricing episode is cut at midnight for the daily ETL. If the pipeline writes `done = 1` at that cut, every TD target there becomes `r + 0`, which teaches the Q-function that the state just before the cut is worth almost nothing. The value of the last decision of each shift is then underestimated by the whole discounted future the shift boundary hid.

```python
import torch

GAMMA = 0.99

def td_target_broken(r: torch.Tensor, v_next: torch.Tensor, done: torch.Tensor) -> torch.Tensor:
    # BROKEN: `done` mixes true terminals (trip completed, customer churned)
    # with truncations (shift ended, log file rolled over)
    return r + GAMMA * v_next * (1 - done)

def td_target_fixed(
    r: torch.Tensor, v_next: torch.Tensor, terminated: torch.Tensor, truncated: torch.Tensor
) -> torch.Tensor:
    # FIX: only a real terminal stops bootstrapping. A truncated row still
    # bootstraps from V(s'), because the world did not end at the cut.
    del truncated  # kept in the schema so the two cases are never merged again
    return r + GAMMA * v_next * (1 - terminated)

r = torch.tensor([1.0])
v_next = torch.tensor([50.0])
print(td_target_broken(r, v_next, done=torch.tensor([1.0])))                   # tensor([1.])
print(td_target_fixed(r, v_next, torch.tensor([0.0]), torch.tensor([1.0])))    # tensor([50.5000])
```

With `V(s') = 50`, the broken target is 1.0 and the correct one is 50.5, a 98% underestimate at every shift boundary. Log `terminated` and `truncated` as two fields, the split Gymnasium and Minari use, and audit the share of rows marked as each before training.

---

## 11. Technologies & Tools

| Tool | Use Case | Notes |
|------|----------|-------|
| Pearl (Meta) | Production-ready RL agent library | Offline: `ImplicitQLearning` (IQL), `TD3BC`, and CQL via `DeepQLearning(is_conservative=True)`, alongside online actor-critic and bandits ([arXiv:2312.03814](https://arxiv.org/abs/2312.03814)); `github.com/facebookresearch/Pearl` |
| d3rlpy | Offline deep RL library, scikit-learn-style API | Actively maintained, PyPI (`pip install d3rlpy`); implements BC, BCQ, CQL, IQL, TD3+BC and more; `github.com/takuseno/d3rlpy` |
| CORL (corl-team) | Single-file, research-oriented offline RL implementations | AWAC, BC, CQL, DT, IQL, TD3+BC, SAC-N, ReBRAC and others, each in one readable file; `github.com/corl-team/CORL` (formerly under `tinkoff-ai`) |
| D4RL | Standard offline RL benchmark datasets | Maintained by Farama Foundation ([arXiv:2004.07219](https://arxiv.org/abs/2004.07219)); all D4RL offline datasets have moved to **Minari** (`Farama-Foundation/Minari`), the successor offline-dataset library where new dataset work happens, so new work should load them from Minari |
| Tianshou | PyTorch RL library | Strong offline RL support alongside online algorithms (already listed in [`reinforcement_learning.md` §11](reinforcement_learning.md)) |
| CleanRL | Single-file reference implementations | Primarily online algorithms; useful as a style reference for the CORL single-file philosophy above |

---

## 12. Interview Questions with Answers

**Q: Why can't you just run standard off-policy Q-learning (DQN, DDPG) directly on a fixed logged dataset?**
**Short:** Unconstrained bootstrapping hallucinates high values for unseen actions with no environment to correct them.

The `max_a' Q(s', a')` term in the Bellman target is an unconstrained optimization over the *learned* Q-function, and a neural network queried on an out-of-distribution action returns a confident number with no basis in data — extrapolation error. Online, this self-corrects: the agent tries the overvalued action, the environment reports the true (lower) reward, and the estimate is repaired within one step. Offline, the dataset is frozen, so an inflated estimate for an action the data never demonstrated is never contradicted, and bootstrapping compounds it into every earlier state's target. This is why every offline RL method modifies either the policy (constrain it near the data) or the value function (regularize it toward conservatism) — plain Q-learning has neither.

**Q: What is the one logging requirement that determines whether offline RL or OPE is possible at all?**
**Short:** The behavior policy's action probability (propensity) must be logged at decision time.

Without a logged propensity `P_b(a|s)`, you cannot compute an importance-sampling ratio, and neither training-time corrections nor any off-policy evaluation estimator has a valid foundation. A deterministic production policy with zero exploration is the worst case: it assigns probability 1 to one action and 0 to every other, so the honest importance ratio for any other action is undefined, not merely large. This is the most common way real offline RL projects fail in practice, and it fails at the logging layer — months before anyone chooses an algorithm.

**Q: A team trains CQL on logs from a fully deterministic production policy with no exploration. What actually breaks?**
**Short:** Training may run without error, but OPE cannot validate the result, so nothing prevents shipping a policy nobody can verify.

CQL's TD loss and conservative penalty are computable on any dataset, including one with a single logged action per state, so the training loop will not visibly fail. What breaks is verification: any OPE estimator (§6) computed against those logs has either an undefined importance weight (true propensity is 0) or a fabricated one (assuming uniform propensity, per §10's Pitfall 1), so there is no honest signal for whether the trained policy is better or worse than what's running today. The failure is silent — loss curves look fine, and the team ships blind.

**Q: Why does plain behavior cloning sometimes outperform CQL or IQL on real logged data?**
**Short:** When the data is near-expert and function-approximation error outweighs distributional-shift error, BC's simplicity wins.

Value-based offline RL methods have to fit a Q-function, and every additional moving part (bootstrapping, a regularizer, a target network) is another source of approximation error on top of whatever distributional-shift error it's trying to fix. When the logged data already comes from a strong policy and is fairly narrow, there is little room to improve on it, so the extra machinery of CQL or IQL buys little upside while still carrying its own estimation noise — BC, with no value function to get wrong, can win outright. This exact motivation is why TD3+BC ([arXiv:2106.06860](https://arxiv.org/abs/2106.06860)) exists: adding a plain BC term to an otherwise standard actor-critic loss matches more complex methods on much of the D4RL benchmark suite.

**Q: Why do OPE confidence intervals rarely settle a ship/no-ship decision on their own?**
**Short:** OPE intervals are usually too wide to distinguish two reasonable candidate policies, so a live A/B test is still required.

Even with a doubly robust estimator's variance reduction (§6 measured roughly a 21% cut, 133 to 105, on a dataset with 20% action support), the resulting confidence interval is often wide enough to contain both "this policy is a clear win" and "this policy is a clear loss." The direct-method component's accuracy is also fundamentally unverifiable offline — it was fit on the same logs it's now being used to correct, so its own bias is invisible from inside the estimate. OPE's real job is triage: reject policies whose interval is unambiguously bad, and pass the rest to a real, cheaper-than-it-looks canary or A/B test rather than treating any offline number as final.

**Q: What is the CQL alpha coefficient actually trading off, and what happens at the two extremes?**
**Short:** Alpha controls conservatism strength; alpha=0 reopens extrapolation error, alpha too large collapses value learning to near-BC.

`alpha_cql` scales the penalty term (`logsumexp_a Q(s,a) - Q(s,a_logged)`) against the ordinary TD loss. At `alpha=0`, CQL degenerates exactly to unconstrained off-policy Q-learning — §6's run at `alpha=0` produced a Q(forward)=8.26 with no mechanism to say whether that number is trustworthy or an extrapolation artifact. As `alpha` grows large, the penalty dominates the loss, every action's Q-value is compressed toward the logged action's, and the resulting policy converges toward simply imitating the data — the same ceiling behavior cloning has, defeating the purpose of using a value function at all. In practice `alpha` is tuned (or, in Lagrangian CQL variants, automatically adjusted) to sit between these extremes.

**Q: Why is importance-sampled OPE variance so much worse over a long horizon than a single step?**
**Short:** Per-decision importance ratios multiply across a trajectory, and the variance of that product grows explosively with horizon length.

A trajectory-level importance weight is the product of `H` per-step ratios, each with mean 1 and its own spread; even mild per-step variance compounds multiplicatively. §6's simulation showed effective sample size collapsing from 80% of the batch at `H=1` to 0.05% of the batch at `H=100` — 20,000 logged trajectories carrying the statistical weight of roughly 11 real ones. This is exactly why per-decision (step-wise) IS, doubly robust estimators, and model-based methods like fitted Q evaluation dominate long-horizon OPE in practice; plain trajectory-level IS is considered unusable past roughly a 10-20 step horizon.

**Q: What is extrapolation error, mechanistically, and why does it self-reinforce during training?**
**Short:** It's the Q-function's ungrounded guess at out-of-distribution actions, which greedy improvement systematically prefers.

`Q(s,a)` for a state-action pair the data never demonstrated is a function-approximator extrapolation with no ground truth to check it — like any regression queried outside its training distribution, it can land arbitrarily high or low with no penalty for being wrong, because nothing in the loss function has ever seen that input. The self-reinforcement comes from the `max` operator in policy improvement: among several noisy, ungrounded guesses across the action space, `max` systematically selects whichever one happens to be an overestimate, and bootstrapping then propagates that overestimate backward into every state that can reach it. §5's spike diagram shows the resulting shape directly — the two actions furthest from the behavior policy's support are exactly where the unconstrained network's guess is worst.

**Q: How does CQL's conservative penalty force the learned Q-function to be a provable lower bound?**
**Short:** By minimizing a softmax over all actions while maximizing the logged action's value, so untested actions are pushed below it.

The penalty term is `logsumexp_a Q(s,a) - Q(s, a_logged)`: the first term is a soft maximum over every action's current Q-value (including actions never seen at that state), and minimizing it pushes the whole distribution down, while the second term pulls the logged action's value back up. Kumar, Zhou, Tucker & Levine ([arXiv:2006.04779](https://arxiv.org/abs/2006.04779)) prove that, under a mild condition, the resulting Q-function lower-bounds the true value of the policy it's being used to evaluate — the training objective isn't just heuristically "make Q conservative," it is specifically calibrated to produce a bound you can trust in the pessimistic direction. §6's measured compression (8.26 to 2.01 for the same well-supported action) is that guarantee visible in a real run: some achievable value is deliberately sacrificed for the safety property.

**Q: How does IQL avoid ever evaluating the Q-function on an out-of-distribution action?**
**Short:** It fits V(s) by expectile regression on only the logged Q(s, a_logged), never on a max or an unseen action.

Standard TD control bootstraps off `max_a' Q(s', a')`, which requires evaluating Q at whatever action currently looks best — potentially one never logged at that state. IQL instead trains a separate value network `V(s)` as an **expectile regression** of `Q(s, a_logged)`: at expectile `tau > 0.5`, this asymmetrically weights positive residuals more heavily than negative ones, which pulls `V(s)` toward the *upper* end of the logged actions' values without ever computing `Q` at any action outside the batch. The Q-function then bootstraps off `V(s')` rather than `max_a' Q(s', a')`, so an out-of-distribution query never enters the picture at any stage. §6's run shows this concretely: `V(s)` rises monotonically toward the goal state using only the three logged actions per state, no `max` anywhere in the graph.

**Q: What does a doubly robust estimator combine, and why is it usually lower-variance than plain IPS?**
**Short:** It sums a direct-method value estimate with an importance-weighted correction, staying unbiased if either half is right.

DR estimates a candidate policy's value as `V_hat(s) + w * (r - Q_hat(s,a))` — a direct-method term (`V_hat`, computed from a trained Q-function, using no importance weight at all) plus an importance-weighted *residual* correction rather than the importance-weighted reward itself. The "doubly robust" property is that the estimate stays unbiased if either the direct-method Q-function or the propensity weights are correct, even if the other is wrong. Variance drops because the residual `(r - Q_hat)` is typically small and centered near zero wherever `Q_hat` is a reasonable model, so the importance weight — the main source of IPS's variance — is multiplying a much smaller quantity. §6 measured this directly: 133 (IPS) down to 105 (DR) on the same logs, roughly a 21% cut, using the same `q_cql` trained earlier in the same file as the direct-method term.

**Q: What is the philosophical difference between policy-constraint methods (BCQ, TD3+BC) and value-regularization methods (CQL)?**
**Short:** One constrains which actions the policy can propose; the other regularizes the value function used to judge them.

Policy-constraint methods restrict the *action space the policy is allowed to search*: BCQ ([arXiv:1812.02900](https://arxiv.org/abs/1812.02900)) trains a generative model of the data and only lets the policy pick among actions the generative model judges plausible; TD3+BC ([arXiv:2106.06860](https://arxiv.org/abs/2106.06860)) achieves something similar more simply, by adding a behavior-cloning term directly to the policy's loss. CQL instead leaves the policy's search unconstrained and regularizes the *Q-function itself*, so that even if the policy considers an unusual action, the value assigned to it is deliberately suppressed unless the data supports it. In practice the two families often converge on similar behavior — both are ways of keeping the effective policy near the data — but they intervene at different points in the pipeline, and that distinction is a common interview probe for whether a candidate actually understands the mechanism rather than just the algorithm names.

**Q: What does Decision Transformer do differently from value-based offline RL, and what is its known failure mode?**
**Short:** It replaces value functions with return-conditioned supervised sequence modeling, but struggles to stitch sub-optimal trajectories.

Decision Transformer ([arXiv:2106.01345](https://arxiv.org/abs/2106.01345)) trains a causal transformer to autoregressively predict the next action given past states, actions, and a target return-to-go — there is no Bellman backup, no Q-function, and no `max` anywhere, just an ordinary sequence-modeling loss. This sidesteps extrapolation error entirely, since it never bootstraps off its own value estimates. Its documented weakness is **stitching**: value-based methods can in principle recombine the best segments of several individually mediocre trajectories into one better than any of them, because the Bellman equation reasons about states independent of which trajectory they came from; a sequence model conditioned on return-to-go tends to reproduce trajectory shapes it actually saw. The IQL paper's D4RL comparison shows the size of the gap: on the four medium and large AntMaze datasets, which require stitching, DT scores 0.0 on every one, while IQL scores 39.6 to 71.2.

**Q: What role does propensity play in the importance-sampling ratio, and why must it be the actual logging probability rather than an assumed one?**
**Short:** Propensity is the importance ratio's denominator; using an assumed value instead of the real one silently corrupts every downstream estimate.

The importance-sampling ratio is `pi_e(a|s) / P_b(a|s)`, where `P_b(a|s)` must be the probability the behavior policy actually assigned to the action it took at that decision — not a guess, not a uniform-over-actions assumption, and not a value inferred after the fact from aggregate action frequencies (which conflates the marginal action distribution with the state-conditional one). Substituting an assumed propensity produces a ratio, and the ratio produces a number, and the number looks exactly like a valid OPE estimate — there is no error message, only a wrong answer computed with full confidence (§10, Pitfall 1). This is why the propensity must be logged as a first-class field at decision time, alongside the state, action, and reward, rather than reconstructed later.

**Q: When would you choose a contextual bandit formulation over full offline RL for a recommendation or pricing problem?**
**Short:** When the reward genuinely resolves in one step, a bandit is simpler, lower-variance, and far easier to evaluate offline.

If the decision's consequence is adequately captured by the immediate reward — a single recommendation's click, a single price's conversion, with no meaningful multi-step credit assignment — reformulating it as a multi-step MDP adds the entire machinery of Bellman backups, discounting, and horizon-dependent OPE variance (§6) for no benefit a one-step contextual bandit ([`online_learning_and_bandits.md`](../recommender_systems/online_learning_and_bandits.md)) doesn't already provide. The tell in an interview is credit assignment: if you can't articulate what specific future consequence today's action is trading off against, you don't have an RL problem, you have a bandit problem wearing an RL costume.

**Q: What happens to an offline-RL policy trained with a reward tied to a delayed business metric if the logged trajectory horizon is too short?**
**Short:** Training looks correct while silently optimizing a myopic proxy, because the logging boundary — not gamma — caps credit assignment.

If episodes are logged only up to, say, checkout, but the actual objective is a 90-day outcome like churn or lifetime value, no amount of increasing `gamma` toward 1 recovers the missing signal — there simply are no further logged steps for a high `gamma` to discount over, and the value function bootstraps correctly within the session and then stops. Nothing about training breaks visibly: loss curves converge, the Bellman updates are mathematically correct, and the shipped policy is confidently optimizing the wrong horizon (§10, Pitfall 2). The fix is either extending the logged episode to actually span the metric that matters, or explicitly training against a validated short-horizon proxy rather than assuming a long discount factor substitutes for missing data.

**Q: What did D4RL change about how offline RL algorithms are benchmarked?**
**Short:** It replaced data from partially-trained online agents with realistic sources like human demonstrations and mixed-quality policies.

Prior RL benchmarks typically generated "offline" datasets by taking snapshots of an online agent mid-training — data that is qualitatively unlike what a real production system logs. D4RL (Fu, Kumar, Nachum, Tucker & Levine, [arXiv:2004.07219](https://arxiv.org/abs/2004.07219)) instead assembled datasets from hand-designed controllers, human demonstrators, and mixtures of policies of varying quality, explicitly designed to surface properties relevant to real-world use — narrow coverage, mixed-quality logs, multitask settings. The paper's authors report this exposed real deficiencies in algorithms that looked fine on the older, easier benchmark style. D4RL is maintained by the Farama Foundation, and all of its offline datasets have moved to the successor library Minari.

**Q: How would you validate an offline-RL-trained pricing or dispatch policy before it ever touches a real customer?**
**Short:** Layer OPE screening, a Q-function sanity check against domain knowledge, shadow mode, and a staged canary before a real A/B test.

First, run OPE (§6) as a triage filter, not a final answer — reject anything with a clearly bad point estimate or an interval that doesn't clear the current production baseline. Second, sanity-check the learned Q-function against domain knowledge at states with poor logged coverage (§5's spike diagram is exactly what to look for) — an implausible value at a rarely-visited state is a red flag regardless of what OPE says. Third, run the candidate policy in shadow mode: log what it *would* have done against live traffic without acting on it, and compare its implied action distribution to what a domain expert would expect. Only then move to a staged canary — a small traffic percentage with guardrail metrics and a fast kill switch — before a full A/B test. No single offline number, however sophisticated the estimator, replaces this sequence; it only determines how much of the sequence you can skip with confidence.

---

## 13. Best Practices

1. **Log propensities from day one, even before you have an offline RL project.** Retrofitting exploration and propensity logging onto a deterministic production system is far more expensive than building it in from the start, and it is the single most common blocker discovered late (§10, Pitfall 1).
2. **Match the logged episode horizon to the metric you actually care about**, not to whatever boundary is operationally convenient (a session, a day). If the true objective is a 90-day outcome, either the trajectory needs to span it or the reward needs to be an explicitly validated shorter-horizon proxy (§10, Pitfall 2).
3. **Treat every offline number as a screening filter, never a ship decision.** Run OPE to reject clearly bad candidates and pass the rest to shadow mode and a staged canary; §12 covers why the confidence interval alone is rarely decisive.
4. **Start with the simplest method that could work — BC or a contextual bandit — before reaching for CQL, IQL, or a sequence model.** §8's comparison and §12's BC-beats-CQL question exist because the simplest baseline sometimes wins outright, and it is always the cheapest to validate.
5. **Use the trained Q-function as a doubly robust control variate whenever you already have one**, rather than defaulting to plain IPS — §6 measured a real variance reduction from doing exactly this with zero extra training cost.
6. **Sanity-check the learned value function at low-coverage states before trusting the policy it implies.** A spike or an implausible value where the behavior policy rarely visited (§5) is a warning sign an aggregate metric can hide.
7. **Prefer in-sample methods (IQL) or well-tuned value-regularization (CQL) over sequence modeling (Decision Transformer) whenever trajectory stitching plausibly matters** — most industrial logs are a mixture of policies of varying quality, exactly the setting where stitching separates a good offline RL result from a mediocre one (§12).
8. **Never assume a propensity you did not actually log.** A fabricated or aggregate-derived propensity produces an OPE estimate that looks valid and is not (§10, Pitfall 1) — there is no runtime signal that distinguishes it from a correct one.
9. **Audit coverage before choosing a method.** Count logged rows per `(state bucket, action)` cell and report the share of cells under a floor (for example 50 rows). If the candidate policy's top action falls in an empty cell for more than a few percent of traffic, no algorithm will fix it; widen the logging policy's exploration first.
10. **Store `terminated` and `truncated` as separate fields.** A shift end or a log rollover is not the end of the world, and merging the two flags silently zeroes the value of the last decision before every cut (§10, Pitfall 3).
11. **Select hyperparameters offline with the same estimator you will gate on.** There is no live environment for a validation rollout, so pick `alpha_cql`, `tau` or the BC weight by fitted-Q evaluation or DR on a held-out slice of logs, split by time, not at random; a random split leaks the same users and days into train and validation.
12. **Clip or self-normalize importance weights, and report the effective sample size beside every estimate.** An OPE number with an ESS of 40 out of 200,000 rows is a statement about 40 rows. Reporting ESS turns a hidden variance problem into a visible one.
13. **Normalize rewards and keep the scale fixed across retrains.** CQL's `alpha` and IQL's temperature are tuned against the reward scale; changing the revenue unit from dollars to cents multiplies TD errors by 100 and silently changes how strong the conservatism is.
14. **Version the behavior policy with the data.** Each row must name the policy version that produced its propensity. When the logging policy changes mid-dataset, an estimator that assumes one behavior policy is wrong for every row from the other.

---

## 14. Case Study

**Scenario:** A ride-hailing marketplace wants to replace its hand-tuned dispatch heuristic with a learned policy for one decision: when a driver goes idle, should the system dispatch them to the nearest waiting rider, or hold them for a slightly-further rider whose trip ends in a higher-demand zone? The team has 18 months of dispatch logs from a heuristic that already has 8% built-in randomization for exactly this kind of future offline analysis — a deliberate design choice made when the heuristic first launched (see [`design_marketplace_matching.md`](../case_studies/design_marketplace_matching.md) §11 for the online contextual-bandit layer this offline system feeds into).

**Why offline, not online:** a bad live dispatch decision strands a real rider and idles a real driver — exploring randomly at scale is a real-money, real-experience cost, not an abstraction. The team's logging discipline (propensities recorded per decision from day one) is what makes any of the rest of this possible; without it, §10's Pitfall 1 would apply and the project would stop here.

**Pipeline, using the flow from §5:**

1. **Logged interactions.** Each dispatch decision logs `(state, action, reward, propensity)`: state = driver location, idle time, nearby demand, time-of-day; action = which waiting rider to match (one of the candidates within radius); reward = trip revenue plus a driver-utilization bonus for ending the trip in a higher-demand zone; propensity = the heuristic's actual probability of choosing that rider, including its 8% randomization.
2. **Constrained training.** IQL is chosen over CQL after the §8 comparison: the team's logs are a mixture of the heuristic's near-deterministic core policy and its randomized exploration slice, and IQL's in-sample guarantee (never querying an unlogged match) was judged the safer default for a first deployment, with CQL kept as a documented fallback if IQL's achievable value proves too conservative.
3. **OPE gate.** The trained policy is scored with the doubly robust estimator from §6, using the IQL Q-function as the direct-method term. The team requires the DR point estimate to clear the current heuristic's logged average by a margin larger than the estimator's own confidence interval — not merely a positive delta — before promoting a candidate.
4. **Canary and A/B test.** Candidates that clear the OPE gate run first in shadow mode (logged, not acted on) for one week, then in a single-city canary with a kill switch on driver utilization and rider wait-time guardrails, before a full geographic A/B test.

**BROKEN -> FIX: the OPE gate first ran on reconstructed propensities.** The first gate build
did not join the logged per-decision propensity; it recomputed one as "how often was this kind
of action taken across the whole log". The heuristic picks the nearest rider 92% of the time
and a uniformly random candidate 8% of the time, so the true probability of picking one
specific non-nearest rider depends on how many candidates the state had. Downtown states have 4
candidates, suburban states 2, and the reconstructed number averages them away.

```python
import numpy as np

EPS = 0.08
# state type -> (share of decisions, candidates in radius, reward of "hold for zone rider")
STATES = {"downtown": (0.5, 4, 30.0), "suburb": (0.5, 2, 12.0)}


def true_propensity(n_candidates: int) -> float:
    """Probability the heuristic picked ONE specific non-nearest rider."""
    return EPS / n_candidates


def ips_expectation(propensity_of: dict[str, float]) -> float:
    """Expected IPS value of the candidate policy 'always hold for the zone rider'."""
    total = 0.0
    for name, (share, n_cand, reward) in STATES.items():
        p_logged = true_propensity(n_cand)          # how often the log actually holds this action
        total += share * p_logged * (1.0 / propensity_of[name]) * reward
    return total


true_value = sum(share * reward for share, _, reward in STATES.values())

# BROKEN: one pooled frequency for the action type, ignoring the state
pooled = sum(share * true_propensity(n) for share, n, _ in STATES.values())
broken = ips_expectation({name: pooled for name in STATES})

# FIX: the propensity logged at decision time, per state
fixed = ips_expectation({name: true_propensity(n) for name, (_, n, _) in STATES.items()})

print(f"true {true_value:.1f}  broken {broken:.1f}  fixed {fixed:.1f}")
# true 21.0  broken 18.0  fixed 21.0
assert np.isclose(fixed, true_value) and not np.isclose(broken, true_value)
```

The pooled propensity is 0.03 for both state types, while the true values are 0.02 downtown and
0.04 in the suburbs. The broken gate under-weights downtown rows by a third and over-weights
suburban rows by a third, and because downtown holds pay more, the estimate comes out 14% low
(18.0 against 21.0) even with infinite data. In production the bias ran the other way on a
different candidate, which is how the team found it: a policy that looked 6% better offline
was flat in the canary. The fix was a schema rule, not a model change: the gate refuses any
row whose propensity was not written by the serving path at decision time.

**Outcome and honest caveats:** the offline-trained policy's DR-estimated utilization gain was consistent with, though smaller than, the gain the online contextual-bandit pre-positioning layer already achieves in [`design_marketplace_matching.md`](../case_studies/design_marketplace_matching.md) §11 — expected, since the offline policy is solving a narrower single-decision problem, not the full shift-length pre-positioning problem. The team explicitly did not claim the OPE estimate as ground truth: the A/B test was run in full regardless of how strong the offline number looked, consistent with §12's answer on why OPE intervals rarely settle a ship decision alone.

**Interview framing:** this case study is a compact illustration of nearly every mechanism in this file at once — propensity logging as a prerequisite, an in-sample method chosen for its safety guarantee over raw achievable value, DR as the OPE gate, and a staged rollout that treats the offline estimate as a filter rather than a verdict. A candidate who can walk through why each of those four choices was made, not just name the algorithms, has demonstrated the actual judgment this file is trying to teach.
