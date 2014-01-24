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

    private static String[] modDepList = { "amod", "advmod", "rcmod",
                                           "quantmod", "infmod", "partmod"};

    protected String inputAnnotationSetName;
    protected String outputAnnotationSetName;
    protected String triggerAnnName;
    protected String scopeAnnName;

    public void execute() throws ExecutionException {
        if (document == null) {
            throw new GateRuntimeException("No document to process!");
        }
        AnnotationSet inAnns = document.getAnnotations(inputAnnotationSetName);
        AnnotationSet outAnns = document.getAnnotations(outputAnnotationSetName);
        String docName = (new File(document.getSourceUrl().getFile())).getName();
        String out = "";
        AnnotationSet triggers = inAnns.get(triggerAnnName);
        for (Annotation trigger : triggers) {
            annotateScope(trigger, inAnns, outAnns);
        }
    }

    /** Annotate the scope of a given trigger */
    private void annotateScope(Annotation trigger, AnnotationSet inAnns, AnnotationSet outAnns) {
        //List<String> results = new ArrayList<String>();
        Annotation token = getCoextensive(trigger, inAnns.get("Token"));
        // If the trigger is not a single token, Give up (e.g. Pro-American)
        if (token == null) {
            System.err.println("Warning: no token for trigger: "
                              +getAnnotationText(trigger));
            return;
        }
        // Ignore non-adjectives
        if (!isAdj(token)) { return; }
        // Triggers should only have one scope, issue a warning otherwise
        boolean scopeFound = false;
        // Iterate through all Dependencies TODO: Inefficient
        for (Annotation dep : inAnns.get("Dependency")) {
            String kind = dep.getFeatures().get("kind").toString().trim();
            String ids = dep.getFeatures().get("args").toString().trim();
            ids = ids.substring(1, ids.length()-1);
            String[] args = ids.split("\\,");
            int depId = Integer.parseInt(args[1].trim());
            int govId = Integer.parseInt(args[0].trim());
            // Check *mod dependencies
            for (int i = 0; i < modDepList.length; i++) {
                if (kind.equals(modDepList[i]) && token.getId() == depId) {
                    if (scopeFound ) {
                        System.err.println( "Warning: Multiple '*mod' dependencies for trigger: "
                                          + getAnnotationText(token) + " (" + modDepList[i] + ")" );
                        continue;
                    }
                    try {
                        Annotation scope = (Annotation) inAnns.get(govId);
                        Long start = scope.getStartNode().getOffset();
                        Long end = scope.getEndNode().getOffset();
                        FeatureMap fm = gate.Factory.newFeatureMap();
                        fm.put("heuristic", dep.getFeatures().get("kind").toString().trim());
                        fm.put("triggerID", trigger.getId());
                        fm.put("tokenID", token.getId());
                        outAnns.add(start, end, scopeAnnName, fm);
                        scopeFound = true;
                    } catch (InvalidOffsetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private boolean isAdj(Annotation token) {
        String pos = (String) token.getFeatures().get("category");
        if ( pos.length() >= 2 && pos.substring(0,2).equals("JJ") ) {
        System.out.println("pos: "+pos);
            return true;
        }
        return false;
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
    }

    public String getInputAnnotationSetName() {
        return this.inputAnnotationSetName;
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "The annotation set name for the output annotations")
    public void setOutputAnnotationSetName(String outputAnnotationSetName) {
        this.outputAnnotationSetName = outputAnnotationSetName;
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
