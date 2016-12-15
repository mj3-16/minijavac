package minijava.ir.assembler.block;

import com.sun.jna.Platform;
import java.util.*;
import minijava.ir.NameMangler;
import minijava.ir.assembler.GNUAssemblerConvertible;
import org.jetbrains.annotations.NotNull;

/** List of segments that are combined to an assembler file */
public class AssemblerFile implements GNUAssemblerConvertible, Collection<Segment> {

  /** File name that helps debuggers. */
  private String fileName;

  private final List<Segment> segments;

  public AssemblerFile() {
    segments = new ArrayList<>();
  }

  public AssemblerFile(Segment... segments) {
    this();
    addAll(Arrays.asList(segments));
  }

  /** Sets the file name to help debuggers. */
  public AssemblerFile setFileName(String newFileName) {
    this.fileName = newFileName;
    return this;
  }

  @Override
  public int size() {
    return segments.size();
  }

  @Override
  public boolean isEmpty() {
    return segments.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return segments.contains(o);
  }

  @NotNull
  @Override
  public Iterator<Segment> iterator() {
    return segments.iterator();
  }

  @NotNull
  @Override
  public Object[] toArray() {
    return segments.toArray();
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    return segments.<T>toArray(a);
  }

  @Override
  public boolean add(Segment segment) {
    return segments.add(segment);
  }

  @Override
  public boolean remove(Object o) {
    return segments.remove(o);
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    return segments.contains(c);
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends Segment> c) {
    return segments.addAll(c);
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    return segments.removeAll(c);
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    return segments.retainAll(c);
  }

  @Override
  public void clear() {
    segments.clear();
  }

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    if (fileName != null) {
      builder.append(".file " + fileName + "\n");
    }
    builder.append(getGNUAssemblerFilePrologue());
    for (Segment segment : segments) {
      builder.append("\n\n").append(segment.toGNUAssembler());
    }
    return builder.toString();
  }

  private static String getGNUAssemblerFilePrologue() {
    String mainMethod = NameMangler.mangledMainMethodName();
    if (Platform.isMac()) {
      return "\t" + String.join("\n\t", ".p2align 4,0x90,15", ".globl " + mainMethod);
    } else {
      return "\t"
          + String.join(
              "\n\t",
              ".p2align 4,,15",
              ".globl " + mainMethod,
              ".type\t" + mainMethod + ", @function");
    }
  }
}
