Scoper
========

Instructions
------------

This is a GATE Processing Resource used to annotate the scope of a generic trigger.

Requirements:

* Stanford Parser Wrapper (For Annotation Types)

Parameters:

* triggerAnnName: Name of the trigger annotations
* sentenceAnnName: Name of the sentence annotations
* (optional) inputAnnotationSetName: Name of the input annotation set
* (optional) outputAnnotationSetName: Name of the output annotation set

Required Annotations:

* Sentence (param)
* Trigger (param)
* Token
* Dependency
* SyntaxTreeNode
* Sentence

Output Annotation:
* Scope: Detected scope for a trigger
