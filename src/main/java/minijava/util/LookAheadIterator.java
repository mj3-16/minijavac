package minijava.util;

import com.sun.jmx.remote.internal.ArrayQueue;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;
import java.util.Queue;

/**
 * An @Iterator@ decorator which enables look ahead into iterated elements.
 */
public class LookAheadIterator<T> implements Iterator<T> {

    private final Iterator<T> it;
    private final ArrayDeque<T> nextElements = new ArrayDeque<>(16);

    public LookAheadIterator(Iterator<T> it) {
        this.it = it;
    }

    /**
     * Looks ahead into the stream of elements. @lookAhead(0)@ will return the current element and throw if there is none.
     * To get a feel for how this works: In the call sequence @lookAhead(1)@, @next()@, @lookAhead(0)@ on an underlying
     * iterable of size greater than 1, all calls return the same element.
     *
     * If called before @next()@ has been called at least once, this will throw. This is because there is no current
     * element in the semantics of iterators.
     *
     * To fullfill calls to @lookAhead(n)@ with n greater than 1, it will call @next@ on the underlying iterable as
     * needed. A call to @lookAhead(0)@ is guaranteed not to call @next@.
     *
     * @param n The number of elements to look ahead.
     * @return The element which will be the return value after @n-1@ calls to @next@.
     */
    public Optional<T> lookAhead(int n) {
        if (nextElements.isEmpty() & n == 0) {
            // This can only happen if next() hasn't been called yet.
            // Otherwise index 0 in nextElements would be filled with the current element.
            // In this case the current element (index 0) doesn't exist!
            return Optional.empty();
        }

        while (nextElements.size() <= n) {
            if (!it.hasNext()) {
                return Optional.empty();
            }

            nextElements.add(it.next());
        }

        // Why on earth would a BCL implement a Deque without random access?!! $§$%*
        Iterator<T> q = nextElements.descendingIterator();
        // q will be at least n+1 elements long, so no need for checking hasNext()
        while (true) {
            T cur = q.next();
            if (n-- == 0) {
                return Optional.of(cur);
            }
        }
    }

    @Override
    public boolean hasNext() {
        return lookAhead(0).isPresent();
    }

    @Override
    public T next() {
        Optional<T> ret = lookAhead(1);

        if (!ret.isPresent()) {
            // propagate to decorated iterator and let it figure out how to handle this
            return it.next();
        }
        if (ret.isPresent()) {
            nextElements.removeFirst();
            return ret.get();
        }
    }
}
