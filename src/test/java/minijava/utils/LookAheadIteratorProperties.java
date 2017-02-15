package minijava.utils;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class LookAheadIteratorProperties {

  @Property
  public void lookAheadIsLikeRandomAccess(@Size(min = 0, max = 1000) List<Integer> list) {
    LookAheadIterator<Integer> it = new LookAheadIterator<>(list.iterator());

    List<Integer> actual = new ArrayList<>(list.size());

    for (int i = 0; i < list.size(); ++i) {
      actual.add(it.lookAhead(i + 1).get()); // +1 because we start before the first element
    }

    Assert.assertFalse(it.lookAhead(list.size() + 1).isPresent());

    // System.out.println("expected: " + Arrays.toString(list.toArray()));
    // System.out.println("actual:   " + Arrays.toString(actual.toArray()));
    Assert.assertArrayEquals(list.toArray(), actual.toArray());
  }

  @Property
  public void decoratedIterableUnchanged(@Size(min = 0, max = 1000) List<Integer> lookAheads) {
    // We'll use lookAheads both as the iterable we want decorate and as lookAhead numbers supply
    List<Integer> actual = new ArrayList<>(lookAheads.size());

    LookAheadIterator<Integer> it = new LookAheadIterator<>(lookAheads.iterator());
    int cur = -1; // index that it points to
    for (Integer n : lookAheads) {
      // even if we sprinkle calls to lookAhead,
      int la = n & 3;
      Optional<Integer> i = it.lookAhead(la);
      int accessedIndex = cur + la;
      // we should get the same sequence of elements out.
      actual.add(it.next());
      cur++;
      // Also: We now for certain what i above should be.
      if (accessedIndex < 0 || accessedIndex >= lookAheads.size()) {
        Assert.assertFalse(i.isPresent());
      } else {
        Assert.assertEquals(lookAheads.get(accessedIndex), i.get());
      }
    }

    Assert.assertFalse(it.hasNext());

    // System.out.println("expected: " + Arrays.toString(lookAheads.toArray()));
    // System.out.println("actual:   " + Arrays.toString(actual.toArray()));
    Assert.assertArrayEquals(lookAheads.toArray(), actual.toArray());
  }
}
