package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Load;
import static firm.bindings.binding_irnode.ir_opcode.iro_Phi;
import static firm.bindings.binding_irnode.ir_opcode.iro_Proj;
import static firm.bindings.binding_irnode.ir_opcode.iro_Start;
import static firm.bindings.binding_irnode.ir_opcode.iro_Store;
import static firm.bindings.binding_irnode.ir_opcode.iro_Sync;
import static org.jooq.lambda.Seq.seq;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import minijava.ir.emit.Types;
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

/**
 * Performs a may-alias analysis on Memory dependencies.
 *
 * <p>Two pointers a and b may-alias, if in some run of the program it cannot be ruled out whether a
 * points to the same memory region as b.
 *
 * <p>The reverse information is pretty important: When we can prove that a and b may not alias,
 * they can effectively be computed independent of one another, so we don't need a transitive memory
 * dependency between them.
 *
 * <p>The goal of this analysis is to elimininate these unnecessary memory depenendencies, so that
 * the {@link LoadStoreOptimizer} can optimize much more aggressively.
 *
 * <p>The analysis takes the following information into account:
 *
 * <ul>
 *   <li>Flow: We calculate alias information per node.
 *   <li>Types: When a points to an instance of A and b to an instance of B, we know they can't
 *       alias.
 *   <li>Offsets: When we access a[0] this should obviously be independent of a[1].
 * </ul>
 */
public class AliasAnalyzer extends BaseOptimizer {

  private static final int MAX_RELEVANT_NODES = 1000;
  private static final int UNKNOWN_OFFSET = -1;
  private static final Logger LOGGER = LoggerFactory.getLogger("AliasAnalyzer");

