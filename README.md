<p align="center">Large-Scale Distributed Systems</br>Amazon Dynamo - a replicated key-value storage</br>CSE 586
==========================================================================================
<p align="center">![Img_4](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Images/2.png)

Goal
------
Implement a Dynamo styled key-value storage with **simultaneous availability and linearizability (or sometimes called strong consistency) guarantees**. The system should have the ability to successfully undergo **concurrent read and write operations** and should provide consistent results even under **node failures**

Context & Problem Statement
-------------------------------------------
This assignment is about implementing a simplified version of Dynamo. There are three main pieces that has been implemented:

1. Partitioning 
> - Implemented Virtual Ring based on [**CHORD Protocol**](https://en.wikipedia.org/wiki/Chord_(peer-to-peer)) to provide ID space partitioning/re-partitioning
> - Used [**SHA-1**](https://en.wikipedia.org/wiki/SHA-1) for Consistent hashing and lexically arrange nodes in the virtual ring and find the location for a particular key to be stored

2. Replication
3. Failure Handling

	> Implemented [**Quorum**](https://en.wikipedia.org/wiki/Quorum_(distributed_computing)) based replication technique that provide partition/failure tolerance and enforce consistent operation in a distributed system


References
---------------
Here are two references for the Dynamo design:
	 1. [Lecture slides](http://www.cse.buffalo.edu/~stevko/courses/cse486/spring16/lectures/26-dynamo.pdf)
	 2. [Dynamo paper](http://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf)
