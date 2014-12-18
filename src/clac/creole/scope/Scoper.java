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
 * This class is the implementation of the resource SCOPER.
 */
@CreoleResource(name = "Scoper",
        comment = "Annotates Scope of a Trigger List")
public class Scoper extends AbstractLanguageAnalyser
        implements ProcessingResource {

    // Parameters
    protected String inputAnnotationSetName;
    protected String outputAnnotationSetName;
    protected String sentenceAnnName;
    protected String triggerAnnName;
    protected boolean includeTrigger;
    protected boolean filterPredicates;
    protected boolean enableAdjScope;
    protected boolean enableNegatorScope;
    protected boolean enableNomScope;
    protected boolean enableGrammarScope;

    // Private attributes
    private AnnotationSet inAnns;
    private AnnotationSet outAnns;

    /// CONSTANTS ///

    public static final boolean DEBUG = true;

    // Trigger
    public static final String TRIGGER_ANNOTATION_TYPE      = "Trigger";
    public static final String TRIGGER_SOURCE_FEATURE       = "source";
    public static final String TRIGGER_TYPE_FEATURE         = "type";
    public static final String TRIGGER_MINORTYPE_FEATURE    = "minorType";
    public static final String TRIGGER_POLARITY_FEATURE     = "priorPolarity";
    public static final String TRIGGER_SCORE_FEATURE        = "sentimentScore"; // Unused

    public static final String TRIGGER_SCOPEID_FEATURE      = "scopeID";
    public static final String TRIGGER_SCOPESTRING_FEATURE  = "scopeString";
    public static final String TRIGGER_RSCOPEIDS_FEATURE    = "rScopeIDs";

    // Scope
    public static final String SCOPE_ANNOTATION_TYPE        = "Scope";
    public static final String SCOPE_HEURISTIC_FEATURE      = "heuristic";
    public static final String SCOPE_TRIGGERID_FEATURE      = "triggerID";
    public static final String SCOPE_TRIGGERSTRING_FEATURE  = "triggerString";
    public static final String[] SCOPE_INHERITED_FEATURES   =
            { TRIGGER_TYPE_FEATURE, TRIGGER_MINORTYPE_FEATURE,
              TRIGGER_POLARITY_FEATURE, TRIGGER_SCORE_FEATURE };

    // Token
    public static final String TOKEN_ANNOTATION_TYPE  = ANNIEConstants.TOKEN_ANNOTATION_TYPE;
    public static final String TOKEN_STRING_FEATURE   = ANNIEConstants.TOKEN_STRING_FEATURE_NAME;
    public static final String TOKEN_CATEGORY_FEATURE = ANNIEConstants.TOKEN_CATEGORY_FEATURE_NAME;
    public static final String TOKEN_CATEGORY_ADJ     = "JJ"; // Matches JJ.* (see filterPos)
    public static final String TOKEN_CATEGORY_NOUN    = "NN"; // Matches NN.* (see filterPos)
    public static final String TOKEN_CATEGORY_PREP    = "IN";
    public static final String TOKEN_CATEGORY_CONJ    = "CC";

    // Phrase (SyntaxTreeNode)
    public static final String PHRASE_ANNOTATION_TYPE  = Parser.PHRASE_ANNOTATION_TYPE;
    public static final String PHRASE_CATEGORY_FEATURE = Parser.PHRASE_CAT_FEATURE;
    public static final String PHRASE_CATEGORY_ROOT    = "ROOT";

    // Dependency
    public static final String DEPENDENCY_ANNOTATION_TYPE = Parser.DEPENDENCY_ANNOTATION_TYPE;
    public static final String DEPENDENCY_ARG_FEATURE     = Parser.DEPENDENCY_ARG_FEATURE;
    public static final String DEPENDENCY_LABEL_FEATURE   = Parser.DEPENDENCY_LABEL_FEATURE;

    public static final String[] CONJ_DEPENDENCIES     = { "conj" };
    public static final String[] PREP_OF_DEPENDENCIES  = { "prep_of" };
    public static final String[] PREP_DEPENDENCIES     = { "prep" };
    // TODO: prep or prepc ? e.g. prepc_without
    // currently matching all "starting with", but this may lead to false positives
    public static final String[] AUX_DEPENDENCIES      = { "aux" };
    // TODO: check additional aux dependencies:
    // cop, auxpass
    public static final String[] COP_DEPENDENCIES      = { "cop", "auxpass" };
    public static final String[] DET_DEPENDENCIES      = { "det" };
    public static final String[] NEG_DEPENDENCIES      = { "neg" };
    public static final String[] COMP_DEPENDENCIES     = { "dobj", "xcomp", "ccomp" };
    // TODO: check additional comp dependencies:
    // acomp, attr, complm, obj, iobj, pobj, mark, rel
    public static final String[] SUBJ_DEPENDENCIES     = { "nsubj", "nsubjpass" };
    // TODO: check additional subj dependencies
    // csubj, csubjpass
    public static final String[] MOD_DEPENDENCIES      =
            { "advmod", "amod", "infmod", "nn", "partmod", "quantmod" };
    // TODO: check if we should include the following list of mod dependencies:
    // advcl, appos, det, discourse, goeswith, mark, mwe, neg, padvmo, rcmod
    // num, number, poss, possessive, preconj, predet, prep, prtd.

    public static final String NO_SCOPE              = "noscope";

    // Predicate Types
    public static final String PREDICATE_MODAL       = "modal";
    public static final String PREDICATE_NEGATOR     = "negator";
    public static final String PREDICATE_HEDGE       = "hedge";
    public static final String PREDICATE_INTENSIFIER = "intensifier";
    public static final String PREDICATE_DIMINISHER  = "diminisher";
    public static final String PREDICATE_SENTIMENT   = "sentiment";
    public static final String[] PREDICATE_ALL       =
            { PREDICATE_MODAL, PREDICATE_NEGATOR, PREDICATE_HEDGE,
              PREDICATE_INTENSIFIER, PREDICATE_DIMINISHER, PREDICATE_SENTIMENT };

    // Sentiment Polarity Values
    public static final String SENTIMENT_NEUTRAL  = "neutral";
    public static final String SENTIMENT_POSITIVE = "positive";
    public static final String SENTIMENT_NEGATIVE = "negative";
    public static final String[] SENTIMENT_ALL    =
            { SENTIMENT_NEUTRAL, SENTIMENT_POSITIVE, SENTIMENT_NEGATIVE };

    /** Execute PR over a single document */
    public void execute() throws ExecutionException {

        inAnns  = document.getAnnotations(inputAnnotationSetName);
        outAnns = document.getAnnotations(outputAnnotationSetName);

        if (document == null) {
            throw new GateRuntimeException("No document to process!");
        }

        AnnotationSet triggers = inAnns.get(triggerAnnName);

        // Optionally remove triggers which are not predicates
        List<Annotation> predicates;
        if (filterPredicates) {
            predicates = filterTypes( gate.Utils.inDocumentOrder(triggers),
                                      PREDICATE_ALL );
        } else {
            predicates = gate.Utils.inDocumentOrder(triggers);
        }

        // PHASE 1: Attempt to find scope for all predicates
        for (Annotation predicate : predicates) {
            // Make sure predicates are limited to a single token
            Annotation token = getToken(predicate);
            if (token != null) {
                // NOTE: Pass the heuristic functions the following:
                //     predicate: The Trigger Annotation
                //     deps: Dependencies with Trigger as argument (Governor or Dependant)
                //     cdeps: Collapsed Dependencies inferring trigger (such as prepc_without, conj_nor)
                //     inAnns: Required only to get other dependencies
                List<ScoperDependency> deps  = getDependencies(predicate);
                List<ScoperDependency> cdeps = getCollapsedDependencies(predicate);
                if (enableNegatorScope) {
                    // Prepositions:
                    prepcScope(predicate, cdeps);
                    // Conjunctions:
                    conjScope(predicate, cdeps);
                    // Auxiliary:
                    auxScope(predicate, deps);
                    // Verbs:
                    compScope(predicate, deps);
                    subjScope(predicate, deps);
                    // Nouns
                    nominalofScope(predicate, deps);
                    pronomsubjScope(predicate, deps);
                    // Adjectives, Adverbs, Determiners
                    negScope(predicate, deps);
                    modScope(predicate, deps);
                    copsubjScope(predicate, deps);
                    // Determiners
                    detScope(predicate, deps, inAnns);
                }
                if (enableAdjScope) {
                    modScope(predicate, deps);
                    copsubjScope(predicate, deps);
                }
                if (enableNomScope) {
                    prenommodScope(predicate, deps);
                }
                if (enableGrammarScope) {
                    grammarScope(predicate, deps);
                }
            }
        }

        // PHASE 2: Propagate the scope features
        for (Annotation trigger : triggers) {
            FeatureMap features = trigger.getFeatures();
            boolean hasScope = false;
            // Get list of scopes this trigger is embedded in
            PriorityQueue<Annotation> scopes =
                    getPath(trigger, SCOPE_ANNOTATION_TYPE);
            // Get list as Annotation IDs, and verify that trigger does not scope over itself
            ArrayList<Integer> ids = getIdList(trigger, scopes);

            if (!ids.isEmpty()) {
                features.put(TRIGGER_RSCOPEIDS_FEATURE, ids);
            }

            // Get the scope types and add as features
            while (scopes.size() > 0) {
                Annotation scope = scopes.remove();
                Annotation scopeTrigger = getScopeTrigger(scope);
                if (scopeTrigger == null) {
                    continue;
                }
                String scopeType = getScopeType(scopeTrigger);
                // Add as feature
                if (scopeType == null) {
                    continue;
                }
                // Annotate scope type as a new feature
                String type = scope.getFeatures().get(TRIGGER_TYPE_FEATURE).toString();
                if (type.equals(PREDICATE_SENTIMENT)) {
                    features.put(type, scope.getFeatures().get(TRIGGER_POLARITY_FEATURE));
                } else {
                    features.put(type, "true");
                }
                hasScope = true;
            }
            if (!hasScope) {
                features.put(NO_SCOPE, "true");
            }
        }
    }

    /** Convert annotations to a list of ids.
     * If the annotations are scopes, verify that the trigger does not scope over itself. */
    public static ArrayList<Integer> getIdList(Annotation trigger, Iterable<Annotation> anns) {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        for (Annotation ann : anns) {
            if (ann.getFeatures().get(SCOPE_TRIGGERID_FEATURE)==null
                    || !trigger.getId().equals(ann.getFeatures().get(SCOPE_TRIGGERID_FEATURE))) {
                ids.add(Integer.parseInt(ann.getId().toString()));
            }
        }
        return ids;
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

    // Scope Heuristics

    /** Annotate the scope of a negation modifier.
     * trigger(T) ^ neg(X, T) =&gt; scope(X)
     */
    private void negScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Annotate the governor of neg dependencies
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, NEG_DEPENDENCIES, false);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, scopeDeps.get(0).getType());
    }

    /** Annotate the scope of a negation determiner.
     * trigger(T) ^ det(X, T) ^ comp(Y, X) =&gt; scope(Y)
     */
    private void detScope(Annotation trigger, List<ScoperDependency> dependencies, AnnotationSet alist) {
        // Annotate the governor of det dependencies
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, DET_DEPENDENCIES, false);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        // If that governor is the object of a verb, include that verb
        // TODO: only supports single target (this should be fine)
        Annotation target = alist.get(Integer.parseInt(scopeDeps.get(0).getTargetId().toString()));
        List<ScoperDependency> targetDependencies = getDependencies(target);
        List<ScoperDependency> tempDeps =
                filterDependencies(targetDependencies, COMP_DEPENDENCIES, false);
        if (!(tempDeps == null || tempDeps.isEmpty()))
            scopeDeps.addAll(tempDeps);
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, "det");
    }

    /** Annotate the scope of a subject.
     * trigger(T) ^ nsubj(X, T) =&gt; scope(X)
     */
    private void pronomsubjScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Annotate the governor of nsubj dependencies
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, SUBJ_DEPENDENCIES, false);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, "pronomsubj");
    }

    /** Annotate the scope of a preposition.
     * trigger(T) ^ preposition(T) ^ prepc_*(Y, X) =&gt; scope(X)
     */
    private void prepcScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Filter all but prepositions
        Annotation token = getToken(trigger);
        if (!filterPos(token, TOKEN_CATEGORY_PREP)) return;
        // Annotate the target of PREP dependencies
        List<ScoperDependency> scopeDeps =
                filterDependenciesStartsWith(dependencies, PREP_DEPENDENCIES, true);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, scopeDeps.get(0).getType());
    }

    /** Annotate the scope of a verb.
     * trigger(T) ^ comp(T, X) =&gt; scope(X)
     */
    private void compScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Annotate the governor of nsubj dependencies
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, COMP_DEPENDENCIES, true);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, scopeDeps.get(0).getType());
    }

    /** Annotate the scope of an intransitive verb.
     * trigger(T) ^ subj(T, X) =&gt; scope(X)
     */
    private void subjScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Annotate the governor of nsubj dependencies
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, SUBJ_DEPENDENCIES, true);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, scopeDeps.get(0).getType());
    }

    /** Annotate the scope of a nominalization with of.
     * trigger(T) ^ noun(T) ^ prep_of(T, X) =&gt; scope(X)
     */
    private void nominalofScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Filter all but nouns
        Annotation token = getToken(trigger);
        if (!filterPos(token, TOKEN_CATEGORY_NOUN)) return;
        // Annotate the target of prep_of dependencies
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, PREP_OF_DEPENDENCIES, true);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, "nominalof");
    }

    /** Annotate the scope of conjunction.
     * trigger(T) ^ cc(T) ^ conj_*(Y, X) =&gt; scope(X)
     */
    private void conjScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Filter all but prepositions
        Annotation token = getToken(trigger);
        if (!filterPos(token, TOKEN_CATEGORY_PREP)) return;
        // Annotate the target of PREP dependencies
        List<ScoperDependency> scopeDeps =
                filterDependenciesStartsWith(dependencies, CONJ_DEPENDENCIES, true);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, scopeDeps.get(0).getType());
    }

    /** Annotate the scope of an auxiliary.
     * trigger(T) ^ aux(X, T) =&gt; scope(X)
     */
    private void auxScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Annotate the governor of mod dependencies, if exists
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, AUX_DEPENDENCIES, false);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        List<Annotation> scopeAnns = targetsToAnns(scopeDeps);
        // Use the trigger to find the scope
        Annotation scope = getPhrase(trigger, scopeAnns);
        annotateScope(scope, trigger, scopeDeps.get(0).getType());
    }

    /** Annotate the scope of a modifier.
     * trigger(T) ^ mod(X, T) =&gt; scope(X)
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

    /** Annotate the subject of an adjective with a copula.
     * trigger(T) ^ adjective(T) ^ cop(T, X) ^ nsubj(T, Y) =&gt; scope(Y)
     */
    private void copsubjScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Filter all but adjectives
        Annotation token = getToken(trigger);
        if (!filterPos(token, TOKEN_CATEGORY_ADJ)) return;
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

    /** Annotate the scope of a noun with a premodifier.
     * trigger(T) ^ noun(T) ^ mod(Y, T) ^ mod(X, T) ^ Y &lt; X =&gt; scope(X)
     */
    private void prenommodScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Filter all but nouns
        Annotation token = getToken(trigger);
        if (!filterPos(token, TOKEN_CATEGORY_NOUN)) return;
        // Annotate the dep of a mod dependencies, if exists
        List<ScoperDependency> scopeDeps =
                filterDependencies(dependencies, MOD_DEPENDENCIES, true);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        Annotation scope = null;
        // Look for the nearest prenominal modifier
        for (ScoperDependency dep : scopeDeps) {
            Annotation candidate = inAnns.get(dep.getTargetId());
            // Verify that the candidate scope precedes trigger
            if (candidate.getStartNode().getOffset()
                < trigger.getStartNode().getOffset())
            {
                // Get the closest modifier to the trigger
                if (scope == null || candidate.getStartNode().getOffset()
                                       > scope.getStartNode().getOffset())
                {
                    scope = candidate;
                }
            }
        }
        if (scope != null) {
            annotateScope(scope, trigger, "prenommod");
        }
    }

    /** Annotate using the grammarscope approach.
     * trigger(T) ^ *dep(T, X) =&gt; scope(X)
     */
    private void grammarScope(Annotation trigger, List<ScoperDependency> dependencies) {
        // Get dependants for this trigger
        List<ScoperDependency> scopeDeps = filterDependencies(dependencies);
        if (scopeDeps == null || scopeDeps.isEmpty()) return;
        // and recursively anotate dependants of dependants, etc.
        LinkedList<Annotation> openList  = new LinkedList<Annotation>(targetsToAnns(scopeDeps));
        LinkedList<Annotation> closeList = new LinkedList<Annotation>();
        while (openList.size() != 0) {
            Annotation a = openList.remove();
            if (!closeList.contains(a)) {
                closeList.add(a);
                List<ScoperDependency> deps =
                        filterDependencies(getDependencies(a));
                if (!(deps == null || deps.isEmpty())) {
                    openList.addAll(targetsToAnns(deps));
                }
            }
        }
        Annotation scope = getPhrase(closeList);
        annotateScope(scope, trigger, "grammarscope");
    }

    /** Filter Triggers by type. */
    public static List<Annotation> filterTypes(List<Annotation> triggers,
            String[] types, AnnotationSet alist) {
        List<Annotation> results = new ArrayList<Annotation>();
        for (Annotation trigger : triggers) {
            if ( Arrays.asList(PREDICATE_ALL).contains(
                        trigger.getFeatures().get(TRIGGER_TYPE_FEATURE)) ) {
                results.add(trigger);
            }
        }
        return results;
    }
    private List<Annotation> filterTypes(List<Annotation> triggers,
            String[] types) {
        return filterTypes(triggers, types, inAnns);
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
            List<ScoperDependency> dependencies, boolean gov) {
        List<ScoperDependency> results = new ArrayList<ScoperDependency>();
        for (ScoperDependency dependency : dependencies) {
            if (dependency.isGov() == gov) {
                results.add(dependency);
            }
        }
        return results;
    }
    public static List<ScoperDependency> filterDependencies(
            List<ScoperDependency> dependencies, String[] types) {
        return filterDependencies(dependencies, types, true);
    }
    public static List<ScoperDependency> filterDependencies(
            List<ScoperDependency> dependencies) {
        return filterDependencies(dependencies, true);
    }

    public static List<ScoperDependency> filterDependenciesStartsWith(
            List<ScoperDependency> dependencies, String[] types, boolean gov) {
        List<ScoperDependency> results = new ArrayList<ScoperDependency>();
        for (ScoperDependency dependency : dependencies) {
            for (int i = 0; i < types.length; i++) {
                if ( dependency.getType().startsWith(types[i])
                        && dependency.isGov() == gov ) {
                    results.add(dependency);
                }
            }
        }
        return results;
    }
    public static List<ScoperDependency> filterDependenciesStartsWith(
            List<ScoperDependency> dependencies, String[] types) {
        return filterDependenciesStartsWith(dependencies, types, true);
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
        // Get the STN path for each token (from smallest to largest)
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
        System.err.println("Error: No common node for candidate tokens");
        return null;
    }
    private Annotation getPhrase(List<Annotation> tokens) {
        return getPhrase(tokens, inAnns);
    }

    /** Find the largest phrase dominating a list of tokens, but not including the trigger.
     *  Reverts to getPhrase(tokens) if no node is found.
     **/
    public static Annotation getPhrase(Annotation trigger, List<Annotation> tokens,
            AnnotationSet alist) {
        // Get the STN path for the trigger
        PriorityQueue<Annotation> triggerPath = getPath(trigger, PHRASE_ANNOTATION_TYPE, alist);
        // Get the STN path for each token (from largest to smallest)
        List<PriorityQueue<Annotation>> paths = new ArrayList<PriorityQueue<Annotation>>();
        for (Annotation token : tokens) {
            paths.add(getPath(token, PHRASE_ANNOTATION_TYPE, false, alist));
        }
        // Find the largest STN which is common to all paths but excludes trigger
        PriorityQueue<Annotation> path1 = paths.get(0);
        while (path1.size() != 0) {
            Annotation node = path1.remove();
            if (triggerPath.contains(node)) continue;
            boolean commonNode = true;
            for (int i=1; i<paths.size(); i++) {
                if (!paths.get(i).contains(node)) {
                    commonNode = false;
                    break;
                }
            }
            if (commonNode) return node;
        }
        System.err.println("Warning: No common node for candidate tokens excluding trigger");
        // Revert to getPhrase without trigger
        return getPhrase(tokens, alist);
    }
    private Annotation getPhrase(Annotation trigger, List<Annotation> tokens) {
        return getPhrase(trigger, tokens, inAnns);
    }

    /** Standard function for creating scope annotation and features */
    private void annotateScope(Long startOffset, Long endOffset,
                               Annotation trigger, String heuristic)
                        throws InvalidOffsetException {
        // If scope already exists for a different heuristic issue a warning
        Annotation scope = getScope(trigger);
        if (scope != null) {
            if (DEBUG) {
                String oldHeuristic =
                    scope.getFeatures().get(SCOPE_HEURISTIC_FEATURE).toString();
                if (!heuristic.equals(oldHeuristic)) {
                    String newScope = this.getDocument().getContent().getContent(
                            startOffset, endOffset).toString();
                    System.err.println("Warning: Multiple scopes detected for trigger:");
                    System.err.println("    OLD: "+getAnnotationText(trigger)+" -> ("
                                      +oldHeuristic+") "+getAnnotationText(scope));
                    System.err.println("    NEW: "+getAnnotationText(trigger)+" -> ("
                                      +heuristic+") "+newScope);
                }
            }
        // Otherwise annotate scope
        } else {
            FeatureMap scopeFeatures = gate.Factory.newFeatureMap();
            FeatureMap triggerFeatures = trigger.getFeatures();

            scopeFeatures.put(SCOPE_HEURISTIC_FEATURE, heuristic);
            scopeFeatures.put(SCOPE_TRIGGERID_FEATURE, trigger.getId());
            scopeFeatures.put(SCOPE_TRIGGERSTRING_FEATURE, getAnnotationText(trigger));
            for (String f : SCOPE_INHERITED_FEATURES) {
                if (triggerFeatures.containsKey(f)) {
                    scopeFeatures.put(f, triggerFeatures.get(f));
                }
            }

            outAnns.add(startOffset, endOffset, SCOPE_ANNOTATION_TYPE, scopeFeatures);

            // Add features to trigger: scopeID, scopeString
            scope = getScope(trigger); // Get the scope we just added to the document
            triggerFeatures.put(TRIGGER_SCOPEID_FEATURE, scope.getId());
            triggerFeatures.put(TRIGGER_SCOPESTRING_FEATURE, getAnnotationText(scope));
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
     * @return  Whether the POS of the token matches the given POS
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

    /** Get the trigger which corresponds to this scope */
    public static Annotation getScopeTrigger(Annotation scope,
            AnnotationSet alist) {
        if (!scope.getFeatures().containsKey(SCOPE_TRIGGERID_FEATURE)) {
            System.err.println("Error: Scope without TriggerID");
            System.err.println("Source: "
                    +scope.getFeatures().get(SCOPE_HEURISTIC_FEATURE));
            return null;
        }
        Annotation trigger = alist.get(Integer.parseInt(
                scope.getFeatures().get(SCOPE_TRIGGERID_FEATURE).toString()));
        return trigger;
    }
    private Annotation getScopeTrigger(Annotation scope) {
        return getScopeTrigger(scope, inAnns);
    }

    /** Find the scope which corresponds to this trigger or token */
    public static Annotation getScope(Annotation trigger,
            AnnotationSet alist) {
        Annotation root = getStn(trigger, PHRASE_CATEGORY_ROOT, alist);
        if (root == null) {
            System.err.println("Error: No root node found.");
            return null;
        }
        // Find scope who's triggerId corresponds to this trigger
        AnnotationSet sentenceScopes = alist.get(SCOPE_ANNOTATION_TYPE,
                                           root.getStartNode().getOffset(),
                                           root.getEndNode().getOffset());
        for (Annotation scope : sentenceScopes) {
            Annotation scopeTrigger = getScopeTrigger(scope, alist);
            if (scopeTrigger != null && trigger.coextensive(scopeTrigger)) {
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
            String type, boolean naturalOrder, AnnotationSet alist) {
        Comparator<Annotation> comparator = new AnnotationSpanComparator();
        PriorityQueue<Annotation> queue = null;
        if (naturalOrder) {
            queue = new PriorityQueue<Annotation>(10, comparator);
        } else {
            queue = new PriorityQueue<Annotation>(10,
                            Collections.reverseOrder(comparator));
        }
        for (Annotation a : getOverlaping(token, alist.get(type))) {
            queue.add(a);
        }
        return queue;
    }
    public static PriorityQueue<Annotation> getPath(Annotation token, String type,
            AnnotationSet alist) {
        return getPath(token, type, true, alist);
    }
    private PriorityQueue<Annotation> getPath(Annotation token, String type,
            boolean naturalOrder) {
        return getPath(token, type, naturalOrder, inAnns);
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
            // Convert dependency annotation to ScoperDependency
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

    public static List<ScoperDependency> getCollapsedDependencies(Annotation trigger,
            AnnotationSet alist) {
        ArrayList<ScoperDependency> depList = new ArrayList<ScoperDependency>();
        Annotation token = getToken(trigger, alist);
        for (Annotation dep : getOverlaping(trigger, alist.get(DEPENDENCY_ANNOTATION_TYPE))) {
            String type = dep.getFeatures().get(DEPENDENCY_LABEL_FEATURE).toString().trim();
            if ( !type.endsWith(token.getFeatures().get(TOKEN_STRING_FEATURE).toString()) ) continue;
            String ids  = dep.getFeatures().get(DEPENDENCY_ARG_FEATURE).toString().trim();
            ids = ids.substring(1, ids.length()-1);
            String[] args = ids.split("\\,");
            int depId = Integer.parseInt(args[1].trim());
            int govId = Integer.parseInt(args[0].trim());
            // Convert dependency annotation to ScoperDependency
            // Add two dependencies for every collapsed dependency
            depList.add(new ScoperDependency(type, depId, true));
            depList.add(new ScoperDependency(type, govId, false));
        }
        return depList;
    }
    private List<ScoperDependency> getCollapsedDependencies(Annotation trigger) {
        return getCollapsedDependencies(trigger, inAnns);
    }

    /** Get the scope type */
    public static String getScopeType(Annotation trigger) {
        FeatureMap features = trigger.getFeatures();
        if (features.containsKey(TRIGGER_TYPE_FEATURE)) {
            String type = features.get(TRIGGER_TYPE_FEATURE).toString();
            if ( Arrays.asList(PREDICATE_ALL).contains(type) ) {
                return type;
            }
            return features.get(TRIGGER_TYPE_FEATURE).toString();
        }
        return null;
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
        return getCoextensive(trigger, alist.get(TOKEN_ANNOTATION_TYPE));
    }
    private Annotation getToken(Annotation trigger) {
        Annotation token = getToken(trigger, inAnns);
        if (DEBUG && token == null) {
            System.err.println( "Warning: no token for trigger ("
                + getAnnotationText(trigger).toString() + ")" );
        }
        return token;
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
    }

    public String getInputAnnotationSetName() {
        return this.inputAnnotationSetName;
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "The output annotation set name")
    public void setOutputAnnotationSetName(String outputAnnotationSetName) {
        this.outputAnnotationSetName = outputAnnotationSetName;
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
    @CreoleParameter(comment = "Include the trigger as part of the narrow scope (doesn't apply to adj/adv scope)",
                     defaultValue = "false")
    public void setIncludeTrigger(Boolean includeTrigger) {
        this.includeTrigger = includeTrigger;
    }

    public Boolean getIncludeTrigger() {
        return this.includeTrigger;
    }

    @RunTime
    @CreoleParameter(comment = "Only annotate scope for triggers with a predicate type in Scoper.PREDICATE_ALL",
                     defaultValue = "false")
    public void setFilterPredicates(Boolean filterPredicates) {
        this.filterPredicates = filterPredicates;
    }

    public Boolean getFilterPredicates() {
        return this.filterPredicates;
    }

    @RunTime
    @CreoleParameter(comment = "Enable the Adjective/Adverb scope heuristics",
                     defaultValue = "true")
    public void setEnableAdjScope(Boolean enableAdjScope) {
        this.enableAdjScope = enableAdjScope;
    }

    public Boolean getEnableAdjScope() {
        return this.enableAdjScope;
    }

    @RunTime
    @CreoleParameter(comment = "Enable the Negator scope heuristics",
                     defaultValue = "true")
    public void setEnableNegatorScope(Boolean enableNegatorScope) {
        this.enableNegatorScope = enableNegatorScope;
    }

    public Boolean getEnableNegatorScope() {
        return this.enableNegatorScope;
    }

    @RunTime
    @CreoleParameter(comment = "Enable the nominal scope (DEPRICATED)",
                     defaultValue = "false")
    public void setEnableNomScope(Boolean enableNomScope) {
        this.enableNomScope = enableNomScope;
    }

    public Boolean getEnableNomScope() {
        return this.enableNomScope;
    }

    @RunTime
    @CreoleParameter(comment = "Annotate the scope as defined by Stanford's GrammarScope (EXPERIMENTAL)",
                     defaultValue = "false")
    public void setEnableGrammarScope(Boolean enableGrammarScope) {
        this.enableGrammarScope = enableGrammarScope;
    }

    public Boolean getEnableGrammarScope() {
        return this.enableGrammarScope;
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
