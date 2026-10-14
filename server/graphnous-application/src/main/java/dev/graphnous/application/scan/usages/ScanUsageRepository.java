package dev.graphnous.application.scan.usages;

import dev.graphnous.domain.scan.Scan;

import java.util.Optional;

/**
 * Reads what uses a node from the graph of its scan.
 */
public interface ScanUsageRepository {

    /**
     * What uses the node, by file and line. A class is used where it is
     * instantiated, named as a type, extended or implemented, and where
     * its methods are called and its fields read or written. What an
     * enhancer added, such as a component, is used where the function or
     * class it is of is.
     * <p>
     * Inheritance is followed: a method is also used where the method of
     * its name of a class above or below its own is, as a call of an
     * interface's method may run any that implements it, and a change to
     * it is one to all of them; the methods below that override it are
     * usages themselves. A class is also used by the classes that extend
     * or implement the ones that extend or implement it. Methods are told
     * apart by name only, so overloads count as one.
     * <p>
     * With a depth above 1, also what calls or instantiates the methods
     * and classes that use it, that many steps out.
     *
     * @param limit at most this many usages
     * @return empty when the scan has no such node
     */
    Optional<ScanUsages.Found> findUsages(Scan.ScanId scanId, String nodeId, int depth, int limit);

}
