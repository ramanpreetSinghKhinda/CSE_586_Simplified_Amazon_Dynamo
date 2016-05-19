<p align="center">Large-Scale Distributed Systems</br>Amazon Dynamo - a replicated key-value storage</br>CSE 586
==========================================================================================
<p align="center">![Img_4](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Images/2.png)

Goal
------
Implement a Dynamo styled key-value storage with simultaneous availability and linearizability guarantees. The system should have the ability to successfully undergo concurrent read and write operations and should provide consistent results even under node failures

Context & Problem Statement
-------------------------------------------
This assignment is about implementing a simplified version of Dynamo. There are three main pieces that has been implemented:

1) Partitioning 
> - Implemented Virtual Ring based on [**CHORD Protocol**](https://en.wikipedia.org/wiki/Chord_(peer-to-peer)) 
> - [**SHA-1**](https://en.wikipedia.org/wiki/SHA-1) hash function provide **ID space partitioning/re-partitioning** and is used to lexically arrange nodes in the virtual ring and find the location for a particular key to be stored


2) Replication
3) Failure handling
