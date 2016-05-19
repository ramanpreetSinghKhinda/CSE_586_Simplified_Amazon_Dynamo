package edu.buffalo.cse.cse486586.simpledynamo;

import android.net.Uri;

import java.lang.reflect.Array;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This file contains the protocols that we define for our Simple Dynamo
 */
public class Globals {
    public static final String TAG = "Raman";

    public static final String PREFS_NAME = "pref_simple_dynamo";
    public static final String IS_FRESH_INSTALL = "is_fresh_install";
    public static final String APP_VERSION = "appVersion";

    public static final String DYNAMO_MASTER_NODE = "5554";

    public static final String REMOTE_PORT0 = "11108";
    public static final String REMOTE_PORT1 = "11112";
    public static final String REMOTE_PORT2 = "11116";
    public static final String REMOTE_PORT3 = "11120";
    public static final String REMOTE_PORT4 = "11124";

    public static final String[] ARRAY_REMOTE_PORTS = {REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};

    public static final int SERVER_PORT = 10000;
    public static final int NUM_OF_DEVICES = 5;

    public static final String KEY_FIELD = "key";
    public static final String VALUE_FIELD = "value";

    public static final String LOCAL_QUERY = "@";
    public static final String GLOBAL_QUERY = "*";

    public static final String DYNAMO_RING_UPDATE_LISTENER = "next_prev_node_listener";
    public static final String TXT_PREV_NODE = "txt_prev_node";
    public static final String TXT_NEXT_NODE = "txt_next_node";
    public static final String TXT_DYNAMO_RING_NODES = "txt_dynamo_ring_nodes";

    public static final String TRUE = "true";
    public static final String FALSE = "false";

    public static final int TIMEOUT_VALUE = 3000;

    public static final String MSG_DYNAMO_RING_JOIN_WAIT = "-- Waiting to Join the Ring --";
    public static final String MSG_DYNAMO_RING_JOIN = "msg_dynamo_ring_join";
    public static final String MSG_DYNAMO_RING_UPDATE = "msg_dynamo_ring_update";

    public static final String MSG_INSERT_LOOKUP = "msg_insert_lookup";
    public static final String MSG_INSERT_LOOKUP_RESPONSE = "msg_insert_lookup_response";

    public static final String MSG_QUORUM_REPLICATION = "msg_quorum_replication";
    public static final String MSG_QUORUM_REPLICATION_RESPONSE = "msg_quorum_replication_response";

    public static final String MSG_FAILURE_RECOVERY = "msg_failure_recovery";
    public static final String MSG_LAST_NODE_JOINED = "msg_last_node_joined";

    // R + W > N
    public static final int QUORUM_REPLICATION_DEGREE = 3;
    public static final int READER_QUORUM_SIZE = 2;
    public static final int WRITER_QUORUM_SIZE = 2;


    public static final String MSG_QUERY_LOOKUP = "msg_query_lookup";
    public static final String MSG_QUERY_LOOKUP_RESPONSE = "msg_query_lookup_response";

    public static final String MSG_QUERY_LOOKUP_IN_REPLICA = "msg_query_lookup_in_replica";
    public static final String MSG_QUERY_LOOKUP_RESPONSE_FROM_REPLICA = "msg_query_lookup_response_from_replica";

    public static final String EMPTY_QUERY_RESPONSE = "empty_query_response";


    public static final String MSG_DELETE_LOOKUP = "msg_delete_lookup";
    public static final String MSG_DELETE_LOOKUP_RESPONSE = "msg_delete_lookup_response";

    public static final String MSG_DELETE_LOOKUP_IN_REPLICA = "msg_delete_lookup_in_replica";
    public static final String MSG_DELETE_LOOKUP_RESPONSE_FROM_REPLICA = "msg_delete_lookup_response_from_replica";


    public static final String MSG_GLOBAL_DUMP_REQUEST = "msg_global_dump_request";
    public static final String MSG_GLOBAL_DUMP_RESPONSE = "msg_global_dump_response";


    public static final String MSG_GLOBAL_DELETE_REQUEST = "msg_global_delete_request";
    public static final String MSG_GLOBAL_DELETE_RESPONSE = "msg_global_delete_response";


    public static final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledynamo.provider");

