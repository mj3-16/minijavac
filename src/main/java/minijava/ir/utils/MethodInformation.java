package minijava.ir.utils;

import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.nodes.Address;
import firm.nodes.Call;

/** Information about a method entity in a more accessible object than the Graph object */
public class MethodInformation {

  public final MethodType type;
  public final int paramNumber;
  public final String name;
  public final String ldName;
  public final boolean hasReturnValue;

  public MethodInformation(Entity entity) {
    this.type = (MethodType) entity.getType();
    this.paramNumber = type.getNParams();
    this.name = entity.getIdent().toString();
    this.ldName = entity.getLdName();
    this.hasReturnValue = type.getNRess() > 0;
  }

  public MethodInformation(Call node) {
    this(((Address) node.getPred(1)).getEntity());
  }

  public MethodInformation(Graph graph) {
    this(graph.getEntity());
  }

  @Override
  public String toString() {
    return ldName;
  }
}
