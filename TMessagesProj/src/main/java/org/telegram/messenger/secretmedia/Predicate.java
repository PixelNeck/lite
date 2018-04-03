package org.telegram.messenger.secretmedia;

/**
 * Created by elanimus on 3/28/18.
 */

public interface Predicate<T> {

    /**
     * Evaluates an input.
     *
     * @param input The input to evaluate.
     * @return The evaluated result.
     */
    boolean evaluate(T input);

}
