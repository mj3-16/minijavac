package minijava.ir.utils;

import firm.Graph;
import firm.MethodType;

/** Information about a method entity in a more accessible object than the Graph object */
public class MethodInformation {

  public final Graph graph;
  public final MethodType type;
  public final int paramNumber;
  public final String name;
  public final String ldName;

  public MethodInformation(Graph graph) {
    this.graph = graph;
    this.type = (MethodType) graph.getEntity().getType();
    this.paramNumber = type.getNParams();
    this.name = graph.getEntity().getIdent().toString();
    this.ldName = graph.getEntity().getLdName();
  }
}
