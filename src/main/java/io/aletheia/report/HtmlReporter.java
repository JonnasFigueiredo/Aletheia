package io.aletheia.report;

import io.aletheia.runner.CaseResult;
import io.aletheia.runner.RunSummary;
import io.aletheia.runner.baseline.Regression;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes a {@link RunSummary} as a self-contained HTML file with inline CSS.
 *
 * <p>No external template engine; rendering uses Java text blocks and a small HTML-escape helper.
 */
public final class HtmlReporter {

  private HtmlReporter() {}

  /** Writes the report to {@code outputPath}, creating parent directories as needed. */
  public static void write(RunSummary summary, Path outputPath) throws IOException {
    Files.createDirectories(outputPath.getParent() == null ? Path.of(".") : outputPath.getParent());
    String html = render(summary);
    Files.writeString(outputPath, html, StandardCharsets.UTF_8);
  }

  private static String render(RunSummary summary) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Aletheia Run Summary</title>
  <style>
    *{box-sizing:border-box;margin:0;padding:0}
    body{font-family:system-ui,sans-serif;background:#f8f9fa;color:#212529;padding:24px}
    h1{font-size:1.5rem;margin-bottom:16px}
    h2{font-size:1.1rem;margin:24px 0 8px}
    .bar{display:flex;gap:16px;background:#fff;border:1px solid #dee2e6;border-radius:6px;
         padding:16px;margin-bottom:24px;flex-wrap:wrap}
    .stat{display:flex;flex-direction:column;align-items:center;min-width:100px}
    .stat-value{font-size:1.8rem;font-weight:700}
    .stat-label{font-size:.75rem;color:#6c757d;text-transform:uppercase;letter-spacing:.05em}
    .pass{color:#198754}.fail{color:#dc3545}
    table{width:100%;border-collapse:collapse;background:#fff;
          border:1px solid #dee2e6;border-radius:6px;overflow:hidden;margin-bottom:24px}
    th{background:#e9ecef;text-align:left;padding:8px 12px;font-size:.85rem;
       font-weight:600;border-bottom:1px solid #dee2e6}
    td{padding:8px 12px;font-size:.85rem;border-bottom:1px solid #f1f3f5;
       vertical-align:top;max-width:320px;word-break:break-word}
    tr:last-child td{border-bottom:none}
    .badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:.75rem;
           font-weight:600}
    .badge-pass{background:#d1e7dd;color:#0f5132}
    .badge-fail{background:#f8d7da;color:#842029}
    .score-pass{color:#198754;font-weight:600}
    .score-fail{color:#dc3545;font-weight:600}
    .diff-section{background:#fff3cd;border:1px solid #ffc107;border-radius:6px;
                  padding:16px;margin-bottom:24px}
  </style>
</head>
<body>
""");

    sb.append("<h1>Aletheia Run Summary</h1>\n");

    // Summary bar
    sb.append("<div class=\"bar\">\n");
    stat(sb, String.valueOf(summary.getTotalCases()), "Total Cases", null);
    stat(sb, String.valueOf(summary.getPassedCases()), "Passed", "pass");
    stat(
        sb,
        String.valueOf(summary.getFailedCases()),
        "Failed",
        summary.getFailedCases() > 0 ? "fail" : null);
    stat(sb, summary.getTotalLatencyMs() + "ms", "Total Latency", null);
    stat(sb, String.format("$%.6f", summary.getTotalCostUsd()), "Est. Cost", null);
    if (summary.getProvider() != null) {
      stat(sb, esc(summary.getProvider()), "Provider", null);
    }
    if (summary.getModel() != null) {
      stat(sb, esc(summary.getModel()), "Model", null);
    }
    sb.append("</div>\n");

    // Baseline diff section
    List<Regression> diff = summary.getBaselineDiff();
    if (diff != null && !diff.isEmpty()) {
      sb.append("<div class=\"diff-section\">\n");
      sb.append("<h2>Baseline Regressions (").append(diff.size()).append(")</h2>\n");
      sb.append("<table>\n<thead><tr>");
      sb.append("<th>Case ID</th><th>Evaluator</th><th>Baseline Score</th>")
          .append("<th>Current Score</th><th>Delta</th>");
      sb.append("</tr></thead>\n<tbody>\n");
      for (Regression r : diff) {
        sb.append("<tr>");
        sb.append("<td>").append(esc(r.getCaseId())).append("</td>");
        sb.append("<td>").append(esc(r.getEvaluatorType())).append("</td>");
        sb.append("<td>").append(String.format("%.4f", r.getPreviousScore())).append("</td>");
        sb.append("<td class=\"score-fail\">")
            .append(String.format("%.4f", r.getCurrentScore()))
            .append("</td>");
        sb.append("<td class=\"score-fail\">")
            .append(String.format("%.4f", r.getDelta()))
            .append("</td>");
        sb.append("</tr>\n");
      }
      sb.append("</tbody>\n</table>\n</div>\n");
    }

    // Per-evaluator stats table
    if (!summary.getEvaluatorStats().isEmpty()) {
      sb.append("<h2>Per-Evaluator Summary</h2>\n");
      sb.append("<table>\n<thead><tr>");
      sb.append(
          "<th>Evaluator</th><th>Count</th><th>Passed</th><th>Pass Rate</th><th>Mean Score</th>");
      sb.append("</tr></thead>\n<tbody>\n");
      summary
          .getEvaluatorStats()
          .forEach(
              (type, stats) -> {
                sb.append("<tr>");
                sb.append("<td>").append(esc(type)).append("</td>");
                sb.append("<td>").append(stats.getCount()).append("</td>");
                sb.append("<td>").append(stats.getPassed()).append("</td>");
                String rateClass = stats.getPassRate() >= 1.0 ? "score-pass" : "score-fail";
                sb.append("<td class=\"")
                    .append(rateClass)
                    .append("\">")
                    .append(String.format("%.1f%%", stats.getPassRate() * 100))
                    .append("</td>");
                sb.append("<td class=\"")
                    .append(rateClass)
                    .append("\">")
                    .append(String.format("%.4f", stats.getMeanScore()))
                    .append("</td>");
                sb.append("</tr>\n");
              });
      sb.append("</tbody>\n</table>\n");
    }

    // Per-case table
    sb.append("<h2>Cases</h2>\n");
    sb.append("<table>\n<thead><tr>");
    sb.append(
        "<th>Case ID</th><th>Status</th><th>Latency (ms)</th><th>Cost"
            + " (USD)</th><th>Evaluators</th>");
    sb.append("</tr></thead>\n<tbody>\n");

    for (CaseResult cr : summary.getCaseResults()) {
      sb.append("<tr>");
      sb.append("<td>").append(esc(cr.getEvalCase().getId())).append("</td>");
      String statusClass = cr.isPassed() ? "badge-pass" : "badge-fail";
      String statusLabel = cr.isPassed() ? "PASS" : "FAIL";
      sb.append("<td><span class=\"badge ")
          .append(statusClass)
          .append("\">")
          .append(statusLabel)
          .append("</span></td>");
      sb.append("<td>").append(cr.getTotalLatencyMs()).append("</td>");
      sb.append("<td>").append(String.format("%.6f", cr.getEstimatedCostUsd())).append("</td>");
      sb.append("<td>");
      for (int i = 0; i < cr.getEvalResults().size(); i++) {
        var er = cr.getEvalResults().get(i);
        String sc = er.isPassed() ? "score-pass" : "score-fail";
        sb.append("<span class=\"")
            .append(sc)
            .append("\">")
            .append(esc(er.getEvaluatorType()))
            .append("=")
            .append(String.format("%.3f", er.getScore()))
            .append("</span>");
        if (i < cr.getEvalResults().size() - 1) sb.append("<br>");
      }
      sb.append("</td>");
      sb.append("</tr>\n");
    }

    sb.append("</tbody>\n</table>\n");
    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  private static void stat(StringBuilder sb, String value, String label, String colorClass) {
    sb.append("<div class=\"stat\">");
    String cls = "stat-value" + (colorClass != null ? " " + colorClass : "");
    sb.append("<span class=\"").append(cls).append("\">").append(value).append("</span>");
    sb.append("<span class=\"stat-label\">").append(esc(label)).append("</span>");
    sb.append("</div>\n");
  }

  static String esc(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
