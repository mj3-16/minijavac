package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Call;
import static firm.bindings.binding_irnode.ir_opcode.iro_Load;
import static firm.bindings.binding_irnode.ir_opcode.iro_Phi;
import static firm.bindings.binding_irnode.ir_opcode.iro_Proj;
import static firm.bindings.binding_irnode.ir_opcode.iro_Start;
import static firm.bindings.binding_irnode.ir_opcode.iro_Store;
import static firm.bindings.binding_irnode.ir_opcode.iro_Sync;
import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import firm.ArrayType;
import firm.BackEdges;
import firm.BackEdges.Edge;
import firm.ClassType;
import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.Mode;
import firm.PointerType;
import firm.Type;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.Load;
import firm.nodes.Member;
import firm.nodes.Node;
import firm.nodes.Phi;
import firm.nodes.Proj;
import firm.nodes.Sel;
import firm.nodes.Store;
import firm.nodes.Sync;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import minijava.ir.emit.Types;
import minijava.ir.utils.ExtensionalEqualityComparator;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.Seq;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.pcollections.IntTreePMap;
import org.pcollections.MapPSet;
import org.pcollections.PMap;
import org.pcollections.PSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AliasAnalyzer extends BaseOptimizer {

  private static final int UNKNOWN_OFFSET = -1;
  private static final Logger LOGGER = LoggerFactory.getLogger("AliasAnalyzer");

  private final Map<Node, Memory> memories = new HashMap<>();
  private final Map<Node, Set<IndirectAccess>> pointsTos = new HashMap<>();

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    fixedPointIteration(GraphUtils.topologicalOrder(graph));
    LoadStoreAliasingTransformation transformation = new LoadStoreAliasingTransformation();
    Boolean hasChanged = FirmUtils.withBackEdges(graph, transformation::transform);
    System.out.println("-------------------------------");
    return hasChanged;
  }

  private Set<IndirectAccess> getPointsTo(Node node) {
    return pointsTos.getOrDefault(node, new HashSet<>());
  }

  private void updatePointsTo(Node node, Set<IndirectAccess> newPointsTo) {
    update(pointsTos, node, newPointsTo);
  }

  private Memory getMemory(Node node) {
    return memories.getOrDefault(node, Memory.empty());
  }

  private void updateMemory(Node node, Memory newMemory) {
    update(memories, node, newMemory);
  }

  private <T> void update(Map<Node, T> map, Node key, T newValue) {
    T oldValue = map.put(key, newValue);
    hasChanged |= oldValue == null || !oldValue.equals(newValue);
    System.out.println("-------------------------------");
    System.out.println(key);
    System.out.println(newValue);
  }

  private void bumpSuccessors() {
    // For nodes like Call, Load and Store we have to bump successors, so that
    // they pick up Memory changes.
    hasChanged = true;
  }

  @Override
  public void visit(Phi node) {
    if (node.getMode().equals(Mode.getM())) {
      Memory memory =
          seq(node.getPreds()).map(this::getMemory).foldLeft(Memory.empty(), Memory::mergeWith);
      updateMemory(node, memory);
    } else {
      Set<IndirectAccess> pointsTo =
          seq(node.getPreds()).map(this::getPointsTo).flatMap(Seq::seq).toSet();
      updatePointsTo(node, pointsTo);
    }
  }

  @Override
  public void visit(Member node) {
    int offset = node.getEntity().getOffset();
    Type fieldType = node.getEntity().getType();
    ClassType containingClassType = (ClassType) node.getEntity().getOwner();

    Set<IndirectAccess> ptrPointsTo = getPointsTo(node.getPtr());

    Set<IndirectAccess> fieldReferences =
        seq(ptrPointsTo)
            .filter(a -> a.isBaseReferencePointingTo(containingClassType))
            .map(a -> new IndirectAccess(a.base, fieldType, offset))
            .toSet();

    updatePointsTo(node, fieldReferences);
  }

  @Override
  public void visit(Sel node) {
    ArrayType arrayType = (ArrayType) node.getType();
    int offset =
        NodeUtils.asConst(node.getIndex()).map(i -> i.getTarval().asInt()).orElse(UNKNOWN_OFFSET);

    Set<IndirectAccess> ptrPointsTo = getPointsTo(node.getPtr());
    Set<IndirectAccess> offsetReferences =
        seq(ptrPointsTo)
            .filter(a -> a.isBaseReferencePointingTo(arrayType))
            .map(a -> new IndirectAccess(a.base, arrayType.getElementType(), offset))
            .toSet();

    updatePointsTo(node, offsetReferences);
  }

  @Override
  public void visit(Call node) {
    bumpSuccessors();
  }

  @Override
  public void visit(Load node) {
    bumpSuccessors();
  }

  @Override
  public void visit(Store node) {
    bumpSuccessors();
  }

  @Override
  public void visit(Proj proj) {
    switch (proj.getPred().getOpCode()) {
      case iro_Load:
        transferProjOnLoad(proj, (Load) proj.getPred());
        return;
      case iro_Store:
        transferProjOnStore(proj, (Store) proj.getPred());
        return;
      case iro_Call:
        transferProjOnCall(proj, (Call) proj.getPred());
        return;
      case iro_Proj:
        transferProjOnProj(proj, (Proj) proj.getPred());
        return;
    }
  }

  private void transferProjOnCall(Proj proj, Call call) {
    if (proj.getNum() == Call.pnM) {
      Entity method = calledMethod(call);

      // A called function may possibly taint all shared chunks, plus the any new chunks.
      PSet<Node> taintedChunks =
          seq(aliasesWithCallee(call))
              .map(ia -> ia.base)
              // We also add the returned chunk (which is always represented by the call) as tainted.
              // If the called method is calloc however, we don't, since we know the result
              // will point to a fresh memory block which nothing may possibly alias.
              .append(isCalloc(method) ? Seq.empty() : Seq.of(call))
              .foldLeft(HashTreePSet.empty(), MapPSet::plus);

      Memory memory = getMemory(call.getMem());
      for (Node tainted : taintedChunks) {
        // Every tainted chunk can possibly alias any other tainted chunk.
        // This is a conservative approximation of which we know isn't precise enough
        // for e.g. calloc.
        assert memory != null;
        memory = memory.modifyChunk(tainted, chunk -> chunk.setSlot(UNKNOWN_OFFSET, taintedChunks));
      }
      updateMemory(proj, memory);
    }
  }

  @NotNull
  private Set<IndirectAccess> aliasesWithCallee(Call call) {
    Entity method = calledMethod(call);
    MethodType mt = (MethodType) method.getType();
    Set<IndirectAccess> sharedChunks = new HashSet<>();
    for (int i = 0; i < mt.getNParams(); ++i) {
      Node arg = call.getPred(i + 2);
      Set<IndirectAccess> pointsTo = getPointsTo(arg);
      Type paramType = mt.getParamType(i);
      if (!contributesToAliasAnalysis(paramType)) {
        continue;
      }
      PointerType ptrType = (PointerType) paramType;
      seq(pointsTo)
          .filter(ia -> ia.isBaseReferencePointingTo(ptrType.getPointsTo()))
          .forEach(sharedChunks::add);
    }
    return sharedChunks;
  }

  /** If a value is not a pointer, it will not contribute any new alias insights. */
  private static boolean contributesToAliasAnalysis(Type paramType) {
    return paramType instanceof PointerType;
  }

  private static Entity calledMethod(Call call) {
    return ((Address) call.getPtr()).getEntity();
  }

  private static boolean isCalloc(Entity method) {
    return method.equals(Types.CALLOC);
  }

  private void transferProjOnProj(Proj proj, Proj pred) {
    assert pred.getMode().equals(Mode.getT());

    // There are two cases when this happens:
    // 1. pred matches on a function call.
    // 2. pred matches on the start node to get the argument tuple.
    // In both cases we want the Start/Call node to act as the representant of a new alias class.
    // For multiple arguments this means we assume that they alias each other (which is a conservative
    // assumption).

    Type storageType = Type.createWrapper(NodeUtils.getLink(proj));
    if (!(contributesToAliasAnalysis(storageType))) {
      // We are not interested in analyzing aliases to value types (as they are copied anways)
      return;
    }

    PointerType pointerType = (PointerType) storageType;
    Node startOrCall = pred.getPred();
    Set<IndirectAccess> newPointsTo = new HashSet<>();
    // There's the case of functions with pointer arguments, which we mostly handle in visit(Call).
    // Here we only need to add all aliases made visible to the called function via arguments to
    // our points-to set.
    if (startOrCall.getOpCode() == iro_Call) {
      newPointsTo =
          seq(aliasesWithCallee((Call) startOrCall))
              .filter(ia -> ia.isBaseReferencePointingTo(pointerType.getPointsTo()))
              .toSet();
    }
    // The startOrCall node will also serve as our new base representant.
    // This implies that all function arguments alias each other and that all functions return
    // a new alias class (plus any possible tainted argument aliases).
    IndirectAccess newRepresentant = new IndirectAccess(startOrCall, pointerType.getPointsTo(), 0);
    newPointsTo.add(newRepresentant);

    updatePointsTo(proj, newPointsTo);
  }

  private void transferProjOnLoad(Proj proj, Load load) {
    if (proj.getNum() == Load.pnM) {
      // A load doesn't change memory
      updateMemory(proj, getMemory(load.getMem()));
    } else if (proj.getNum() == Load.pnRes) {
      // ... but we can say something about the loaded value
      Set<IndirectAccess> pointsTo = getPointsTo(load.getPtr());
      Type referencedType = getReferencedType(load.getPtr());
      Memory memory = getMemory(load.getMem());
      Set<IndirectAccess> loadedRefs = followIndirectRefsInMemory(pointsTo, memory, referencedType);
      updatePointsTo(proj, loadedRefs);
    }
  }

  private static Type getReferencedType(Node node) {
    switch (node.getOpCode()) {
      case iro_Member:
        return ((Member) node).getEntity().getType();
      case iro_Sel:
        // Yay for jFirms inaccurate return types
        return ((ArrayType) ((Sel) node).getType()).getElementType();
      default:
        throw new AssertionError("Could not find out referenced type of " + node + ".");
    }
  }

  @NotNull
  private static Set<IndirectAccess> followIndirectRefsInMemory(
      Set<IndirectAccess> pointsTo, Memory memory, Type referencedType) {
    Set<IndirectAccess> loadedRefs = new HashSet<>();
    if (!contributesToAliasAnalysis(referencedType)) {
      // If the loaded type is not a pointer, it is uninteresting for the rest
      // of the analysis, as it can never be the argument to a Sel or Member.
      // Also it can never alias any other reference.
      return loadedRefs;
    }

    // Think of MyClass[] var.
    // If we load var[2], referenceType will be a pointer to MyClass (the actual storageType).
    // That's exactly what we expect `ptrType` to be: A pointer to some class instance.
    // Now the actual memory chunk the pointer points to has the layout dictated by the MyClass type.
    // That's what we get by `ptrType.getPointsTo()`.
    PointerType ptrType = (PointerType) referencedType;

    for (IndirectAccess ref : pointsTo) {
      // We compute the indirection with our memory model, which consists of sparse chunks for
      // all allocation sites (+ arguments).
      Memory.Chunk allocatedChunk = memory.getChunk(ref.base);

      seq(allocatedChunk.getSlot(ref.offset))
          .map(base -> new IndirectAccess(base, ptrType.getPointsTo(), 0))
          .forEach(loadedRefs::add);
    }
    return loadedRefs;
  }

  private void transferProjOnStore(Proj proj, Store store) {
    if (proj.getNum() != Store.pnM) {
      // Store is only interesting for its memory effects
      return;
    }

    Set<IndirectAccess> ptrPointsTo = getPointsTo(store.getPtr());
    Set<IndirectAccess> valPointsTo = getPointsTo(store.getValue());
    Type referencedType = getReferencedType(store.getPtr());
    Memory memory = getMemory(store.getMem());
    Memory modifiedMemory =
        writeValuesToPossibleMemorySlots(memory, ptrPointsTo, valPointsTo, referencedType);
    // It doesn't make sense to talk about the result value of a Store.
    // Thus it also can't refer to and alias anything.
    updateMemory(proj, modifiedMemory);
  }

  private Memory writeValuesToPossibleMemorySlots(
      Memory memory,
      Set<IndirectAccess> ptrPointsTo,
      Set<IndirectAccess> valPointsTo,
      Type referencedType) {
    if (!(contributesToAliasAnalysis(referencedType))) {
      // The stored value is not a reference, so it is uninteresting for the analysis.
      // Value types can't be aliased, after all!
      return memory;
    }
    PointerType ptrType = (PointerType) referencedType;
    for (IndirectAccess ptrRef : ptrPointsTo) {
      memory =
          memory.modifyChunk(
              ptrRef.base,
              allocatedChunk -> {
                PSet<Node> possibleValues =
                    seq(valPointsTo)
                        .filter(valRef -> valRef.isBaseReferencePointingTo(ptrType.getPointsTo()))
                        .map(valRef -> valRef.base)
                        .foldLeft(HashTreePSet.empty(), MapPSet::plus);
                return allocatedChunk.setSlot(ptrRef.offset, possibleValues);
              });
    }
    return memory;
  }

  private static class IndirectAccess {

    private final Node base;
    private final Type pointedToType;
    /**
     * This is only null, iff base points to an array which was used in a Sel expression where the
     * index is not constant (and thus unknown).
     *
     * <p>Note that in this case, this may never be a base reference.
     */
    private final int offset;

    private IndirectAccess(Node base, Type pointedToType, int offset) {
      this.base = base;
      this.pointedToType = pointedToType;
      this.offset = offset;
    }

    public boolean isBaseReferencePointingTo(Type type) {
      // For base refs we know for sure that the offset has to be 0 (that's true for Java, where
      // reference types are never stored as values in an array, e.g. all references refer to the
      // start of `new`-allocated memory blocks.
      // Also we can filter many refs out just by comparing actual and expected types.
      return offset == 0 && this.pointedToType.equals(type);
    }

    @Override
    public String toString() {
      return "IndirectAccess{"
          + "base="
          + base
          + ", pointedToType="
          + pointedToType
          + ", offset="
          + offset
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IndirectAccess that = (IndirectAccess) o;
      return Objects.equals(base, that.base)
          && Objects.equals(pointedToType, that.pointedToType)
          && Objects.equals(offset, that.offset);
    }

    @Override
    public int hashCode() {
      return Objects.hash(base, pointedToType, offset);
    }
  }

  private static class Memory {
    private static final Memory EMPTY = new Memory(HashTreePMap.empty());
    private static final Chunk EMPTY_CHUNK = new Chunk();
    private final PMap<Node, Chunk> allocatedChunks;

    private Memory(PMap<Node, Chunk> allocatedChunks) {
      this.allocatedChunks = allocatedChunks;
    }

    public static Memory empty() {
      return EMPTY;
    }

    public Memory modifyChunk(Node base, Function<Chunk, Chunk> modifier) {
      return new Memory(allocatedChunks.plus(base, modifier.apply(getChunk(base))));
    }

    public Chunk getChunk(Node base) {
      return allocatedChunks.getOrDefault(base, EMPTY_CHUNK);
    }

    public Memory mergeWith(Memory other) {
      PMap<Node, Chunk> chunks = allocatedChunks;
      for (Map.Entry<Node, Chunk> entry : other.allocatedChunks.entrySet()) {
        Chunk oldValue = getChunk(entry.getKey());
        chunks = chunks.plus(entry.getKey(), oldValue.mergeWith(entry.getValue()));
      }
      return new Memory(chunks);
    }

    @Override
    public String toString() {
      return allocatedChunks.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Memory memory = (Memory) o;
      return Objects.equals(allocatedChunks, memory.allocatedChunks);
    }

    @Override
    public int hashCode() {
      return Objects.hash(allocatedChunks);
    }

    public static class Chunk {
      private final IntTreePMap<PSet<Node>> slots;

      public Chunk() {
        this(IntTreePMap.empty());
      }

      private Chunk(IntTreePMap<PSet<Node>> slots) {
        this.slots = slots;
      }

      public PSet<Node> getSlot(Integer offset) {
        if (offset == UNKNOWN_OFFSET) {
          // We don't know the offset for sure, so we conservatively return all stored values.
          return seq(slots.values())
              .flatMap(Seq::seq)
              .foldLeft(HashTreePSet.empty(), MapPSet::plus);
        }

        // The UNKNOWN_OFFSET is special: It represents every possible offset. Consequently,
        // we have to always merge with values at the UNKNOWN_OFFSET slot.
        return slots
            .getOrDefault(offset, HashTreePSet.empty())
            .plusAll(slots.getOrDefault(UNKNOWN_OFFSET, HashTreePSet.empty()));
      }

      public Chunk setSlot(int offset, PSet<Node> value) {
        if (offset == UNKNOWN_OFFSET) {
          // We can't tell which indices are overwritten and which are not, since this represents
          // any possible offset. So we just have to merge with what's already at null.
          value = value.plusAll(slots.getOrDefault(UNKNOWN_OFFSET, HashTreePSet.empty()));
        }
        // otherwise, we completely overwrite the slot by the new value (That's exactly why we
        // are tracking offsets, after all!).
        return new Chunk(slots.plus(offset, value));
      }

      public Chunk mergeWith(Chunk other) {
        IntTreePMap<PSet<Node>> slots = this.slots;
        for (Map.Entry<Integer, PSet<Node>> entry : other.slots.entrySet()) {
          PSet<Node> oldValue = getSlot(entry.getKey());
          slots = slots.plus(entry.getKey(), oldValue.plusAll(entry.getValue()));
        }
        return new Chunk(slots);
      }

      @Override
      public String toString() {
        return slots.toString();
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        Chunk chunk = (Chunk) o;
        return Objects.equals(slots, chunk.slots);
      }

      @Override
      public int hashCode() {
        return Objects.hash(slots);
      }
    }
  }

  private class LoadStoreAliasingTransformation {
    public boolean transform() {
      // This works by following memory edges and reordering them when there is no alias.
      boolean hasChanged = false;
      Seq<Node> allSideEffectChains =
          seq(graph.getEndBlock().getPreds()).flatMap(this::sideEffectChainBefore);
      for (Node sideEffect : allSideEffectChains) {
        // We got a Proj M. Now we try to redirect Mem edges of preds, always making sure we sync
        // diverging paths.
        if (!isLoadOrStore(sideEffect)) {
          continue;
        }

        System.out.println(sideEffect);

        // We assume that the Mem pred is at index 0 and that the ptr pred is at index 1
        // That's at least the case for Load and Store.
        Node ptr = sideEffect.getPred(1);
        // Since we look at a load or store, there is at least a Start node further up in the
        // side effect chain, so calling get() directly is safe.
        //noinspection OptionalGetWithoutIsPresent
        Node lastSideEffect = sideEffectChainBefore(sideEffect).findFirst().get();
        if (isLoadOrStore(lastSideEffect)) {
          Node predMem = lastSideEffect.getPred(0);
          Node predPtr = lastSideEffect.getPred(1);
          if (!mayAlias(ptr, predPtr)) {
            // we can point the mem node to the pred's
            redirectMem(sideEffect, predMem);
            // We also have to
            hasChanged = true;
          }
        }
      }
      return hasChanged;
    }

    private void redirectMem(Node sideEffect, Node newMem) {
      Node oldMem = sideEffect.getPred(0);
      // This is the easy part.
      sideEffect.setPred(0, newMem);

      // Now, we also have to insert and delete syncs.
      // Consider this chain:
      //    *
      //    |
      //    *
      //    |
      //    *   <-- we redirect this to the top node
      //   /|
      //  * *
      //
      //    *
      //   / \
      //  |   * <-- Now this would be a dangling side effect unreachable from End.
      //   \        So we need to insert a Sync.
      //    *
      //   /|
      //  * *
      //
      //    *
      //   / \
      //  *   *
      //   \ /
      //    *   <-- This is the new Sync node, with the original redirected node further up left.
      //   /|
      //  * *
      //
      // So we need to redirect all uses of the side effect's Proj to a fresh Sync node.

      Proj projOnSideEffect =
          Iterables.getOnlyElement(
              seq(BackEdges.getOuts(sideEffect))
                  .map(be -> be.node)
                  .ofType(Proj.class)
                  .filter(proj -> proj.getMode().equals(Mode.getM())));

      // We have to fix up all usages of the Proj by inserting a Sync node.
      // Redundant Sync nodes could be removed later, similar to Phi nodes.
      List<Edge> usages = Lists.newArrayList(BackEdges.getOuts(projOnSideEffect));
      Sync sync =
          (Sync) graph.newSync(projOnSideEffect.getBlock(), new Node[] {projOnSideEffect, oldMem});
      for (Edge be : usages) {
        be.node.setPred(be.pos, sync);
      }

      removeSyncOnSync(sync);

      System.out.println("-------------------------");
      System.out.println("AliasAnalyzer.redirectMem");
      System.out.println("sideEffect = " + sideEffect);
      System.out.println("oldMem = " + oldMem);
      System.out.println("newMem = " + newMem);
      System.out.println("sync = " + sync);
    }

    private void removeSyncOnSync(Sync node) {
      List<Edge> usages = Lists.newArrayList(BackEdges.getOuts(node));
      boolean optimizationMakesSense =
          usages.size() == 1 && usages.get(0).node.getOpCode() == iro_Sync;
      if (!optimizationMakesSense) {
        // We could in theory also do this if there was more than one usage.
        // But then we'd have to deduplicate all preds to those Sync nodes, and
        // checking for reachability is to complicated to be worth it.
        // This still leaves room for improvement, e.g. This might still
        // produce syncs obviously already depend on another Sync even without the
        // explicit predecessor.
        return;
      }
      Node successorSync = usages.get(0).node;
      // Now we can just merge the predecessor sets (order is unimportant for Sync)
      Node[] newPreds =
          seq(node.getPreds())
              .append(successorSync.getPreds())
              .filter(n -> !n.equals(node)) // We no longer need to depend on the old Sync!
              .distinct()
              .sorted(ExtensionalEqualityComparator.INSTANCE)
              .toArray(Node[]::new);

      Node newSync = graph.newSync(successorSync.getBlock(), newPreds);
      Graph.exchange(successorSync, newSync);
    }

    private boolean mayAlias(Node ptr1, Node ptr2) {
      HashSet<IndirectAccess> commonPointers = new HashSet<>(getPointsTo(ptr1));
      commonPointers.retainAll(getPointsTo(ptr2));
      return !commonPointers.isEmpty();
    }

    /**
     * Assumes that {@param node} itself is a side-effect. This effectively makes us not bother
     * about the intermediate Projs.
     */
    private Seq<Node> sideEffectChainBefore(Node node) {
      return Seq.unfold(
          node,
          last -> {
            if (last.getOpCode() == iro_Start) {
              return Optional.empty();
            }

            if (isPhiOrSync(last)) {
              // We don't iterate any further, as these have two mem preds
              // which doesn't make sense for our traversal.
              return Optional.empty();
            }

            Node someMemNode = last.getPred(0);
            assert someMemNode.getMode().equals(Mode.getM());

            if (isPhiOrSync(someMemNode)) {
              return Optional.of(tuple(someMemNode, someMemNode));
            }

            if (someMemNode.getOpCode() == iro_Proj) {
              Node pred = ((Proj) someMemNode).getPred();
              return Optional.of(tuple(pred, pred));
            }

            throw new AssertionError(
                "Intermediate memory nodes should be either Phis, Syncs or Projs.");
          });
    }

    private boolean isPhiOrSync(Node node) {
      ir_opcode opcode = node.getOpCode();
      return opcode == iro_Phi || opcode == iro_Sync;
    }

    private boolean isLoadOrStore(Node node) {
      return node.getOpCode() == iro_Load || node.getOpCode() == iro_Store;
    }
  }
}
