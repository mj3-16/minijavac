package minijava.ir.assembler;

public interface GNUAssemblerConvertible {

  /**
   * Returns the GNU assembler representation of this object TODO: Improve this method. Should it
   * use a shared StringBuffer?
   *
   * @return GNU assembler representation as string
   */
  String toGNUAssembler();
}