  /** The model of the memory at a certain program point. */
  private final Map<Node, Memory> memories = new HashMap<>();
  /**
   * Models the set of aliased memory locations the node might point to. For side-effecting nodes,
   * this represents the set of possibly tainted memory locations.
   */
  private final Map<Node, Set<IndirectAccess>> pointsTos = new HashMap<>();

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    memories.clear();
    pointsTos.clear();
    ArrayList<Node> worklist = GraphUtils.topologicalOrder(graph);
    long complexity = seq(worklist).filter(AliasAnalyzer::isRelevantNode).count();
    if (complexity > MAX_RELEVANT_NODES) {
      // The analysis is too slow for big graphs...
      return false;
    }
    try {
      fixedPointIteration(worklist);
    } catch (TooComplexError e) {
      // Well, that's unfortunate.
      return false;
    }
    LoadStoreAliasingTransformation transformation = new LoadStoreAliasingTransformation();
    return FirmUtils.withBackEdges(graph, transformation::transform);
  }

  private static boolean isRelevantNode(Node node) {
    switch (node.getOpCode()) {
      case iro_Store:
      case iro_Load:
      case iro_Call:
      case iro_Phi:
      case iro_Sync:
        return true;
    }
    return false;
  }

  private Set<IndirectAccess> getPointsTo(Node node) {
    return pointsTos.getOrDefault(node, new HashSet<>());
  }

  private void updatePointsTo(Node node, Set<IndirectAccess> newPointsTo) {
    if (newPointsTo.size() > 1000) {
      // This will lead to unbearable performance otherwise
      throw new TooComplexError();
    }
    update(pointsTos, node, newPointsTo, HashTreePSet.empty());
  }

  private Memory getMemory(Node node) {
    return memories.getOrDefault(node, Memory.empty());
  }

  private void updateMemory(Node node, Memory newMemory) {
    update(memories, node, newMemory, Memory.empty());
  }

  private <T> void update(Map<Node, T> map, Node key, T newValue, T defaultValue) {
    T oldValue = map.put(key, newValue);
    if (oldValue == null) {
      oldValue = defaultValue;
    }
    hasChanged |= !oldValue.equals(newValue);
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
  public void visit(Sync node) {
    Memory memory =
        seq(node.getPreds()).map(this::getMemory).foldLeft(Memory.empty(), Memory::mergeWith);
    updateMemory(node, memory);
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
  public void visit(Call call) {
    Memory memory = getMemory(call.getMem());
    Entity method = calledMethod(call);

    // Which memory locations can be aliased by the callee?
    // aliasesSharedWithCallee will find all possible transitive aliasing pointers
    // that are visible through the function's arguments.
    Set<IndirectAccess> argumentAliases = aliasesSharedWithCallee(call, memory);

    // A called function may possibly taint all shared chunks, plus any new chunks.
    PSet<Node> taintedChunks =
        seq(argumentAliases)
            .map(ia -> ia.base)
            // We also add the returned chunk (which is always represented by the call) as tainted.
            // If the called method is calloc however, we don't, since we know the result
            // will point to a fresh memory block which nothing may possibly alias.
            .append(isCalloc(method) ? Seq.empty() : Seq.of(call))
            .foldLeft(HashTreePSet.empty(), MapPSet::plus);

    for (Node tainted : taintedChunks) {
      // Every tainted chunk can possibly alias any other tainted chunk.
      // This is a conservative approximation of which we know isn't precise enough
      // for e.g. calloc.
      memory = memory.modifyChunk(tainted, chunk -> chunk.setSlot(UNKNOWN_OFFSET, taintedChunks));
    }
    updateMemory(call, memory);

    // Now that we have tainted the memory, we can compute the aliasing state *after* the callee.
    // Note that now there might even be hidden new references in some transitive argument.
    Set<IndirectAccess> newPointsTo = aliasesSharedWithCallee(call, memory);
    updatePointsTo(call, newPointsTo);
  }

  @Override
  public void visit(Load load) {
    // A load doesn't change memory
    updateMemory(load, getMemory(load.getMem()));
    // ... but we can say something about the loaded value
    Set<IndirectAccess> pointsTo = getPointsTo(load.getPtr());
    Type referencedType = getReferencedType(load.getPtr());
    Memory memory = getMemory(load.getMem());
    Set<IndirectAccess> loadedRefs =
        followIndirectAccessesInMemory(pointsTo, memory, referencedType);
    updatePointsTo(load, loadedRefs);
  }

  @Override
  public void visit(Store store) {
    // Store is only interesting for its side effects. Yet we record the pointed to set,
    // which means in this case 'might be modified by'.
    Set<IndirectAccess> ptrPointsTo = getPointsTo(store.getPtr());
    updatePointsTo(store, ptrPointsTo);
    Set<IndirectAccess> valPointsTo = getPointsTo(store.getValue());
    Type referencedType = getReferencedType(store.getPtr());
    Memory memory = getMemory(store.getMem());
    Memory modifiedMemory =
        writeValuesToPossibleMemorySlots(memory, ptrPointsTo, valPointsTo, referencedType);
    // It doesn't make sense to talk about the result value of a Store.
    // Thus it also can't refer to and alias anything.
    updateMemory(store, modifiedMemory);
  }

  @Override
  public void visit(Proj proj) {
    if (proj.getMode().equals(Mode.getM())) {
      // we just forward the memory
      updateMemory(proj, getMemory(proj.getPred()));
    } else if (proj.getPred().getOpCode() == iro_Proj) {
      transferProjOnProj(proj, (Proj) proj.getPred());
    } else {
      updatePointsTo(proj, getPointsTo(proj.getPred()));
    }
  }

  @NotNull
  private Set<IndirectAccess> aliasesSharedWithCallee(Call call, Memory memory) {
    Entity method = calledMethod(call);
    MethodType mt = (MethodType) method.getType();
    Set<IndirectAccess> sharedAliases = new HashSet<>();
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
          .flatMap(ia -> transitiveAliasesOfChunk(ia, memory))
          .forEach(sharedAliases::add);
    }
    // Also the callee might set new aliases to every possible known location.
    Set<IndirectAccess> possibleNewAliases =
        seq(sharedAliases).map(ia -> new IndirectAccess(call, ia.pointedToType, ia.offset)).toSet();
    sharedAliases.addAll(possibleNewAliases);
    return sharedAliases;
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
    // There's the case of functions with pointer arguments, which we mostly handle in visit(Call).
    // Here we only need to consider the aliases of matching type and add them to our points-to set.
    Set<IndirectAccess> newPointsTo =
        seq(getPointsTo(startOrCall))
            .filter(ia -> ia.isBaseReferencePointingTo(pointerType.getPointsTo()))
            .toSet();
    // The startOrCall node will also serve as our new base representant.
    // This implies that all function arguments alias each other and that all functions return
    // a new alias class (plus any possible tainted argument aliases).
    IndirectAccess newRepresentant = new IndirectAccess(startOrCall, pointerType.getPointsTo(), 0);
    newPointsTo.add(newRepresentant);

    updatePointsTo(proj, newPointsTo);
  }

  private Seq<IndirectAccess> transitiveAliasesOfChunk(IndirectAccess ref, Memory memory) {
    // Calls to base may potentially modify any field/array element.
    Set<IndirectAccess> transitiveAliases = new HashSet<>();
    Set<IndirectAccess> toVisit = new HashSet<>();
    toVisit.add(ref);

    while (!toVisit.isEmpty()) {
      IndirectAccess cur = toVisit.iterator().next();
      toVisit.remove(cur);
      if (transitiveAliases.contains(cur)) {
        continue;
      }
      transitiveAliases.add(cur);

      if (cur.pointedToType instanceof ArrayType) {
        // We also return an access to all elements
        Type elementType = ((ArrayType) cur.pointedToType).getElementType();
        toVisit.add(new IndirectAccess(cur.base, elementType, UNKNOWN_OFFSET));
      } else if (cur.pointedToType instanceof ClassType) {
        // We return an access to all fields
        ClassType classType = (ClassType) cur.pointedToType;
        int n = classType.getNMembers();
        for (int i = 0; i < n; ++i) {
          Entity field = classType.getMember(i);
          toVisit.add(new IndirectAccess(cur.base, field.getType(), field.getOffset()));
        }
      } else if (cur.pointedToType instanceof PointerType) {
        // We follow the ref and add all transitive refs.
        followIndirectAccessInMemory(cur, memory).forEach(toVisit::add);
      }
    }

    return seq(transitiveAliases);
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
  private static Set<IndirectAccess> followIndirectAccessesInMemory(
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

    seq(pointsTo)
        .filter(ref -> ref.pointedToType.equals(ptrType))
        .flatMap(ref -> followIndirectAccessInMemory(ref, memory))
        .forEach(loadedRefs::add);

    return loadedRefs;
  }

  private static Seq<IndirectAccess> followIndirectAccessInMemory(
      IndirectAccess ref, Memory memory) {
    // We compute the indirection with our memory model, which consists of sparse chunks for
    // all allocation sites (+ arguments).
    assert ref.pointedToType instanceof PointerType;
    Type newBaseType = ((PointerType) ref.pointedToType).getPointsTo();
    Memory.Chunk allocatedChunk = memory.getChunk(ref.base);
    return seq(allocatedChunk.getSlot(ref.offset))
        .map(base -> new IndirectAccess(base, newBaseType, 0));
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
      if (other instanceof AliasedMemory) {
        // We better be conservative for performance.
        return other.mergeWith(this);
      }

      PMap<Node, Chunk> chunks = allocatedChunks;
      for (Map.Entry<Node, Chunk> entry : other.allocatedChunks.entrySet()) {
        Chunk oldValue = getChunk(entry.getKey());
        chunks = chunks.plus(entry.getKey(), oldValue.mergeWith(entry.getValue()));
      }

      if (chunks.size() > 50) {
        // Convert this memory to a conservative fallback AliasedMemory.
        AliasedChunk initialChunk = new AliasedChunk(HashTreePSet.from(chunks.keySet()));
        AliasedChunk completeChunk =
            seq(chunks.values()).foldLeft(initialChunk, AliasedChunk::mergeWith);
        return new AliasedMemory(completeChunk.encounteredNodes);
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

      public PSet<Node> getSlot(int offset) {
        if (offset == UNKNOWN_OFFSET) {
          // We don't know the offset for sure, so we conservatively return all stored values.
          return seq(slots.values())
              .flatMap(Seq::seq)
              .foldLeft(HashTreePSet.empty(), MapPSet::plus);
        }

        // The UNKNOWN_OFFSET is special: It represents every possible offset. Consequently,
        // we have to always merge with values at the UNKNOWN_OFFSET slot.
        PSet<Node> unknown = slots.getOrDefault(UNKNOWN_OFFSET, HashTreePSet.empty());
        return slots.getOrDefault(offset, HashTreePSet.empty()).plusAll(unknown);
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
        if (other instanceof AliasedChunk) {
          // We want to favor the conservative fallback solution, because performance
          return other.mergeWith(this);
        }
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

    /**
     * This is an ugly hack for a conservative fallback memory that doesn't track offsets nor single
     * memory slots.
     */
    private static class AliasedMemory extends Memory {
      private final PSet<Node> encounteredNodes;

      public AliasedMemory(PSet<Node> encounteredNodes) {
        super(null);
        this.encounteredNodes = encounteredNodes;
      }

      @Override
      public Memory modifyChunk(Node base, Function<Chunk, Chunk> modifier) {
        AliasedChunk changed = (AliasedChunk) modifier.apply(new AliasedChunk(encounteredNodes));
        return new AliasedMemory(changed.encounteredNodes);
      }

      @Override
      public Chunk getChunk(Node base) {
        return encounteredNodes.contains(base) ? new AliasedChunk(encounteredNodes) : EMPTY_CHUNK;
      }

      @Override
      public Memory mergeWith(Memory other) {
        if (other instanceof AliasedMemory) {
          PSet<Node> bothEncountered =
              encounteredNodes.plusAll(((AliasedMemory) other).encounteredNodes);
          return new AliasedMemory(bothEncountered);
        } else {
          AliasedChunk completeChunk =
              seq(other.allocatedChunks.values())
                  .foldLeft(new AliasedChunk(encounteredNodes), AliasedChunk::mergeWith);
          return new AliasedMemory(
              completeChunk.encounteredNodes.plusAll(other.allocatedChunks.keySet()));
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        if (!super.equals(o)) {
          return false;
        }
        AliasedMemory that = (AliasedMemory) o;
        return Objects.equals(encounteredNodes, that.encounteredNodes);
      }

      @Override
      public int hashCode() {
        return Objects.hash(super.hashCode(), encounteredNodes);
      }

      @Override
      public String toString() {
        return "AliasedMemory{" + "encounteredNodes=" + encounteredNodes + '}';
      }
    }

    private static class AliasedChunk extends Chunk {
      private final PSet<Node> encounteredNodes;

      public AliasedChunk(PSet<Node> nodes) {
        super();
        encounteredNodes = nodes;
      }

      @Override
      public PSet<Node> getSlot(int offset) {
        return encounteredNodes;
      }

      @Override
      public Chunk setSlot(int offset, PSet<Node> value) {
        return new AliasedChunk(encounteredNodes.plusAll(value));
      }

      @Override
      public AliasedChunk mergeWith(Chunk other) {
        if (other instanceof AliasedChunk) {
          PSet<Node> bothEncountered =
              encounteredNodes.plusAll(((AliasedChunk) other).encounteredNodes);
          return new AliasedChunk(bothEncountered);
        } else {
          PSet<Node> allEncountered =
              seq(other.slots.values()).foldLeft(encounteredNodes, PSet::plusAll);
          return new AliasedChunk(allEncountered);
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }
        if (!super.equals(o)) {
          return false;
        }
        AliasedChunk that = (AliasedChunk) o;
        return Objects.equals(encounteredNodes, that.encounteredNodes);
      }

      @Override
      public int hashCode() {
        return Objects.hash(super.hashCode(), encounteredNodes);
      }

      @Override
      public String toString() {
        return "AliasedChunk{" + "encounteredNodes=" + encounteredNodes + '}';
      }
    }
  }

  private class LoadStoreAliasingTransformation {

    private final int MAX_NUMBER_OF_SYNC_PREDS = 10;

    public boolean transform() {
      // This works by following memory edges and reordering them when there is no alias.
      boolean hasChanged = false;
      for (Node sideEffect : GraphUtils.topologicalOrder(graph)) {
        // We try to point the Mem pred as far up in the graph as possible.
        // That can possibly mean that we have to insert multiple Sync nodes.
        if (!isLoadOrStore(sideEffect)) {
          continue;
        }

        Set<Node> aliasing = lastAliasingSideEffects(sideEffect);
        Node newMem;
        if (aliasing.size() > 1) {
          // This should drastically cut down on some edges and should
          // always preserve the invariant that all other aliased nodes
          // transitively depend on the Start node.
          aliasing.remove(graph.getStart());
        }

        if (aliasing.size() == 1) {
          newMem = NodeUtils.projModeMOf(aliasing.iterator().next());
        } else if (aliasing.size() > 50) {
          newMem = sideEffect.getPred(0);
        } else {
          Node[] mems = seq(aliasing).map(NodeUtils::projModeMOf).toArray(Node[]::new);
          newMem = graph.newSync(sideEffect.getBlock(), mems);
        }

        if (!newMem.equals(sideEffect.getPred(0))) {
          hasChanged = true;
          redirectMem(sideEffect, newMem);
        }
      }
      return hasChanged;
    }

    /**
     * Performs a pre-order search on all previous side effects, stopping on aliasing side effects.
     */
    private Set<Node> lastAliasingSideEffects(Node sideEffect) {
      Set<Node> ret = new HashSet<>();
      Set<Node> toVisit = NodeUtils.getPreviousSideEffects(sideEffect.getPred(0));
      Set<Node> visited = new HashSet<>();
      while (!toVisit.isEmpty()) {
        Node prevSideEffect = toVisit.iterator().next();
        toVisit.remove(prevSideEffect);
        if (visited.contains(prevSideEffect)) {
          continue;
        }
        visited.add(prevSideEffect);

        // We assume that the Mem pred is at index 0 and that the ptr pred is at index 1
        // That's at least the case for Load and Store.
        Node ptr = sideEffect.getPred(1);

        boolean cannotMoveBeyond =
            prevSideEffect.getOpCode() == iro_Start
                || prevSideEffect.getOpCode() == iro_Phi
                || mayAlias(ptr, aliasClass(prevSideEffect));
        if (cannotMoveBeyond) {
          ret.add(prevSideEffect);
        } else {
          Node prevMem = prevSideEffect.getPred(0);
          seq(NodeUtils.getPreviousSideEffects(prevMem))
              .filter(n -> !visited.contains(n))
              .forEach(toVisit::add);
        }

        if (toVisit.size() > Math.max(MAX_NUMBER_OF_SYNC_PREDS, sideEffect.getPredCount())) {
          // This is a conservative default, to speed up compilation time and space.
          return NodeUtils.getPreviousSideEffects(sideEffect.getPred(0));
        }
      }
      return ret;
    }

    private Set<IndirectAccess> aliasClass(Node node) {
      switch (node.getOpCode()) {
        case iro_Load:
        case iro_Store:
          Node ptr = node.getPred(1);
          return getPointsTo(ptr);
        case iro_Call:
          return getPointsTo(node);
        case iro_Start:
          // Clearly, we can't move stuff before the Start node. But there is no way
          // to state this just by its alias class.
          throw new UnsupportedOperationException("The alias class of Start can't be computed.");
        case iro_Phi:
          // Similarly, dominance is a bitch. Don't even try to move beyond Phis.
          // If we really need that last bit of precision: MEMO: we'd have to make a new
          // phi and start tracing again from the preds. Not worth it though if no transformation
          // makes use of that infomration.
          LOGGER.warn("The alias class of a Phi shouldn't be computed, as we never move beyond.");
          return getPointsTo(node);
        default:
          // I wonder what other side-effects we might hit...
          // Div/Mod are never reachable.
          throw new UnsupportedOperationException("Can't handle side effect " + node);
      }
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

      Proj projOnSideEffect = NodeUtils.getMemProjSuccessor(sideEffect);

      // We have to fix up all usages of the Proj by inserting a Sync node.
      // Redundant Sync nodes could be removed later, similar to Phi nodes.
      List<Edge> usages = Lists.newArrayList(BackEdges.getOuts(projOnSideEffect));
      Sync sync =
          (Sync) graph.newSync(projOnSideEffect.getBlock(), new Node[] {projOnSideEffect, oldMem});
      for (Edge be : usages) {
        be.node.setPred(be.pos, sync);
      }

      // We might be able to merge the newly created Sync node when it only has a single Sync usage.
      removeSyncOnSync(sync);
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
              .toArray(Node[]::new);

      Node newSync = graph.newSync(successorSync.getBlock(), newPreds);
      Graph.exchange(successorSync, newSync);
    }

    private boolean mayAlias(Node ptr, Set<IndirectAccess> otherAliasClass) {
      for (IndirectAccess ref : getPointsTo(ptr)) {
        if (ref.offset == UNKNOWN_OFFSET) {
          // This is bad asymptotic wise, since we don't have an index for base and type.
          boolean mayPotentiallyAlias =
              !seq(otherAliasClass)
                  .filter(
                      ia -> ia.base.equals(ref.base) && ia.pointedToType.equals(ref.pointedToType))
                  .isEmpty();
          if (mayPotentiallyAlias) {
            return true;
          }
        } else {
          // This is much better.
          IndirectAccess refAtAnyOffset =
              new IndirectAccess(ref.base, ref.pointedToType, UNKNOWN_OFFSET);
          boolean mayPotentiallyAlias =
              otherAliasClass.contains(ref) || otherAliasClass.contains(refAtAnyOffset);
          if (mayPotentiallyAlias) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean isLoadOrStore(Node node) {
      return node.getOpCode() == iro_Load || node.getOpCode() == iro_Store;
    }
  }

  private static class TooComplexError extends RuntimeException {}
}
