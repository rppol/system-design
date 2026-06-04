# ML Case Studies — Cross-Cutting Shared Primitives

These five files are shared infrastructure and methodology primitives that appear across multiple ML case studies. Each follows the full 14-section module template with 15+ interview Q&As. They exist here rather than inside individual case studies to prevent the same pattern (feature store design, calibration methodology, fairness audit) from being invented independently — and inconsistently — in each case study.

Read a cross-cutting file when you want to understand a production ML pattern in depth. The case studies that reference each file are listed in that file's Section 14 (Case Study).

---

## Deep-Dive Files

| File | Topic | Referenced by |
|------|-------|---------------|
| [feature_store_and_point_in_time_correctness.md](./feature_store_and_point_in_time_correctness.md) | Point-in-time correct feature joins, dual-store architecture, training-serving skew detection, freshness SLOs | design_churn_prediction, design_credit_risk_scoring, design_eta_prediction, design_marketplace_matching |
| [model_calibration_and_thresholding.md](./model_calibration_and_thresholding.md) | Platt scaling, isotonic regression, reliability diagrams, ECE, cost-sensitive thresholds, calibration monitoring | design_churn_prediction, design_credit_risk_scoring, design_eta_prediction, design_marketplace_matching |
| [responsible_ai_fairness_and_explainability.md](./responsible_ai_fairness_and_explainability.md) | Demographic parity, equalized odds, SHAP, LIME, counterfactual explanations, GDPR Article 22, adverse-action notices | design_credit_risk_scoring, design_churn_prediction, design_marketplace_matching, design_eta_prediction |
| [experimentation_and_online_evaluation.md](./experimentation_and_online_evaluation.md) | OEC / metric design, A/B testing, CUPED, interleaving, switchback, SRM detection, sequential testing | design_churn_prediction, design_credit_risk_scoring, design_eta_prediction, design_marketplace_matching |
| [drift_monitoring_and_retraining.md](./drift_monitoring_and_retraining.md) | PSI, KS test, score distribution monitoring, champion/challenger pattern, label latency, retraining automation | design_churn_prediction, design_credit_risk_scoring, design_eta_prediction, design_marketplace_matching |

---

## How to Use These Files

Each sub-file is self-contained — read it independently to understand the production ML pattern end-to-end. Then navigate to the case studies listed in its Section 14 to see how the pattern is applied in a specific product context.

Cross-reference links within case studies point here using relative paths:
```
[Feature Store and PIT Correctness](./cross_cutting/feature_store_and_point_in_time_correctness.md)
[Calibration and Thresholding](./cross_cutting/model_calibration_and_thresholding.md)
[Responsible AI, Fairness, Explainability](./cross_cutting/responsible_ai_fairness_and_explainability.md)
[Experimentation and Online Evaluation](./cross_cutting/experimentation_and_online_evaluation.md)
[Drift Monitoring and Retraining](./cross_cutting/drift_monitoring_and_retraining.md)
```

---

## Adding a New Cross-Cutting Sub-File

A pattern belongs here (not inside a case study) when it is referenced by 3+ case studies. Steps:

1. Create `cross_cutting/<pattern_name>.md` — 14-section module template, 15+ Q&As, 600-800 lines
2. Section 14 must list all case studies that use it, with one paragraph each on the specific application
3. Update this README table
4. Update `../README.md` (case studies learning path), `../../README.md` (ml master index), and root `README.md` Sub-Files Index
