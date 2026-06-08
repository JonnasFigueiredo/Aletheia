package io.aletheia.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Per-model token cost table. Defaults are loaded from the bundled {@code cost_table.yaml}. */
public final class CostTable {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private final Map<String, ModelCost> costs;

  private CostTable(Map<String, ModelCost> costs) {
    this.costs = Map.copyOf(costs);
  }

  /** Loads the default cost table from the classpath resource {@code /cost_table.yaml}. */
  public static CostTable fromResource() {
    try (InputStream in = CostTable.class.getResourceAsStream("/cost_table.yaml")) {
      if (in == null) {
        throw new IllegalStateException("cost_table.yaml not found on classpath");
      }
      Map<String, Map<String, Object>> raw =
          YAML.readValue(in, new TypeReference<Map<String, Map<String, Object>>>() {});
      Map<String, ModelCost> costs = new HashMap<>();
      raw.forEach(
          (model, vals) ->
              costs.put(
                  model,
                  new ModelCost(
                      toDouble(vals.get("input_per_1k"), 0.0),
                      toDouble(vals.get("output_per_1k"), 0.0))));
      return new CostTable(costs);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load cost_table.yaml", e);
    }
  }

  /**
   * Returns a new {@code CostTable} with per-model entries from {@code overrides} taking
   * precedence. Each entry in {@code overrides} must be a {@code Map} with {@code input_per_1k} and
   * {@code output_per_1k} keys.
   */
  public CostTable withOverrides(Map<String, Object> overrides) {
    if (overrides == null || overrides.isEmpty()) {
      return this;
    }
    Map<String, ModelCost> merged = new HashMap<>(this.costs);
    overrides.forEach(
        (model, raw) -> {
          if (raw instanceof Map<?, ?> m) {
            merged.put(
                model,
                new ModelCost(
                    toDouble(m.get("input_per_1k"), 0.0), toDouble(m.get("output_per_1k"), 0.0)));
          }
        });
    return new CostTable(merged);
  }

  /**
   * Computes the total USD cost for a request to {@code model} with {@code tokensIn} input tokens
   * and {@code tokensOut} output tokens. Unknown models are treated as zero cost.
   */
  public double computeCost(String model, int tokensIn, int tokensOut) {
    ModelCost mc = costs.getOrDefault(model != null ? model : "", ModelCost.ZERO);
    return (mc.inputPer1k() * tokensIn + mc.outputPer1k() * tokensOut) / 1000.0;
  }

  private static double toDouble(Object val, double defaultValue) {
    if (val instanceof Number n) {
      return n.doubleValue();
    }
    return defaultValue;
  }

  /** Per-model cost configuration in USD per 1 000 tokens. */
  public record ModelCost(double inputPer1k, double outputPer1k) {
    static final ModelCost ZERO = new ModelCost(0.0, 0.0);
  }
}
