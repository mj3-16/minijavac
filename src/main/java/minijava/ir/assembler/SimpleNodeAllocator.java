package minijava.ir.assembler;

import static minijava.ir.utils.FirmUtils.modeToWidth;

import firm.*;
import firm.nodes.*;
import java.util.*;
import minijava.ir.Types;
import minijava.ir.assembler.instructions.Argument;
import minijava.ir.assembler.instructions.ConstArgument;
import minijava.ir.assembler.location.*;
import minijava.ir.utils.MethodInformation;
import org.jetbrains.annotations.Nullable;

/**
 * Basic allocator that allocates nodes into {@link NodeLocation} objects.
 *
 * <p>These {@link NodeLocation}s should be replaced later by a register allocator
 */
public class SimpleNodeAllocator {

  private int currentLocationId;
  private Graph graph;
  private MethodInformation info;
  public final List<ParamLocation> paramLocations;
  private Map<Node, NodeLocation> assignedLocations;

  public SimpleNodeAllocator(Graph graph) {
    this.currentLocationId = 0;
    this.graph = graph;
    this.info = new MethodInformation(graph);
    this.assignedLocations = new HashMap<>();
    List<ParamLocation> paramLocations = new ArrayList<>();
    for (int i = 0; i < info.paramNumber; i++) {
      paramLocations.add(
          new ParamLocation(modeToWidth(info.type.getParamType(i).getMode()), genLocationId(), i));
    }
    this.paramLocations = Collections.unmodifiableList(paramLocations);
  }

  public Location getLocation(Node node) {
    return getLocation(node, null);
  }

  public Location getLocation(Node node, Mode mode) {
    if (node instanceof Conv) {
      // Conv nodes are only generated for div and mod instructions
      // this leads either to
      // [Mod] <- [Proj] <- [Conv]
      // where we return the location of the [Mov] node
      // or
      // [Arg node] <- [Conv] <- [Mod]
      // where we return the location of the [Arg node]
      Node operand = ((Conv) node).getOp();
      if (operand.getPredCount() > 0) {
        Node operandPred = operand.getPred(0);
        if (operandPred instanceof Mod || operandPred instanceof Div) {
          return getLocation(operandPred, Types.INT_TYPE.getMode());
        }
      }
      return getLocation(operand);
    } else if (node instanceof Proj) {
      Proj proj = (Proj) node;
      if (proj.getPred().equals(graph.getArgs())) {
        // the proj node points to a method argument
        int slot = proj.getNum();
        return paramLocations.get(slot);
      } else if (proj.getPred().getPredCount() > 0 && proj.getPred().getPred(0) instanceof Call) {
        // the proj node points to method result
        // the result always lays in the RAX register
        // [Call] <- [Tuple Proj] <- [Is Proj]
        return getNodeLocation(proj.getPred().getPred(0));
      } else if (node.getPred(0) instanceof Load || node.getPred(0) instanceof Store) {
        // [Proj p64] <- [Load|Store] <- [Proj res]
        return getLocation(node.getPred(0));
      }
    }
    if (node instanceof firm.nodes.Call) {
      return getNodeLocation(
          node,
          ((MethodType) (((Address) node.getPred(1)).getEntity().getType()))
              .getResType(0)
              .getMode());
    }
    if (node instanceof firm.nodes.Load) {
      return getNodeLocation(node, ((Load) node).getType().getMode());
    }
    return getNodeLocation(node, mode);
  }

  public List<Argument> getArguments(Node node) {
    List<Argument> args = new ArrayList<>();
    int start = 0;
    if (node instanceof Call) {
      start = 2;
    } else if (node instanceof Return || node instanceof Mod || node instanceof Div) {
      // they have memory dependencies
      start = 1;
    }
    for (int i = start; i < node.getPredCount(); i++) {
      args.add(getAsArgument(node.getPred(i)));
    }
    return args;
  }

  public Argument getAsArgument(Node node) {
    if (node instanceof Const) {
      TargetValue tarVal = ((Const) node).getTarval();
      if (tarVal.getMode().getSizeBytes() == 1) {
        return new ConstArgument(Register.Width.Byte, tarVal.asInt());
      } else if (tarVal.getMode().getSizeBytes() == 4) {
        return new ConstArgument(Register.Width.Long, tarVal.asLong());
      } else if (tarVal.getMode().getSizeBytes() == 8 || tarVal.getMode().isReference()) {
        return new ConstArgument(Register.Width.Quad, tarVal.asLong());
      } else if (tarVal.getMode().equals(Mode.getb())) {
        return new ConstArgument(Register.Width.Byte, tarVal.isNull() ? 0 : 1);
      } else {
        System.err.println(tarVal);
        System.err.println(tarVal.getMode());
        assert false;
      }
    }
    return getLocation(node);
  }

  public Location getResultLocation(Node node) {
    return getLocation(node);
  }

  private Location getNodeLocation(Node node) {
    return getNodeLocation(node, null);
  }

  private Location getNodeLocation(Node node, @Nullable Mode mode) {
    if (!assignedLocations.containsKey(node)) {
      if (mode == null) {
        mode = node.getMode();
      }
      assignedLocations.put(node, new NodeLocation(modeToWidth(mode), genLocationId(), node));
    }
    return assignedLocations.get(node);
  }

  public int genLocationId() {
    return currentLocationId++;
  }
}
