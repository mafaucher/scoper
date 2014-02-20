/*
 *  Scoper.java
 *
 * Copyright (c) 2000-2012, The University of Sheffield.
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free
 * software, licenced under the GNU Library General Public License,
 * Version 3, 29 June 2007.
 *
 * A copy of this licence is included in the distribution in the file
 * licence.html, and is also available at http://gate.ac.uk/gate/licence.html.
 *
 *  ma_fauch, 19/1/2014
 *
 * For details on the configuration options, see the user guide:
 * http://gate.ac.uk/cgi-bin/userguide/sec:creole-model:config
 */
package clac.creole.scope;

import gate.stanford.Parser;

import java.io.*;
import java.util.*;

import gate.*;
import gate.creole.*;
import gate.creole.metadata.*;
import gate.util.*;

/**
 * This class is the implementation of the resource SCOPEEXPORTER.
 */
@CreoleResource(name = "Scoper",
        comment = "Annotates Scope of Adjectives from a Trigger List")
public class Scoper extends AbstractLanguageAnalyser
        implements ProcessingResource {

    // Parameters
    protected String inputAnnotationSetName;
    protected String outputAnnotationSetName;
    protected String sentenceAnnName;
    protected String triggerAnnName;

    // Private attributes
    private AnnotationSet inAnns;
    private AnnotationSet outAnns;

    /// CONSTANTS ///

    private static final boolean DEBUG = true;

    // Trigger
    public static final String TRIGGER_ANNOTATION_TYPE    = "Trigger";
    public static final String TRIGGER_SOURCE_FEATURE     = "source";
    public static final String TRIGGER_POLARITY_FEATURE   = "priorpolarity";
    public static final String TRIGGER_SCORE_FEATURE      = "sentimentScore"; //TODO
    public static final String TRIGGER_SCOPEID_FEATURE    = "scopeID"; //TODO

    // Scope
    public static final String SCOPE_ANNOTATION_TYPE      = "Scope";
    public static final String SCOPE_HEURISTIC_FEATURE    = "heuristic";
    public static final String SCOPE_TYPE_FEATURE         = "type"; //TODO
    public static final String SCOPE_TRIGGERID_FEATURE    = "triggerID";

    // Token
    public static final String TOKEN_ANNOTATION_TYPE      = ANNIEConstants.TOKEN_ANNOTATION_TYPE;
    public static final String TOKEN_CATEGORY_FEATURE     = ANNIEConstants.TOKEN_CATEGORY_FEATURE_NAME;
    public static final String TOKEN_CATEGORY_ADJECTIVE   = "JJ"; // Matches JJ.* (see filterPos)

    // Phrase (SyntaxTreeNode)
    public static final String PHRASE_ANNOTATION_TYPE     = Parser.PHRASE_ANNOTATION_TYPE;
    public static final String PHRASE_CATEGORY_FEATURE    = Parser.PHRASE_CAT_FEATURE;

    // Dependency
    public static final String DEPENDENCY_ANNOTATION_TYPE = Parser.DEPENDENCY_ANNOTATION_TYPE;
    public static final String DEPENDENCY_ARG_FEATURE     = Parser.DEPENDENCY_ARG_FEATURE;
    public static final String DEPENDENCY_LABEL_FEATURE   = Parser.DEPENDENCY_LABEL_FEATURE;

    public static final String[] MOD_DEPENDENCIES =
            { "amod", "rcmod", "quantmod", "infmod", "partmod", "nn" };
    // Unsure: advcl, appos, mark, num, number, discourse, advmod,
    // npadvmod, mwe, det, predet, preconj, poss, possessive, prep, prt, goeswith

    public static final String[] COP_DEPENDENCIES = { "cop", "auxpass" };
    public static final String[] SUBJ_DEPENDENCIES = { "nsubj" };

    /** Execute PR over a single document */
    public void execute() throws ExecutionException {

        inAnns = document.getAnnotations(inputAnnotationSetName);
        outAnns = document.getAnnotations(outputAnnotationSetName);

        if (document == null) {
            throw new GateRuntimeException("No document to process!");
        }

        // Attempt to find scope for all triggers
        AnnotationSet triggers = inAnns.get(triggerAnnName);
        for (Annotation trigger : triggers) {
            Annotation token = getToken(trigger);
            if (token != null) {
                List<ScoperDependency> deps = getDependencies(trigger);
                modScope(trigger, deps);
                copsubjScope(trigger, deps);
                //nomScope(trigger);
            }
        }
    }

    /** Initialize the resource. */
    public Resource init() throws ResourceInstantiationException {
        super.init();
        return this;
    }

    @Override
    public void reInit() throws ResourceInstantiationException {
        init();
    }

    /** Annotate the subject of an adjective with a copula.
     * e.g. cop(X) ^ nsubj(Y) ^ scope(Y)
     */
    private void copsubjScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Filter all but adjectives
        Annotation token = getToken(trigger);
        if (!filterPos(token, TOKEN_CATEGORY_ADJECTIVE)) return;
        // Check if there is a copula
        List<ScoperDependency> copDeps =
                filterDependencies(dependencies, COP_DEPENDENCIES);
        if (copDeps == null || copDeps.isEmpty()) return;
        // Annotate the subject, if exists
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, SUBJ_DEPENDENCIES);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        Annotation scope = getPhrase(scopeAnns);
        annotateScope(scope, trigger, "copsubj");
    }

    /** Annotate the scope of a modifier.
     * e.g. *amod(X) ^ scope(X)
     */
    private void modScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Annotate the governor of mod dependencies, if exists
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, MOD_DEPENDENCIES, false);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        Annotation scope = getPhrase(scopeAnns);
        annotateScope(scope, trigger, scopeDeps.get(0).getType());
    }

    /** Filter Dependencies by type. */
    public static List<ScoperDependency> filterDependencies(
            List<ScoperDependency> dependencies, String[] types, boolean gov) {
        List<ScoperDependency> results = new ArrayList<ScoperDependency>();
        for (ScoperDependency dependency : dependencies) {
            for (int i = 0; i < types.length; i++) {
                if ( dependency.getType().equals(types[i])
                  && dependency.isGov() == gov ) {
                    results.add(dependency);
                }
            }
        }
        return results;
    }
    public static List<ScoperDependency> filterDependencies(
            List<ScoperDependency> dependencies, String[] types) {
        return filterDependencies(dependencies, types, true);
    }

    /** Convert scoper dependency targets into Annotations */
    public static List<Annotation> targetsToAnns(List<ScoperDependency> deps,
            AnnotationSet alist) {
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        for (ScoperDependency dep : deps) {
            anns.add(alist.get(dep.getTargetId()));
        }
        return anns;
    }
    private List<Annotation> targetsToAnns(List<ScoperDependency> deps) {
        return targetsToAnns(deps, inAnns);
    }

    /** Find the smallest phrase dominating a list of tokens. */
    public static Annotation getPhrase(List<Annotation> tokens,
            AnnotationSet alist) {
        // Get the STN path for each token
        List<PriorityQueue<Annotation>> paths = new ArrayList<PriorityQueue<Annotation>>();
        for (Annotation token : tokens) {
            paths.add(getPath(token, PHRASE_ANNOTATION_TYPE, alist));
        }
        // Find the smallest STN which is common to all paths
        PriorityQueue<Annotation> path1 = paths.get(0);
        while (path1.size() != 0) {
            Annotation node = path1.remove();
            boolean commonNode = true;
            for (int i=1; i<paths.size(); i++) {
                if (!paths.get(i).contains(node)) {
                    commonNode = false;
                    break;
                }
            }
            if (commonNode) return node;
        }
        System.err.println("Error: No common node");
        return null;
    }
    private Annotation getPhrase(List<Annotation> tokens) {
        return getPhrase(tokens, inAnns);
    }

    /** Standard function for creating scope annotation and features */
    private void annotateScope(Long startOffset, Long endOffset,
                               Annotation trigger, String heuristic)
                        throws InvalidOffsetException {
        // If scope already exists issue a warning
        Annotation scope = getScope(trigger);
        if (scope != null) {
            String newScope = this.getDocument().getContent().getContent(
                    startOffset, endOffset).toString();
            if (DEBUG) {
                System.err.println("Warning: Multiple scopes detected for trigger:");
                System.err.println("    OLD: "+getAnnotationText(trigger)+" -> ("
                                  +scope.getFeatures().get(SCOPE_HEURISTIC_FEATURE)+") "
                                  +getAnnotationText(scope));
                System.err.println("    NEW: "+getAnnotationText(trigger)+" -> ("
                                  +heuristic+") "+newScope);
            }
        // Otherwise annotate scope
        } else {
            FeatureMap fm = gate.Factory.newFeatureMap();
            fm.put(SCOPE_TRIGGERID_FEATURE, trigger.getId());
            fm.put(SCOPE_HEURISTIC_FEATURE, heuristic);
            outAnns.add(startOffset, endOffset, SCOPE_ANNOTATION_TYPE, fm);
        }
    }
    /** Annotates scope from the offsets of an existing annotation */
    private void annotateScope(Annotation scope, Annotation trigger,
                               String heuristic) {
        try {
            Long startOffset = scope.getStartNode().getOffset();
            Long endOffset   = scope.getEndNode().getOffset();
            annotateScope(startOffset, endOffset, trigger, heuristic);
        } catch (InvalidOffsetException e) {
            System.err.println("Error: invalid scope offsets.");
            e.printStackTrace();
        }
    }

    /** Returns true iff token's POS matches given POS.
     * @param   token  Annotation with a part-of-speech category.
     * @param   pos    The desired part-of-speech pattern.
     * @param   strict (optional) whether to match the 'pos' pattern exactly,
     *                 default is false.
     * @param   cat    (optional) the feature name for the category,
     *                 default is "category".
     * @return  Whether the annotation has the right part-of-speech
     * */
    private boolean filterPos(Annotation token, String pos) {
        return filterPos(token, pos, false, "category");
    }
    private boolean filterPos(Annotation token, String pos, String cat) {
        return filterPos(token, pos, false, cat);
    }
    private boolean filterPos(Annotation token, String pos, boolean strict) {
        return filterPos(token, pos, strict, "category");
    }
    private boolean filterPos(Annotation token, String pos,
                              boolean strict, String cat) {
        String tokenPos = token.getFeatures().get(cat).toString();
        if (strict) return tokenPos.equals(pos);
        return ( tokenPos.length() >= pos.length() &&
                 tokenPos.substring(0, pos.length()).equals(pos) );
    }

    /** Find the scope which corresponds to this trigger or token */
    // TODO: Make triggers point to their scope to speed this up (change negator format)
    public static Annotation getScope(Annotation trigger, AnnotationSet alist) {
        Annotation root = getStn(trigger, "ROOT", alist);
        if (root == null) {
            System.err.println("Error: No ROOT node found.");
            return null;
        }
        // Find scope who's triggerId corresponds to this trigger
        AnnotationSet sentenceScopes = alist.get(SCOPE_ANNOTATION_TYPE,
                                           root.getStartNode().getOffset(),
                                           root.getEndNode().getOffset());
        for (Annotation scope : sentenceScopes) {
            Annotation scopeTrigger = alist.get(Integer.parseInt(
                    scope.getFeatures().get(SCOPE_TRIGGERID_FEATURE).toString()));
            if ( scope.getFeatures().containsKey(SCOPE_TRIGGERID_FEATURE) &&
                    trigger.coextensive(scopeTrigger) ) {
                return scope;
            }
        }
        // No scope found
        return null;
    }
    private Annotation getScope(Annotation trigger) {
        return getScope(trigger, inAnns);
    }

    /** Get a SyntaxTreeNode of a certain category including an annotation */
    public static Annotation getStn(Annotation ann, String cat, AnnotationSet alist) {
        for (Annotation a : getPath(ann, PHRASE_ANNOTATION_TYPE, alist)) {
            if (a.getFeatures().get(PHRASE_CATEGORY_FEATURE).equals(cat)) {
                return a;
            }
        }
        return null;
    }
    private Annotation getStn(Annotation ann, String cat) {
        return getStn(ann, cat, inAnns);
    }

    /** Starting from a token, get a sorted list of embedded typed Annotations */
    public static PriorityQueue<Annotation> getPath(Annotation token,
            String type, AnnotationSet alist) {
        Comparator<Annotation> comparator = new AnnotationSpanComparator();
        PriorityQueue<Annotation> queue =
                new PriorityQueue<Annotation>(10, comparator);
        for (Annotation a : getOverlaping(token, alist.get(type))) {
            queue.add(a);
        }
        return queue;
    }
    private PriorityQueue<Annotation> getPath(Annotation token, String type) {
        return getPath(token, type, inAnns);
    }

    /** Get the dependencies for this token/trigger */
    public static List<ScoperDependency> getDependencies(Annotation trigger,
            AnnotationSet alist) {
        ArrayList<ScoperDependency> depList = new ArrayList<ScoperDependency>();
        Annotation token = getToken(trigger, alist);
        for (Annotation dep : getOverlaping(trigger, alist.get(DEPENDENCY_ANNOTATION_TYPE))) {
            String type = dep.getFeatures().get(DEPENDENCY_LABEL_FEATURE).toString().trim();
            String ids  = dep.getFeatures().get(DEPENDENCY_ARG_FEATURE).toString().trim();
            ids = ids.substring(1, ids.length()-1);
            String[] args = ids.split("\\,");
            int depId = Integer.parseInt(args[1].trim());
            int govId = Integer.parseInt(args[0].trim());
            if (token.getId() == govId) {
                depList.add(new ScoperDependency(type, depId, true));
            }
            else if (token.getId() == depId) {
                depList.add(new ScoperDependency(type, govId, false));
            }
        }
        return depList;
    }
    private List<ScoperDependency> getDependencies(Annotation trigger) {
        return getDependencies(trigger, inAnns);
    }

    /** Get the Sentence for this token/trigger */
    public static Annotation getSentence(Annotation token, AnnotationSet alist,
            String sentenceType) {
        for (Annotation a : getOverlaping(token, alist.get(sentenceType))) {
            return a;
        }
        return null;
    }
    private Annotation getSentence(Annotation token) {
        return getSentence(token, inAnns, sentenceAnnName);
    }

    /** Find the token which corresponds to this trigger */
    public static Annotation getToken(Annotation trigger, AnnotationSet alist) {
        Annotation token = getCoextensive(trigger, alist.get(TOKEN_ANNOTATION_TYPE));
        if (token == null) {
            if (DEBUG) System.err.println("Warning: no token for trigger");
        }
        return token;
    }
    private Annotation getToken(Annotation trigger) {
        return getToken(trigger, inAnns);
    }

    /** Find the first coextensive annotation in a list or return null */
    public static Annotation getCoextensive(Annotation ann, AnnotationSet alist) {
        for (Annotation a : getOverlaping(ann, alist)) {
            if (ann.coextensive(a)) {
                return a;
            }
        }
        return null;
    }

    /** Find the overlaping AnnotationSet */
    public static AnnotationSet getOverlaping(Annotation ann, AnnotationSet alist) {
        return alist.get(ann.getStartNode().getOffset(),
                         ann.getEndNode().getOffset());
    }

    /** Get the text of an annotation */
    public static gate.DocumentContent getAnnotationText(Annotation annotation,
            gate.Document document) {
        try {
            return document.getContent().getContent(
                    annotation.getStartNode().getOffset(),
                    annotation.getEndNode().getOffset());
        }
        catch(gate.util.InvalidOffsetException e) {
            System.err.println("Error: Invalid Annotation Offsets");
            return null;
        }
    }
    private gate.DocumentContent getAnnotationText(Annotation annotation) {
        return getAnnotationText(annotation, this.getDocument());
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "The input annotation set name")
    public void setInputAnnotationSetName(String inputAnnotationSetName) {
        this.inputAnnotationSetName = inputAnnotationSetName;
        this.inAnns = document.getAnnotations(inputAnnotationSetName);
    }

    public String getInputAnnotationSetName() {
        return this.inputAnnotationSetName;
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "The output annotation set name")
    public void setOutputAnnotationSetName(String outputAnnotationSetName) {
        this.outputAnnotationSetName = outputAnnotationSetName;
        this.outAnns = document.getAnnotations(outputAnnotationSetName);
    }

    public String getOutputAnnotationSetName() {
        return this.outputAnnotationSetName;
    }

    @RunTime
    @CreoleParameter(comment = "The annotation name for sentences",
                     defaultValue = ANNIEConstants.SENTENCE_ANNOTATION_TYPE)
    public void setSentenceAnnName(String sentenceAnnName) {
        this.sentenceAnnName = sentenceAnnName;
    }

    public String getSentenceAnnName() {
        return this.sentenceAnnName;
    }

    @RunTime
    @CreoleParameter(comment = "The annotation name for triggers",
                     defaultValue = TRIGGER_ANNOTATION_TYPE)
    public void setTriggerAnnName(String triggerAnnName) {
        this.triggerAnnName = triggerAnnName;
    }

    public String getTriggerAnnName() {
        return this.triggerAnnName;
    }

    @RunTime
    @CreoleParameter(comment = "The document to be processed")
    public void setDocument(gate.Document document) {
        this.document = document;
    }

    public gate.Document getDocument() {
        return this.document;
    }

}
