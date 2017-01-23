package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import firm.Graph;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import minijava.Cli;
import org.jooq.lambda.Seq;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.pcollections.PMap;
import org.pcollections.PSet;
import org.pcollections.PVector;
import org.pcollections.TreePVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizerFramework {
  private static final Logger LOGGER = LoggerFactory.getLogger("OptimizerFramework");
  private final Optimizer[] idToOptimizers;
  private final List<Integer>[] referrers;

  private OptimizerFramework(Optimizer[] idToOptimizers, List<Integer>[] referrers) {
    this.idToOptimizers = idToOptimizers;
    this.referrers = referrers;
  }

  /**
   * This will run each optimization at least once until a fixed-point is reached. Note that
   * first-registered Optimizers have a higher priority of being run.
   */
  public void optimizeUntilFixedpoint(Graph graph) {
    SortedSet<Integer> toVisit = new TreeSet<>(Seq.range(0, idToOptimizers.length).toList());
    while (!toVisit.isEmpty()) {
      int next = toVisit.first();
      toVisit.remove(next);

      Optimizer chosenOptimizer = idToOptimizers[next];
      LOGGER.debug(chosenOptimizer.getClass().getSimpleName());
      Cli.dumpGraphIfNeeded(graph, "before-" + chosenOptimizer.getClass().getSimpleName());
      if (chosenOptimizer.optimize(graph)) {
        // The optimizer changed something, so we enqueue all dependent optimizers
        List<Integer> needRerun = referrers[next];
        LOGGER.debug(
            " ... changed. Bumping "
                + Iterables.toString(
                    seq(needRerun).map(i -> idToOptimizers[i].getClass().getSimpleName())));
        toVisit.addAll(needRerun);
      }
    }
  }

  public static class Builder {
    private final PVector<Optimizer> idToOptimizers;
    private final PMap<Optimizer, Integer> optimizersToId;
    private final PMap<Optimizer, PSet<Optimizer>> references;

    private Builder(
        PVector<Optimizer> idToOptimizers,
        PMap<Optimizer, Integer> optimizersToId,
        PMap<Optimizer, PSet<Optimizer>> references) {
      this.idToOptimizers = idToOptimizers;
      this.optimizersToId = optimizersToId;
      this.references = references;
    }

    public Builder() {
      this(TreePVector.empty(), HashTreePMap.empty(), HashTreePMap.empty());
    }

    public DependsOn add(Optimizer optimizer) {
      return new DependsOn(optimizer);
    }

    public OptimizerFramework build() {
      Optimizer[] idToOptimizers = seq(this.idToOptimizers).toArray(Optimizer[]::new);
      return new OptimizerFramework(idToOptimizers, invert(idToOptimizers, this.references));
    }

    private List<Integer>[] invert(
        Optimizer[] idToOptimizer, PMap<Optimizer, PSet<Optimizer>> edges) {
      List<Integer>[] inverted = (List<Integer>[]) new List[edges.size()];
      for (int i = 0; i < inverted.length; ++i) {
        Optimizer current = idToOptimizer[i];
        inverted[i] =
            Seq.range(0, inverted.length)
                .filter(
                    j ->
                        seq(edges.get(idToOptimizer[j]))
                            .anyMatch(
                                opt ->
                                    opt.equals(
                                        current))) // this filters all j which have current as neighbor
                .toList();
      }
      return inverted;
    }

    public class DependsOn {
      private final Optimizer focus;

      private DependsOn(Optimizer focus) {
        this.focus = focus;
      }

      public Builder dependsOn(Optimizer... deps) {
        PSet<Optimizer> depsIds = HashTreePSet.from(Seq.of(deps).toList());
        return new Builder(
            idToOptimizers.plus(focus),
            optimizersToId.plus(focus, idToOptimizers.size()),
            references.plus(focus, depsIds));
      }
    }
  }
}
