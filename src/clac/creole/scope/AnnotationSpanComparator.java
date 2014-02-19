package clac.creole.scope;

import java.util.Comparator;
import gate.Annotation;

/** Comparator used to order annotations by span.
 * a1 < a2 iff the span of a1 is shorter than the span of a2
 * WARNING: When used for SyntaxTreeNode, nodes covering the same
 * span (e.g. ROOT and S) will have an equal value.
 * TODO: It would be preferable to look at the consists list
 * for these cases, but this wouldn't work for scopes
 * */
public class AnnotationSpanComparator
        implements Comparator<Annotation> {
    @Override
    public int compare(Annotation a1, Annotation a2) {
        if (span(a1) == span(a2)) return 0;
        else return span(a1) < span(a2) ? -1 : 1;
    }

    private long span(Annotation a) {
        return a.getEndNode().getOffset()
             - a.getStartNode().getOffset();
    }
}
