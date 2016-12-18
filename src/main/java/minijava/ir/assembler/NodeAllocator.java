package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.Node;
import java.util.List;
import minijava.ir.assembler.instructions.Argument;
import minijava.ir.assembler.location.Location;

/**
 * Chooses the location for each node
 *
 * <p>Register allocators should implement this interface
 */
public interface NodeAllocator {

  /**
   * Process the given graph and initialize internal data structures. This method has to be called
   * before any of the other methods.
   *
   * @param graph given graph
   */
  void process(Graph graph);

  /**
   * Returns the appropriate location (stack or register) for a given expression node
   *
   * @param node expression node (no phi node)
   * @return node location
   */
  Location getLocation(Node node);

  /** Returns the locations of the arguments of the given expression nodes. */
  List<Argument> getArguments(Node node);

  /** Returns the location of the result of the given expression node */
  Location getResultLocation(Node node);

  /** Returns the size of the activation record in bytes. */
  int getActivationRecordSize();

  Argument getAsArgument(Node node);

  String getActivationRecordInfo();

  Location createNewTemporaryVariable();
}
