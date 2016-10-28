package minijava.util;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Optional;

/**
 * An @Iterator@ decorator which enables look ahead into iterated elements.
 */
public class LookAheadIterator<T> implements Iterator<T> {

    private final Iterator<T> it;
    private final ArrayDeque<T> nextElements = new ArrayDeque<>(16);
    private boolean isBeforeFirstElement = true;

    public LookAheadIterator(Iterator<T> it) {
        this.it = it;
    }

    /**
     * Looks ahead into the stream of elements, tries to return the element as the result of the n-th call to @next()@,
     * if there is one, or @Optional.empty()@ otherwise.
     *
     * To get a feel for how this works: In the call sequence @lookAhead(1)@, @next()@, @lookAhead(0)@ on an underlying
     * non-empty iterable, all calls return the same element (which is the first element of the stream!).
     *
     * @lookAhead(0)@ is always the 'current' element, if any. On a non-empty, fresh iterable, @lookAhead(0)@ will
     * be @Optional.empty()@, because we start 'before' the first element in the stream, respecting @Iterable@ semantics.
     * @lookAhead(1)@ will point to the first element in that case.
     *
     * To fullfill calls to @lookAhead(n)@ with n greater than 1, it will call @next@ on the underlying iterable as
     * needed. A call to @lookAhead(0)@ is guaranteed not to call @next@.
     *
     * @param n non-negative integer, denoting the number of elements to look ahead.
     * @return The element which will be the return value after @n-1@ calls to @next@.
     */
    public Optional<T> lookAhead(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n was negative");
        }

        if (isBeforeFirstElement) {
            // This can only happen if next() hasn't been called yet.
            // Otherwise index 0 in nextElements would be filled with the current element.
            // In this case the current element (index 0) doesn't exist!
            if (n == 0) {
                return Optional.empty();
            }
            // E.g.: When we access index 1, we really want to access index 0 in the queue, act as if we already
            // called next() once.
            n--;
        }

        while (nextElements.size() <= n) {
            if (!it.hasNext()) {
                return Optional.empty();
            }

            nextElements.addLast(it.next());
        }

        // Why on earth would a BCL implement a Deque without random access?!! $§$%*
        Iterator<T> q = nextElements.iterator();
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
        return lookAhead(1).isPresent();
    }

    @Override
    public T next() {
        Optional<T> ret = lookAhead(1);

        if (!ret.isPresent()) {
            // propagate to decorated iterator and let it figure out how to handle this
            return it.next();
        }

        // Some handling of weirdness because we can't really store lookAhead(0) in the queue for the case
        // when next() hasn't been called before.
        if (isBeforeFirstElement) {
            isBeforeFirstElement = false;
        } else {
            nextElements.removeFirst();
        }
        return ret.get();
    }
}
