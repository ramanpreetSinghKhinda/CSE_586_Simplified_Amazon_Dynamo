<p align="center">Large-Scale Distributed Systems</br>Amazon Dynamo - a replicated key-value storage</br>CSE 586
==========================================================================================
<p align="center">![Img_4](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Resources/2.png)

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


Amazon Dynamo design guidelines
-------------------------------------------------
This project implements **simplified version of Amazon Dynamo** based on below design guidelines: - 

**1. Membership**
> a) Just as the original Dynamo, every node can know every other node. This means that each node knows all other nodes in the system and also knows exactly which partition belongs to which node
	
> b) Any node can forward a request to the correct node without using a ring-based routing

**2. Request routing**
> a) Unlike Chord, each Dynamo node knows all other nodes in the system and also knows exactly which partition belongs to which node

> b) Under no failures, a request for a key is directly forwarded to the coordinator (i.e., the successor of the key), and the coordinator should be in charge of serving read/write operations

**2. Quorum replication**
> a) Implement [**Quorum**](https://en.wikipedia.org/wiki/Quorum_(distributed_computing)) based replication that provide **Linearizability**

> b) Note that the **original design does not provide linearizability**. We have adapted our implementation

> c) **The replication degree N is 3**. This means that given a key, the key’s coordinator as well as the 2 successor nodes in the Dynamo ring store the key

> d) **Both the reader quorum size R and the writer quorum size W is 2**. It means that the coordinator for a get/put request **always contact other two nodes** and get a vote from each (i.e., an acknowledgement for a write, or a value for a read)

> f) For write operations, all objects are **versioned** in order to distinguish stale copies from the most recent copy

> g) For read operations, if the readers in the reader quorum have different versions of the same object, the coordinator **picks the most recent version** and returns it


**3. Failure handling**
> a) Handling failures is done very carefully because there can be many corner cases to consider and cover

> d) We cannot rely on socket creation or connect status to determine if a node has failed. Due to the Android emulator networking setup, it is not safe to rely on socket creation or connect status to judge node failures

> e) When a coordinator for a request fails and it does not respond to the request, **its successor can be contacted next for the request**


Testing
-------
The  [**Grader**](https://github.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/tree/master/Testing_Program) test our implementation rigorously in 6 different phases. Each testing phase is quite intensive. Refer [**Project Specifications**](https://docs.google.com/document/d/1iHtWvSE2pul7_OhcUMkZ9EMGn1IFhhPwwmz00TDLjP8/edit) for details: -  
**1. Testing basic ops**
> a) This phase will test basic operations, i.e., insert, query, delete, @, and *. This will test if everything is correctly replicated. There is no concurrency in operations and there is no failure either

**2. Testing concurrent ops with different keys**
> a) This phase will test if your implementation can handle concurrent operations under no failure

> b) The tester will use independent (key, value) pairs inserted/queried concurrently on all the nodes

**3. Testing concurrent ops with same keys**
> a) This phase will test if your implementation can handle concurrent operations with same keys under no failure

> b) The tester will use the same set of (key, value) pairs inserted/queried concurrently on all the nodes

**4. Testing one failure**
> a) This phase will test one failure with every operation

> b) One node will crash before operations start. After all the operations are done, the node will recover

> c) This will be repeated for each and every operation

**5. Testing concurrent operations with one failure**
> a) This phase will execute operations concurrently and crash one node in the middle of the execution. After some time, the failed node will also recover in the middle of the execution

**6. Testing concurrent operations with one consistent failure**
> a) This phase will crash one node at a time consistently, i.e., one node will crash then recover, and another node will crash and recover, etc.

> b) There will be a brief period of time in between the crash-recover sequence


Running the Grader/Testing Program
-----------------------------------------
> 1. Load the Project in Android Studio and create the [**apk file**](https://developer.android.com/studio/run/index.html)
> 2. Download  the [**Testing Program**](https://github.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/tree/master/Testing_Program) for your platform 
> 3. Before you run the program, please make sure that you are **running five AVDs**. The below command will do it: -
	- **python [run_avd.py](https://github.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/blob/master/Scripts/run_avd.py) 5**
> 4. Also make sure that the **Emulator Networking** setup is done. The below command will do it: -
	- **python [set_redir.py](https://github.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/blob/master/Scripts/set_redir.py) 10000**
> 5.  Run the grader: -
	- chmod +x ***simpledynamo-grading.osx***
    - ./simpledynamo-grading.osx ***apk file path***
> 6. You can specify which testing phase you want to test by providing **‘-p’** or ‘--phase’ argument to the tester
> 7. **Note:** If you run an individual phase with "-p", it will always be a fresh install. However if you run all phases (without "-p"), it will not always be a fresh install; the grader will do a fresh-install before phase 1, and do another fresh-install before phase 2. Afterwards, there will be no install. **This means that all data from previous phases will remain intact**
> 8. **‘-h’** argument will show you what options are available
> 9. The grader uses **multiple threads** to test your code and each thread will independently print out its own log messages. This means that an **error message might appear in the middle of the combined log messages from all threads, rather than at the end**


Credits
-------
This project contains scripts and other related material that is developed by [**Networked Systems Research Group**](https://nsr.cse.buffalo.edu)

I acknowledge and grateful to [**Professor Steve ko**](https://nsr.cse.buffalo.edu/?page_id=272) and [**TA Kyungho Jeon**](http://www.cse.buffalo.edu/~kyunghoj/) for their continuous support throughout the course that helped me learn the skills of Large Scale Distributed Systems and develop a simplified version of **Amazon Dynamo** - a highly available replicated key-value storage


Developer
---------
Ramanpreet Singh Khinda (rkhinda@buffalo.edu)</br>
[![website](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Resources/ic_website.png)](https://branded.me/ramanpreet1990)		[![googleplay](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Resources/ic_google_play.png)](https://play.google.com/store/apps/details?id=suny.buffalo.mis.research&hl=en)		[![linkedin](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Resources/ic_linkedin.png)](https://www.linkedin.com/in/ramanpreet1990)		[![twitter](https://raw.githubusercontent.com/ramanpreet1990/CSE_586_Simplified_Amazon_Dynamo/master/Resources/ic_linkedin.png)](https://twitter.com/dk_sunny1)

License
----------
Copyright {2016} 
{Ramanpreet Singh Khinda rkhinda@buffalo.edu} 

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