    public static Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    public static String genHash(String input) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] sha1Hash = sha1.digest(input.getBytes());
            Formatter formatter = new Formatter();
            for (byte b : sha1Hash) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    // Participating Nodes in the Dynamo Ring
    public static final String REMOTE_NODE_5562 = "5562";
    public static final String REMOTE_NODE_5556 = "5556";
    public static final String REMOTE_NODE_5554 = "5554";
    public static final String REMOTE_NODE_5558 = "5558";
    public static final String REMOTE_NODE_5560 = "5560";

    // Pre-Calculated Hash Values of the Nodes
    public static final String HASH_NODE_5562 = genHash(REMOTE_NODE_5562);
    public static final String HASH_NODE_5556 = genHash(REMOTE_NODE_5556);
    public static final String HASH_NODE_5554 = genHash(REMOTE_NODE_5554);
    public static final String HASH_NODE_5558 = genHash(REMOTE_NODE_5558);
    public static final String HASH_NODE_5560 = genHash(REMOTE_NODE_5560);

    // Dynamo Ring
    public static final String[] REPLICAS_GLOBAL = {REMOTE_NODE_5562, REMOTE_NODE_5556, REMOTE_NODE_5554, REMOTE_NODE_5558, REMOTE_NODE_5560};
    public static final ArrayList<String> LIST_REPLICAS_GLOBAL = new ArrayList<String>(Arrays.asList(REPLICAS_GLOBAL));


    // Decided Replicas for each node based on their hashed values
    public static final String[] REPLICAS_5562 = {REMOTE_NODE_5562, REMOTE_NODE_5556, REMOTE_NODE_5554};
    public static final String[] REPLICAS_5556 = {REMOTE_NODE_5556, REMOTE_NODE_5554, REMOTE_NODE_5558};
    public static final String[] REPLICAS_5554 = {REMOTE_NODE_5554, REMOTE_NODE_5558, REMOTE_NODE_5560};
    public static final String[] REPLICAS_5558 = {REMOTE_NODE_5558, REMOTE_NODE_5560, REMOTE_NODE_5562};
    public static final String[] REPLICAS_5560 = {REMOTE_NODE_5560, REMOTE_NODE_5562, REMOTE_NODE_5556};

    // Replica ArrayList which we are using in our Dynamo Provider
    public static final ArrayList<String> LIST_REPLICAS_5562 = new ArrayList<String>(Arrays.asList(REPLICAS_5562));
    public static final ArrayList<String> LIST_REPLICAS_5556 = new ArrayList<String>(Arrays.asList(REPLICAS_5556));
    public static final ArrayList<String> LIST_REPLICAS_5554 = new ArrayList<String>(Arrays.asList(REPLICAS_5554));
    public static final ArrayList<String> LIST_REPLICAS_5558 = new ArrayList<String>(Arrays.asList(REPLICAS_5558));
    public static final ArrayList<String> LIST_REPLICAS_5560 = new ArrayList<String>(Arrays.asList(REPLICAS_5560));


    // Successor and Predecessor of each node. This will be used for Failure Handling
    public static final String[] LOOKUP_5562 = {REMOTE_NODE_5560, REMOTE_NODE_5556};
    public static final String[] LOOKUP_5556 = {REMOTE_NODE_5562, REMOTE_NODE_5554};
    public static final String[] LOOKUP_5554 = {REMOTE_NODE_5556, REMOTE_NODE_5558};
    public static final String[] LOOKUP_5558 = {REMOTE_NODE_5554, REMOTE_NODE_5560};
    public static final String[] LOOKUP_5560 = {REMOTE_NODE_5558, REMOTE_NODE_5562};

    // Successor and Predecessor ArrayList which we are using in our Dynamo Provider
    public static final ArrayList<String> LIST_LOOKUP_5562 = new ArrayList<String>(Arrays.asList(LOOKUP_5562));
    public static final ArrayList<String> LIST_LOOKUP_5556 = new ArrayList<String>(Arrays.asList(LOOKUP_5556));
    public static final ArrayList<String> LIST_LOOKUP_5554 = new ArrayList<String>(Arrays.asList(LOOKUP_5554));
    public static final ArrayList<String> LIST_LOOKUP_5558 = new ArrayList<String>(Arrays.asList(LOOKUP_5558));
    public static final ArrayList<String> LIST_LOOKUP_5560 = new ArrayList<String>(Arrays.asList(LOOKUP_5560));

}

