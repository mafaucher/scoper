package clac.creole.scope;

import java.util.Comparator;
import gate.Annotation;

public class AnnotationSpanComparator
        implements Comparator<Annotation> {
    @Override
    public int compare(Annotation a1, Annotation a2) {
        long a1Span = getSpan(a1);
        long a2Span = getSpan(a2);
        if (a1Span == a2Span) return 0;
        else return a1Span < a2Span ? -1 : 1;
    }

    private long getSpan(Annotation a) {
        return a.getEndNode().getOffset()
             - a.getStartNode().getOffset();
    }
}
