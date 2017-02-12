package minijava.ir.utils;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.Relation;
import java.util.function.Supplier;
import minijava.ir.assembler.operands.OperandWidth;

public class FirmUtils {

  public static <T> T withBackEdges(Graph graph, Supplier<T> body) {
    boolean responsible = !BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.enable(graph);
    }
    try {
      return body.get();
    } finally {
      if (responsible) {
        BackEdges.disable(graph);
      }
    }
  }

  public static void withBackEdges(Graph graph, Runnable body) {
    boolean responsible = !BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.enable(graph);
    }
    try {
      body.run();
    } finally {
      if (responsible) {
        BackEdges.disable(graph);
      }
    }
  }

  public static <T> T withoutBackEdges(Graph graph, Supplier<T> body) {
    boolean responsible = BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.disable(graph);
    }
    try {
      return body.get();
    } finally {
      if (responsible) {
        BackEdges.enable(graph);
      }
    }
  }

  public static void withoutBackEdges(Graph graph, Runnable body) {
    boolean responsible = BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.disable(graph);
    }
    try {
      body.run();
    } finally {
      if (responsible) {
        BackEdges.enable(graph);
      }
    }
  }

  public static OperandWidth modeToWidth(Mode mode) {
    switch (mode.getSizeBytes()) {
      case 1:
        return OperandWidth.Byte;
      case 4:
        return OperandWidth.Long;
      case 8:
        return OperandWidth.Quad;
    }
    if (mode.isReference()) {
      return OperandWidth.Quad;
    } else if (mode.equals(Mode.getb())) {
      return OperandWidth.Byte;
    }
    throw new RuntimeException(mode.toString());
  }

  public static String relationToInstructionSuffix(Relation relation) {
    switch (relation) {
      case Greater:
        return "g";
      case GreaterEqual:
        return "ge";
      case Less:
        return "l";
      case LessEqual:
        return "le";
      case Equal:
        return "e";
      case LessGreater:
        return "ne";
      default:
        throw new RuntimeException(relation.toString());
    }
  }
}
