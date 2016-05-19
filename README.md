<p align="center">Large-Scale Distributed Systems</br>Amazon Dynamo - a replicated key-value storage</br>CSE 586
==========================================================================================
<p align="center">![Img_4](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Images/2.png)

Goal
------
Implement a Dynamo styled key-value storage with **simultaneous availability and linearizability (or sometimes called strong consistency) guarantees**. The system should have the ability to successfully undergo **concurrent read and write operations** and should provide consistent results even under **node failures**


References
---------------
Here are two references for the Dynamo design: -</br>
1. [Lecture slides](http://www.cse.buffalo.edu/~stevko/courses/cse486/spring16/lectures/26-dynamo.pdf)</br>
2. [Dynamo paper](http://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf)


Writing the Content Provider
-----------------------------------------
This project implements a [**Content Provider**](https://developer.android.com/guide/topics/providers/content-providers.html) that provide all storage functionalities. For example, it creates server and client threads, open sockets, and respond to incoming requests. There are few assumptions/restrictions for the [**Grader**](https://github.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/blob/master/simpledynamo-grading.osx) that test this application: -
>  1. Any app (not just our app) should be able to access (read and write) our content provider.
>  2. There are always **5 nodes** in the system
>  3. There can be at most **1 node failure** at any given time and is emulated by force closing an app instance
>  4.  **All failures are temporary** and you can assume that a failed node will recover soon
>  5.  **When a node recovers, it should copy all the object writes it missed during the failure**. This is done by asking the right nodes and copy from them
>  6. Content Provider should support **concurrent read/write operations**
>  7. Content Provider should handle a **failure happening at the same time with read/write operations**
>  8.  **Replication should be done exactly the same way as Dynamo does**. In other words, a (key, value) pair should be replicated over three consecutive partitions, starting from the partition that the key belongs to
>  9. All replicas should store the same value for each key. This is “per-key” consistency. There is no consistency guarantee we need to provide across keys. More formally, we only implement **per-key linearizability**
>  10. Each content provider instance should have a node id derived from its emulator port. This node id should be obtained by applying the SHA1 hash function to the emulator port. For example, **the node id of the content provider instance running on emulator-5554 should be, node_id = genHash(“5554”)**. This is necessary to find the correct position of each node in the Dynamo ring
>  11. Unlike Dynamo, there are two things that we do not need to implement: - </br>
>   a) **Virtual nodes** - This implementation uses physical nodes rather than virtual nodes, i.e., all partitions are static and fixed</br>
>   b) **Hinted handoff** - This project do not implement hinted handoff. This means that when there is a failure, it is **OK to replicate on only two alive nodes**
>  11. We have fixed the ports & sockets: -</br>
	a) Our app opens one server socket that listens on **Port 10000**</br>
	b) We use [**run_avd.py**](https://github.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/blob/master/Scripts/run_avd.py) and [**set_redir.py**](https://github.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/blob/master/Scripts/set_redir.py) scripts to set up the testing environment </br>
	c) The grading will use 5 AVDs. The redirection ports are **11108, 11112, 11116, 11120, and 11124**
