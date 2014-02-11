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

    // Phrase (SyntaxTreeNode)
    public static final String PHRASE_ANNOTATION_TYPE     = Parser.PHRASE_ANNOTATION_TYPE;
    public static final String PHRASE_CATEGORY_FEATURE    = Parser.PHRASE_CAT_FEATURE;

    // Dependency
    public static final String DEPENDENCY_ANNOTATION_TYPE = Parser.DEPENDENCY_ANNOTATION_TYPE;
    public static final String DEPENDENCY_ARG_FEATURE     = Parser.DEPENDENCY_ARG_FEATURE;
    public static final String DEPENDENCY_LABEL_FEATURE   = Parser.DEPENDENCY_LABEL_FEATURE;

    public static final String[] MOD_DEPENDENCIES = {
            "amod", "advmod", "rcmod", "quantmod", "infmod", "partmod" };
    //Unsure: advcl, mark, num, number, nn, appos, discourse, advmod, npadvmod,
    // mwe, det, predet, preconj, poss, possessive, prep, prt, goeswith
    // JJ*, VBP


    public void execute() throws ExecutionException {
        inAnns = document.getAnnotations(inputAnnotationSetName);
        outAnns = document.getAnnotations(outputAnnotationSetName);
        if (document == null) {
            throw new GateRuntimeException("No document to process!");
        }
        AnnotationSet triggers = inAnns.get(triggerAnnName);
        for (Annotation trigger : triggers) {
            Annotation token = getToken(trigger);
            if (token != null) {
                adjScope(trigger);
                //nomScope(trigger);
            }
        }
    }

    /** Annotate the scope of a given trigger */ /*
    private void nomScope(Annotation trigger) {
        Annotation token = getCoextensive(trigger,
                inAnns.get(TOKEN_ANNOTATION_TYPE));
        // Get nominalizations (TODO)
        if (!filterPos(token, "NN")) return;
        // Ignore noms which already get a scope from negator
        if (getScope(trigger) != null) return;
        // Iterate through all Dependencies TODO: Inefficient, limit by offset
        Annotation scope = getMaxCat(trigger, "N");
        if (scope == null) {
            System.err.println("Warning: no scope for nominalization");
            return;
        }
        String heuristic = "nom-premod";
        annotateScope(scope, trigger, heuristic);
    }
*/
    /** Annotate the scope of a given trigger */
    private void adjScope(Annotation trigger) {
        Annotation token = getCoextensive(trigger, inAnns.get(TOKEN_ANNOTATION_TYPE));
        //if (!filterPos(token, "JJ")) return;
        // Iterate through all overlaping Dependencies
        for (Annotation dep : getOverlaping(trigger, inAnns.get(DEPENDENCY_ANNOTATION_TYPE))) {
            String kind = dep.getFeatures().get(DEPENDENCY_LABEL_FEATURE).toString().trim();
            String ids  = dep.getFeatures().get(DEPENDENCY_ARG_FEATURE).toString().trim();
            ids = ids.substring(1, ids.length()-1);
            String[] args = ids.split("\\,");
            int depId = Integer.parseInt(args[1].trim());
            int govId = Integer.parseInt(args[0].trim());

            // Check *mod dependencies
            for (int i = 0; i < MOD_DEPENDENCIES.length; i++) {
                if (kind.equals(MOD_DEPENDENCIES[i]) && token.getId() == depId) {
                    Annotation scope = (Annotation) inAnns.get(govId);
                    String heuristic = dep.getFeatures().get(DEPENDENCY_LABEL_FEATURE).toString();
                    String source = "";
                    if (trigger.getFeatures().get(TRIGGER_SOURCE_FEATURE) != null) {
                        source = trigger.getFeatures().get(TRIGGER_SOURCE_FEATURE).toString();
                    }
                    annotateScope(scope, trigger, heuristic, source);
                }
            }
        }
    }

    /** Wrapper which uses single annotation as the scope */
    private void annotateScope(Annotation scope, Annotation trigger,
            String heuristic, String source) {
        try {
            Long startOffset = scope.getStartNode().getOffset();
            Long endOffset   = scope.getEndNode().getOffset();
            annotateScope(startOffset, endOffset, trigger, heuristic, source);
        } catch (InvalidOffsetException e) {
            System.out.println("Error: invalid scope offsets.");
            e.printStackTrace();
        }
    }
    /** Standard function for creating scope annotation and features */
    private void annotateScope(Long startOffset, Long endOffset,
            Annotation trigger, String heuristic, String source)
            throws InvalidOffsetException {
        // If scope already exists issue a warning
        Annotation scope = getScope(trigger);
        if (scope != null) {
            String newScope = this.getDocument().getContent().getContent(
                    startOffset, endOffset).toString();
            System.err.println("Warning: Multiple scopes detected for trigger:");
            System.err.println("OLD: "+getAnnotationText(trigger)+"' -> ("
                              +scope.getFeatures().get(SCOPE_HEURISTIC_FEATURE)+") "
                              +getAnnotationText(scope));
            System.err.println("NEW: "+getAnnotationText(trigger)+"' -> ("
                              +heuristic+") "+newScope);
        // Otherwise annotate scope
        } else {
            FeatureMap fm = gate.Factory.newFeatureMap();
            fm.put(SCOPE_TRIGGERID_FEATURE, trigger.getId());
            fm.put(SCOPE_HEURISTIC_FEATURE, heuristic);
            fm.put(TRIGGER_SOURCE_FEATURE, source);
            outAnns.add(startOffset, endOffset, SCOPE_ANNOTATION_TYPE, fm);
        }
    }

    /** Get the smallest STN gov including a trigger,
     * with a given category pattern. */
    /*
    private Annotation getFirstStn(Annotation trigger, String cat) {
        Annotation token = getCoextensive(trigger, inAnns.get(TOKEN_ANNOTATION_TYPE));
        Annotation maxCat = null;
        // Get the 
        for (Annotation stn : inAnns.get(PHRASE_ANNOTATION_TYPE)) {
            if (filterPos(stn, cat, PHRASE_CATEGORY_FEATURE)) {
                if (maxCat == null) {
                    maxCat = stn;
                } else if (false) {
            }
        }}
        System.err.println("Error: problem with SyntaxTreeNodes");
        return null;*/
        /* Kept as Sample code for PQ method
       for (Annotation a : queue) {
            System.out.println(a.getFeatures().get(PHRASE_CATEGORY_FEATURE));
            System.out.println(getAnnotationText(a));
            System.out.println();
            if (filterPos(a, cat, PHRASE_CATEGORY_FEATURE)) {
                maxCat = a;
            } else {
                return maxCat;
            }
        }
        */
    //}

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

    /** Get a SyntaxTreeNode of a certain category including an annotation */
    public static Annotation getStn(Annotation ann, String cat, AnnotationSet alist) {
        for (Annotation a : getStnPath(ann, alist)) {
            if (a.getFeatures().get(PHRASE_CATEGORY_FEATURE).equals(cat)) {
                return a;
            }
        }
        return null;
    }
    private Annotation getStn(Annotation ann, String cat) {
        return getStn(ann, cat, inAnns);
    }

    /** Get the SyntaxTreeNode path, from token/trigger to ROOT */
    // TODO: Convert this to getPath(token, "stn") to allow reuse with scopes
    private PriorityQueue<Annotation> getStnPath(Annotation token) {
        return getStnPath(token, inAnns);
    }
    public static PriorityQueue<Annotation> getStnPath(Annotation token,
            AnnotationSet alist) {
        Comparator<Annotation> comparator = new AnnotationSpanComparator();
        PriorityQueue<Annotation> queue =
                new PriorityQueue<Annotation>(10, comparator);
        for (Annotation a : getOverlaping(token, alist.get(PHRASE_ANNOTATION_TYPE))) {
            queue.add(a);
        }
        return queue;
    }

    /** Find the scope which corresponds to this trigger or token */
    // TODO: Make triggers point to their scope to speed this up (change negator format)
    public static Annotation getScope(Annotation trigger, AnnotationSet alist) {
        Annotation root = getStn(trigger, "ROOT", alist);
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
        return null;
    }
    private Annotation getScope(Annotation trigger) {
        return getScope(trigger, inAnns);
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
            System.err.println("Warning: no token for trigger");
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
    private gate.DocumentContent getAnnotationText(Annotation annotation) {
        try {
            return this.getDocument().getContent().getContent(
                    annotation.getStartNode().getOffset(),
                    annotation.getEndNode().getOffset());
        }
        catch(gate.util.InvalidOffsetException e) {
            System.err.println("Error: Invalid Annotation Offsets");
            return null;
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
