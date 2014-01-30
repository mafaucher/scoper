/*
 *  AdjScope.java
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

import java.io.*;
import java.util.*;

import gate.*;
import gate.creole.*;
import gate.creole.metadata.*;
import gate.util.*;

/**
 * This class is the implementation of the resource SCOPEEXPORTER.
 */
@CreoleResource(name = "AdjScope",
        comment = "Annotates Scope of Adjectives from a Trigger List")
public class AdjScope extends AbstractLanguageAnalyser
        implements ProcessingResource {

    private static String[] modDepList = {
            "amod", "rcmod", "quantmod", "infmod", "partmod" };

    protected String inputAnnotationSetName;
    protected String outputAnnotationSetName;
    private AnnotationSet inAnns;
    private AnnotationSet outAnns;
    protected String triggerAnnName;
    protected String scopeAnnName;

    public void execute() throws ExecutionException {
        inAnns = document.getAnnotations(inputAnnotationSetName);
        outAnns = document.getAnnotations(outputAnnotationSetName);
        if (document == null) {
            throw new GateRuntimeException("No document to process!");
        }
        AnnotationSet triggers = inAnns.get(triggerAnnName);
        for (Annotation trigger : triggers) {
            // Triggers should overlap with a token
            Annotation token = getCoextensive(trigger, inAnns.get("Token"));
            if (token == null) {
                System.err.println("Warning: no token for trigger: "
                                  +getAnnotationText(trigger));
                continue;
            }
            adjScope(trigger);
            //nomScope(trigger);
        }
    }

    /** Annotate the scope of a given trigger */
    private void nomScope(Annotation trigger) {
        Annotation token = getCoextensive(trigger, inAnns.get("Token"));
        // Get nominalizations (TODO)
        if (!filterPos(token, "NN")) return;
        // Iterate through all Dependencies TODO: Inefficient, limit by offset
        for (Annotation dep : inAnns.get("Dependency")) {
            String kind = dep.getFeatures().get("kind").toString().trim();
            String ids  = dep.getFeatures().get("args").toString().trim();
            ids = ids.substring(1, ids.length()-1);
            String[] args = ids.split("\\,");
            int depId = Integer.parseInt(args[1].trim());
            int govId = Integer.parseInt(args[0].trim());

            // Check *mod dependencies
            for (int i = 0; i < modDepList.length; i++) {
                if (kind.equals(modDepList[i]) && token.getId() == depId) {
                    Annotation scope = (Annotation) inAnns.get(govId);
                    String heuristic = dep.getFeatures().get("kind").toString();
                    annotateScope(scope, trigger, heuristic);
                }
            }
        }
    }

    /** Annotate the scope of a given trigger */
    private void adjScope(Annotation trigger) {
        Annotation token = getCoextensive(trigger, inAnns.get("Token"));
        // Ignore non-adjectives TODO: What about nominal modifiers?
        if (!filterPos(token, "JJ")) return;
        // Iterate through all Dependencies TODO: Inefficient, limit by offset
        for (Annotation dep : inAnns.get("Dependency")) {
            String kind = dep.getFeatures().get("kind").toString().trim();
            String ids  = dep.getFeatures().get("args").toString().trim();
            ids = ids.substring(1, ids.length()-1);
            String[] args = ids.split("\\,");
            int depId = Integer.parseInt(args[1].trim());
            int govId = Integer.parseInt(args[0].trim());

            // Check *mod dependencies
            for (int i = 0; i < modDepList.length; i++) {
                if (kind.equals(modDepList[i]) && token.getId() == depId) {
                    Annotation scope = (Annotation) inAnns.get(govId);
                    String heuristic = dep.getFeatures().get("kind").toString();
                    annotateScope(scope, trigger, heuristic);
                }
            }
        }
    }

    /** Wrapper which uses single annotation as the scope */
    private void annotateScope(Annotation scope, Annotation trigger, String heuristic) {
        try {
            Long startOffset = scope.getStartNode().getOffset();
            Long endOffset   = scope.getEndNode().getOffset();
            annotateScope(startOffset, endOffset, trigger, heuristic);
        } catch (InvalidOffsetException e) {
            System.out.println("Error: invalid scope offsets.");
            e.printStackTrace();
        }
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
            System.err.println("Warning: Multiple scopes detected for trigger:");
            System.err.println("OLD: "+getAnnotationText(trigger)+"' -> ("
                              +scope.getFeatures().get("heuristic")+") "
                              +getAnnotationText(scope));
            System.err.println("NEW: "+getAnnotationText(trigger)+"' -> ("
                              +heuristic+") "+newScope);
        // Otherwise annotate scope
        } else {
            FeatureMap fm = gate.Factory.newFeatureMap();
            fm.put("heuristic", heuristic);
            fm.put("triggerID", trigger.getId());
            outAnns.add(startOffset, endOffset, scopeAnnName, fm);
        }
    }

    /** Returns true iff token's POS matches given POS. */
    private boolean filterPos(Annotation token, String pos) {
        return filterPos(token, pos, false);
    }
    private boolean filterPos(Annotation token, String pos, boolean strict) {
        String tokenPos = token.getFeatures().get("category").toString();
        if (strict) return tokenPos.equals(pos);
        return ( tokenPos.length() >= pos.length() &&
                 tokenPos.substring(0, pos.length()).equals(pos) );
    }

    /** Find the scope which corresponds to this trigger or token */
    private Annotation getScope(Annotation trigger) {
        return getScope(trigger, inAnns);
    }
    private Annotation getScope(Annotation trigger, AnnotationSet alist) {
        AnnotationSet scopes = alist.get(scopeAnnName);
        for (Annotation scope : scopes) {
            Annotation scopeTrigger = alist.get(Integer.parseInt(
                    scope.getFeatures().get("triggerID").toString()));
            if ( scope.getFeatures().containsKey("triggerID") &&
                    trigger.coextensive(scopeTrigger) ) {
                return scope;
            }
        }
        return null;
    }

    /** Find the first coextensive annotation in a list or return null */
    private Annotation getCoextensive(Annotation ann, AnnotationSet alist) {
        for (Annotation a : alist) {
            if (ann.coextensive(a)) {
                return a;
            }
        }
        return null;
    }

    /** Find the first overlaping annotation in a list or return null */
    private Annotation getOverlaping(Annotation ann, AnnotationSet alist) {
        for (Annotation a : alist) {
            if (ann.overlaps(a)) {
                return a;
            }
        }
        return null;
    }

    /** Find the first spanning annotation in a list or return null */
    private Annotation getSpanning(Annotation ann, AnnotationSet alist) {
        for (Annotation a : alist) {
            if (ann.withinSpanOf(a)) {
                return a;
            }
        }
        return null;
    }

    /** Get the text of an annotation */
    private gate.DocumentContent getAnnotationText(Annotation annotation) {
        try {
            return this.getDocument().getContent().getContent(
                    annotation.getStartNode().getOffset(),
                    annotation.getEndNode().getOffset());
        }
        catch(gate.util.InvalidOffsetException e) {
            System.err.println("INVALID ANNOTATION OFFSET");
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
    @CreoleParameter(comment = "The annotation set name for the input annotations")
    public void setInputAnnotationSetName(String inputAnnotationSetName) {
        this.inputAnnotationSetName = inputAnnotationSetName;
        this.inAnns = document.getAnnotations(inputAnnotationSetName);
    }

    public String getInputAnnotationSetName() {
        return this.inputAnnotationSetName;
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "The annotation set name for the output annotations")
    public void setOutputAnnotationSetName(String outputAnnotationSetName) {
        this.outputAnnotationSetName = outputAnnotationSetName;
        this.outAnns = document.getAnnotations(outputAnnotationSetName);
    }

    public String getOutputAnnotationSetName() {
        return this.outputAnnotationSetName;
    }

    @RunTime
    @CreoleParameter(comment = "The annotation name for triggers", defaultValue="Trigger")
    public void setTriggerAnnName(String triggerAnnName) {
        this.triggerAnnName = triggerAnnName;
    }

    public String getTriggerAnnName() {
        return this.triggerAnnName;
    }

    @RunTime
    @CreoleParameter(comment = "The annotation name for scopes", defaultValue="Scope")
    public void setScopeAnnName(String scopeAnnName) {
        this.scopeAnnName = scopeAnnName;
    }

    public String getScopeAnnName() {
        return this.scopeAnnName;
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
