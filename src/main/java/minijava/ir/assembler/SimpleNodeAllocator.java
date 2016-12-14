package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.Node;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import minijava.ir.assembler.location.Location;
import minijava.ir.assembler.location.Register;

/**
 * Simple stack based node allocator
 *
 * <p>TODO: doesn't currently work and exists only for basic tests
 */
public class SimpleNodeAllocator implements NodeAllocator {
  @Override
  public void process(Graph graph) {}

  @Override
  public Location getLocation(Node node) {
    return Register.RETURN_REGISTER;
  }

  @Override
  public List<Location> getArgumentLocations(Node node) {
    return Arrays.asList(new Location[] {Register.RETURN_REGISTER});
  }

  @Override
  public Optional<Location> getResultLocation(Node node) {
    return Optional.of(Register.RETURN_REGISTER);
  }

  @Override
  public int getActivationRecordSize() {
    return 0;
  }
}
