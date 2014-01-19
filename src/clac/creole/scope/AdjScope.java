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
		comment = "Annotateds Scope of Adjectives from a Trigger List")
public class AdjScope
	extends AbstractLanguageAnalyser
	implements ProcessingResource {

	//TODO: should I filter out a list of deps.:
	//private static String[] depList = {"neg","det","dep","pobj","pcomp","xcomp","advmod","amod","infmod","cc","nsubj","conj","nsubjpass","dobj","conj_negcc","preconj","conj_nor","conj_but","conj_and","conj_or","prep","ccomp","nn","expl","acomp","rcmod","auxpass","compl","cop","mark","aux"};
	private static String[] depList = {"amod","rcmod"};
	
	// TODO: Make configurable
	protected String triggerAnnName = "Trigger";
	protected String scopeAnnName = "Scope";
	public boolean scopeFound = false;

	public void execute() throws ExecutionException {
		if (document == null) {
			throw new GateRuntimeException("No document to process!");
		}

		String docName = (new File(document.getSourceUrl().getFile())).getName();
		String out = "";
		
		// Get the list of triggers
		
		AnnotationSet triggerAnnSet = document.getAnnotations().get(triggerAnnName);

		for (Annotation trigger : triggerAnnSet) {
			//Annotation sentence = getSpanning(trigger, document.getAnnotations().get("Sentence"));
			Annotation token = getCoextensive(trigger, document.getAnnotations().get("Token"));
			// TODO: If the trigger  is not a single token, Give up (e.g. Pro-American)
			if (token == null) {
				System.err.println("Warning: Multi-token trigger: "+getAnnotationText(trigger));
				continue;
			}
			annotateScope(token, document.getAnnotations());
		}
	}
	
	// Get a list of dependencies for a given token
	// This currently returns a comma seperated string, but this may change in the future
	private void annotateScope(Annotation token, AnnotationSet anns) {
		//List<String> results = new ArrayList<String>();
		String results = "";
		
		for (Annotation dep : document.getAnnotations().get("Dependency")) {
			String kind = dep.getFeatures().get("kind").toString().trim();
			String ids = dep.getFeatures().get("args").toString().trim();
			ids = ids.substring(1, ids.length()-1);
			String[] args = ids.split("\\,");
			int govId = Integer.parseInt(args[0].trim());
			for (int i = 0; i < depList.length; i++) {
				if (kind.equals(depList[i]) && token.getId() == govId) {
					if (scopeFound) {
						System.err.println( "Warning: Multiple '*mod' dependencies for trigger: "
								           +getAnnotationText(token) );
						continue;
					}
					try {
						scopeFound = true;
						Annotation scope = (Annotation) anns.get(govId);
						Long start = scope.getStartNode().getOffset();
						Long end = scope.getEndNode().getOffset();
						FeatureMap fm = gate.Factory.newFeatureMap();
						fm.put("heuristic", dep.getFeatures().get("kind").toString().trim());
						anns.add(start, end, scopeAnnName, fm);
					} catch (InvalidOffsetException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	// Find the first coextensive annotation in a list or return null
	private Annotation getCoextensive(Annotation ann, AnnotationSet alist) {
		for (Annotation a : alist) {
			if (ann.coextensive(a)) {
				return a;
			}
		}
		return null;
	}

	// Find the first overlaping annotation in a list or return null
	private Annotation getOverlaping(Annotation ann, AnnotationSet alist) {
		for (Annotation a : alist) {
			if (ann.overlaps(a)) {
				return a;
			}
		}
		return null;
	}

	// Find the first spanning annotation in a list or return null
	private Annotation getSpanning(Annotation ann, AnnotationSet alist) {
		for (Annotation a : alist) {
			if (ann.withinSpanOf(a)) {
				return a;
			}
		}
		return null;
	}

	private Annotation getScope(Annotation trigger, AnnotationSet alist) {
		for (Annotation a : alist) {
			if ( a.getFeatures().containsKey("ID_of_Input_Trigger") &&
					trigger.getId() == Integer.parseInt(
						a.getFeatures().get("ID_of_Input_Trigger").toString() ) ) {
				return a;
			}
		}
		return null;
	}
	// Get the text of an annotation
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

	/**
	 * Initialize the resource.
	 */
	public Resource init() throws ResourceInstantiationException {
		super.init();
		return this;
	}

	@Override
	public void reInit() throws ResourceInstantiationException {
		init();
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
