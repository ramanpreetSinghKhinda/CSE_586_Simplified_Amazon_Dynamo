package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleDynamoProvider extends ContentProvider {
    /**
     * Database specific constant declarations
     */
    private SQLiteDatabase db;
    private Context mContext;

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "SimpleDynamo";
    private static final String TABLE_NAME = "PA_4";
    private static final String COL_KEY_FIELD = "key";
    private static final String COL_VALUE_FIELD = "value";

    // A string that defines the SQL statement for creating a table
    private static final String CREATE_DB_TABLE = " CREATE TABLE " +
            TABLE_NAME +
            " (" +
            COL_KEY_FIELD + " TEXT NOT NULL, " +
            COL_VALUE_FIELD + " TEXT NOT NULL);";


    private String MY_EMULATOR_NODE;
    private String FAILURE_NODE;

    private ConcurrentHashMap<RamanKey, ArrayList<String>> dynamoRingMap = new ConcurrentHashMap<RamanKey, ArrayList<String>>();

    // Used for Failure Handling
    private ConcurrentHashMap<String, ArrayList<String>> lookupMap = new ConcurrentHashMap<String, ArrayList<String>>();

    // This map acts as responder for the inserts
    private ConcurrentHashMap<String, RamanMessage> insertRequestMap = new ConcurrentHashMap<String, RamanMessage>();

    // This map acts as both requester as well as responder for the queries
    private ConcurrentHashMap<String, RamanMessage> queryRequestResponseMap = new ConcurrentHashMap<String, RamanMessage>();

    // This map acts as both requester as well as responder for the queries
    private ConcurrentHashMap<String, RamanMessage> deleteRequestResponseMap = new ConcurrentHashMap<String, RamanMessage>();

    private CountDownLatch failureRecoveryCountDownLatch;
    private CountDownLatch nodeJoinCountDownLatch;
    private CountDownLatch insertCountDownLatch;
    private CountDownLatch queryCountDownLatch;
    private CountDownLatch deleteCountDownLatch;

    private AtomicBoolean isInRecoveryMode = new AtomicBoolean(false);
    private AtomicBoolean isFreshInstall = new AtomicBoolean(false);

    /**
     * Helper class that actually creates and manages the provider's underlying data repository.
     */
    private static class RamanDatabaseHelper extends SQLiteOpenHelper {
        /**
         * Instantiates an open helper for the provider's SQLite data repository
         * Do not do database creation and upgrade here.
         */
        RamanDatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        /**
         * Creates the data repository.
         * This is called when the provider attempts to open the repository
         * and SQLite reports that it doesn't exist.
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            // Creates the Database table
            db.execSQL(CREATE_DB_TABLE);
            Log.v(Globals.TAG, "Raman Database Table created");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.v(Globals.TAG, "Raman upgrading Database from VERSION : " + oldVersion + " to VERSION : " + newVersion);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        Log.v(Globals.TAG, "***** Raman inside onCreate() START *****");

        mContext = getContext();

        /**
         * Creates a new helper object. This method always returns quickly.
         * Notice that the database itself isn't created or opened
         * until SQLiteOpenHelper.getWritableDatabase is called
         */
        RamanDatabaseHelper dbHelper = new RamanDatabaseHelper(mContext);

        /**
         * Create a write able database which will trigger its
         * creation if it doesn't already exist.
         */
        db = dbHelper.getWritableDatabase();

        /*
         * Calculate the port number that this AVD listens on.
         * Hack to get around the networking limitations of AVDs.
         */
        TelephonyManager tel = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

        MY_EMULATOR_NODE = String.valueOf((Integer.parseInt(portStr)));

        initializeDynamoRing();

        if (isFreshInstall()) {
            Log.v(Globals.TAG, "This is a fresh install");
            broadcastPresence();
            setSharedPref();
        } else {
            Log.v(Globals.TAG, "Node started in failure recovery mode");
            processFailureRecovery();
        }

        boolean isServerStarted = true;
        try {
            //Create a server socket as well as a thread (AsyncTask) that listens on the server port.
            ServerSocket serverSocket = new ServerSocket(Globals.SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            isServerStarted = false;
            Log.v(Globals.TAG, "Can't create a ServerSocket");
        }


        Log.v(Globals.TAG, "***** Raman inside onCreate() END. The created db is : " + db + " *****");
        return (db == null) ? false : true;
    }

    private void initializeDynamoRing() {
        dynamoRingMap.put(new RamanKey(Globals.HASH_NODE_5562, Globals.REMOTE_NODE_5562), Globals.LIST_REPLICAS_5562);
        dynamoRingMap.put(new RamanKey(Globals.HASH_NODE_5556, Globals.REMOTE_NODE_5556), Globals.LIST_REPLICAS_5556);
        dynamoRingMap.put(new RamanKey(Globals.HASH_NODE_5554, Globals.REMOTE_NODE_5554), Globals.LIST_REPLICAS_5554);
        dynamoRingMap.put(new RamanKey(Globals.HASH_NODE_5558, Globals.REMOTE_NODE_5558), Globals.LIST_REPLICAS_5558);
        dynamoRingMap.put(new RamanKey(Globals.HASH_NODE_5560, Globals.REMOTE_NODE_5560), Globals.LIST_REPLICAS_5560);

        lookupMap.put(Globals.REMOTE_NODE_5562, Globals.LIST_LOOKUP_5562);
        lookupMap.put(Globals.REMOTE_NODE_5556, Globals.LIST_LOOKUP_5556);
        lookupMap.put(Globals.REMOTE_NODE_5554, Globals.LIST_LOOKUP_5554);
        lookupMap.put(Globals.REMOTE_NODE_5558, Globals.LIST_LOOKUP_5558);
        lookupMap.put(Globals.REMOTE_NODE_5560, Globals.LIST_LOOKUP_5560);
    }

    private void broadcastPresence() {
        isFreshInstall.set(true);

        nodeJoinCountDownLatch = new CountDownLatch(1);

        if (MY_EMULATOR_NODE.equalsIgnoreCase(Globals.REMOTE_NODE_5562)) {
            RamanMessage ramanMessage = new RamanMessage(MY_EMULATOR_NODE, MY_EMULATOR_NODE, Globals.MSG_LAST_NODE_JOINED);
            ramanMessage.setReplicaList(Globals.LIST_REPLICAS_GLOBAL);

            notifyDynamoRing(ramanMessage);
        }
    }

    private void processFailureRecovery() {
        isInRecoveryMode.set(true);

        failureRecoveryCountDownLatch = new CountDownLatch(1);

        RamanMessage ramanMessage = new RamanMessage(MY_EMULATOR_NODE, MY_EMULATOR_NODE, Globals.MSG_FAILURE_RECOVERY);
        ramanMessage.setReplicaList(lookupMap.get(MY_EMULATOR_NODE));

        notifyDynamoRing(ramanMessage);
    }

    private String getCoordinator(final String hashKey) {
        RamanKey currentKey = null, firstKey = null;
        boolean updated = false, firstEntry = true;

        for (Map.Entry<RamanKey, ArrayList<String>> entry : dynamoRingMap.entrySet()) {
            currentKey = entry.getKey();

            if (firstEntry) {
                firstEntry = false;
                firstKey = currentKey;
            }

            if (hashKey.compareTo(currentKey.getHashNode()) < 0) {
                updated = true;
                break;
            }
        }

        if (updated) {
            return currentKey.getStrNode();
        } else {
            // Means hash key is larger than every node in the ring, so return the first node
            return firstKey.getStrNode();
        }

    }

    private ArrayList<String> getCoordinatorReplicas(final String coordinatorNode) {
        final RamanKey ramanCoordinatorKey = new RamanKey(Globals.genHash(coordinatorNode), coordinatorNode);

        return dynamoRingMap.get(ramanCoordinatorKey);
    }

    private Uri insertToDB(final Uri uri, final ContentValues values) {
        Uri contentUri = uri;

        // checking if a value with specified key already exists
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME);
        Cursor cursor = queryBuilder.query(db, null, COL_KEY_FIELD + "=?", new String[]{values.getAsString(COL_KEY_FIELD)}, null, null, null);

        if (cursor.moveToFirst()) {
            Log.v(Globals.TAG, "The specific KEY : " + values.getAsString(COL_KEY_FIELD) + " already exists hence only UPDATE the VALUE");

            db.update(TABLE_NAME, values, COL_KEY_FIELD + "=?", new String[]{values.getAsString(COL_KEY_FIELD)});

        } else {
            Log.v(Globals.TAG, "Inserting new KEY-VALUE pair");

            /**
             * Add a new record
             * @return the row ID of the newly inserted row, or -1 if an error occurred
             */
            long rowId = db.insert(TABLE_NAME, "", values);

            /**
             * If record is added successfully
             */
            if (rowId > 0) {
                /**
                 * Appends the given ID to the end of the path
                 * This is used to access a particular row in case
                 */
                contentUri = ContentUris.withAppendedId(uri, rowId);
            }
        }

        return contentUri;
    }

    private void holdOperations(String action) {
        if (isFreshInstall.get()) {
            Log.v(Globals.TAG, action + "() operation on hold as the Node : " + MY_EMULATOR_NODE + " is waiting for the Last Node 5562 to join the Dynamo Ring");

            try {
                nodeJoinCountDownLatch.await();  //Main thread is waiting on this CountDownLatch to be count down by another thread
                Log.v(Globals.TAG, "Got response for : " + Globals.MSG_LAST_NODE_JOINED + ". Main Thread Resumed. Application is starting now !!!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isFreshInstall.set(false);
            }
        }

        if (isInRecoveryMode.get()) {
            Log.v(Globals.TAG, action + "() operation on hold as the Node : " + MY_EMULATOR_NODE + " is recovering from Failure");

            try {
                failureRecoveryCountDownLatch.await();  //Main thread is waiting on this CountDownLatch to be count down by another thread
                Log.v(Globals.TAG, "Got response for : " + Globals.MSG_FAILURE_RECOVERY + ". Main Thread Resumed. Application is starting now !!!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isInRecoveryMode.set(false);
            }
        }

    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.v(Globals.TAG, "***** Raman inside insert() START. Uri : " + uri + " , ContentValues : " + values.toString() + " *****");

        holdOperations("insert");
        Uri contentUri = uri;

        if (null == values.getAsString(COL_KEY_FIELD) || values.getAsString(COL_KEY_FIELD).trim().length() == 0 || null == values.getAsString(COL_VALUE_FIELD) || values.getAsString(COL_VALUE_FIELD).trim().length() == 0) {
            Log.v(Globals.TAG, "***** Raman inside insert() END. Either Key or Value is Null or Empty *****");
            return contentUri;
        }

        String hashKey;
        String myMessage = Globals.MSG_INSERT_LOOKUP;
        synchronized (this) {
            hashKey = Globals.genHash(values.getAsString(COL_KEY_FIELD));
            final String coordinatorNode = getCoordinator(hashKey);


            if (MY_EMULATOR_NODE.equalsIgnoreCase(coordinatorNode)) {
                Log.v(Globals.TAG, "Coordinator Node : " + coordinatorNode + " is processing Quorum Replication");

            } else {
                Log.v(Globals.TAG, "Origin Node : " + MY_EMULATOR_NODE + " is processing Quorum Replication on behalf of the Coordinator Node : " + coordinatorNode);
            }

            RamanMessage ramanMessage = new RamanMessage(coordinatorNode, MY_EMULATOR_NODE, Globals.MSG_QUORUM_REPLICATION);
            ramanMessage.setKey(values.getAsString(COL_KEY_FIELD));
            ramanMessage.setValue(values.getAsString(COL_VALUE_FIELD));
            ramanMessage.setReplicaList(getCoordinatorReplicas(coordinatorNode));

            if (ramanMessage.getReplicaList().contains(MY_EMULATOR_NODE)) {
                contentUri = insertToDB(uri, values);
            }

            notifyDynamoRing(ramanMessage);
            insertCountDownLatch = new CountDownLatch(1);

            try {
                Log.v(Globals.TAG, "Waiting for : " + myMessage + " from all the associated nodes. Main Thread Paused...");
                insertCountDownLatch.await();  //Main thread is waiting on this CountDownLatch to be count down by another thread
                Log.v(Globals.TAG, "Got response for : " + myMessage + ". Main Thread Resumed. Application is starting now !!!");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.v(Globals.TAG, "***** Raman inside insert() END. Uri returned to the User/Requester Node is : " + contentUri + " *****");
        return contentUri;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Log.v(Globals.TAG, "***** Raman inside query() START. Uri : " + uri + " , Selection : " + selection + " *****");

        holdOperations("query");


        if (null == selection || selection.trim().length() == 0) {
            Log.v(Globals.TAG, "***** Raman inside query() END. Key is either Null or Empty String *****");
            return null;
        }

        // Sets the list of tables to query
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(TABLE_NAME);

        Cursor cursor = null;
        String myMessage = Globals.MSG_QUERY_LOOKUP;

        if (Globals.LOCAL_QUERY.equalsIgnoreCase(selection)) {
            // Return all <key, value> pairs stored in local partition of the node
            Log.v(Globals.TAG, "Running @ query");

            cursor = queryBuilder.query(db, projection, null, null, null, null, null);

            Log.v(Globals.TAG, "***** Raman inside query() END. Cursor returned to the Requester/User is : " + cursor + " *****");
            return cursor;
        }

        String[] columns = new String[]{COL_KEY_FIELD, COL_VALUE_FIELD};
        MatrixCursor matrixCursor = new MatrixCursor(columns);

        synchronized (this) {
            try {
                final String hashKey = Globals.genHash(selection);
                final String coordinatorNode = getCoordinator(hashKey);

                RamanMessage ramanMessage = new RamanMessage(coordinatorNode, MY_EMULATOR_NODE);
                ramanMessage.setReplicaList(getCoordinatorReplicas(coordinatorNode));

                myMessage = Globals.MSG_QUERY_LOOKUP_IN_REPLICA;

                if (Globals.GLOBAL_QUERY.equalsIgnoreCase(selection)) {
                    ramanMessage.setReplicaList(Globals.LIST_REPLICAS_GLOBAL);

                    //  Return all <key, value> pairs stored in the entire Dynamo Ring. Means run Local Query on all nodes
                    myMessage = Globals.MSG_GLOBAL_DUMP_REQUEST;
                    Log.v(Globals.TAG, "Origin Node : " + MY_EMULATOR_NODE + " is processing Global Dump");

                } else if (MY_EMULATOR_NODE.equalsIgnoreCase(coordinatorNode)) {
                    Log.v(Globals.TAG, "Coordinator Node : " + coordinatorNode + " is doing Query lookup in its Replicas");

                } else {
                    Log.v(Globals.TAG, "Origin Node : " + MY_EMULATOR_NODE + " is doing Query lookup in the Coordinator's Replicas on behalf of the Coordinator Node : " + coordinatorNode);
                }

                ramanMessage.setMessage(myMessage);
                ramanMessage.setKey(selection);

                notifyDynamoRing(ramanMessage);

                queryCountDownLatch = new CountDownLatch(1);

                try {
                    Log.v(Globals.TAG, "Waiting for response for successful " + myMessage + " from all the associated nodes. Main Thread Paused...");

                    queryCountDownLatch.await();  //main thread is waiting on CountDownLatch to be count down by another thread
                    Log.v(Globals.TAG, "Got response for " + myMessage + ". Main Thread Resumed. Application is starting now !!!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                if (Globals.GLOBAL_QUERY.equalsIgnoreCase(selection)) {
                    cursor = queryBuilder.query(db, projection, null, null, null, null, null);

                    // Return all key-value pairs separated by a colon ":" where individual key-value pair is separated by a comma ","
                    String queryResponse = getKeyValueFromCursor(cursor, true);

                    if (!(null == queryResponse || "".equalsIgnoreCase(queryResponse) || " ".equalsIgnoreCase(queryResponse))) {
                        // Separate all Key-Value pairs
                        String[] response = queryResponse.split(" : ");

                        for (String keyValuePair : response) {
                            String key = keyValuePair.split(",")[0];
                            String value = keyValuePair.split(",")[1];

                            queryRequestResponseMap.get(selection).addQueryResponse(key, value);
                        }
                    }

                    for (Map.Entry<String, String> entry : queryRequestResponseMap.get(selection).getQueryResponseMap().entrySet()) {
                        matrixCursor.addRow(new Object[]{entry.getKey(), entry.getValue()});
                    }
                } else {
                    String finalResponse = null;

                    if (ramanMessage.getReplicaList().contains(MY_EMULATOR_NODE)) {
                        //  Return a particular <key, value> pair represented by the selection
                        cursor = queryBuilder.query(db, projection, COL_KEY_FIELD + "=?", new String[]{selection}, null, null, sortOrder);

                        // Return key value pair separated by a comma ","
                        String queryResponse = getKeyValueFromCursor(cursor, false);

                        if (!(null == queryResponse || "".equalsIgnoreCase(queryResponse) || " ".equalsIgnoreCase(queryResponse))) {
                            finalResponse = queryResponse.split(",")[1];
                        }
                    }

                    //Todo check versioning and pick the higher version object
                    for (Map.Entry<String, String> entry : queryRequestResponseMap.get(selection).getQueryResponseMap().entrySet()) {
                        finalResponse = entry.getValue();
                    }

                    if (null == finalResponse) {
                        matrixCursor = null;
                    } else {
                        matrixCursor.addRow(new Object[]{selection, finalResponse});
                    }
                }
            } finally {
                Log.v(Globals.TAG, "***** Raman inside query() END. Cursor returned to the User is : " + matrixCursor + " *****");
                return matrixCursor;
            }
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.v(Globals.TAG, "***** Raman inside delete() START. Uri : " + uri + " , Selection : " + selection + " *****");

        holdOperations("delete");


        if (null == selection || selection.trim().length() == 0) {
            Log.v(Globals.TAG, "***** Raman inside delete() END. Key is either Null or Empty String *****");
            return -1;
        }

        int rowsDeleted = -1;
        boolean canDelete;
        String myMessage = Globals.MSG_DELETE_LOOKUP;

        if (Globals.LOCAL_QUERY.equalsIgnoreCase(selection)) {
            // Delete all <key, value> pairs stored in local partition of the node
            Log.v(Globals.TAG, "Running @ delete query");

            rowsDeleted = db.delete(TABLE_NAME, null, null);

            Log.v(Globals.TAG, "****** Raman inside delete() END. Rows deleted : " + rowsDeleted + ". Returning response to the Requester/User *****");
            return rowsDeleted;
        }
        synchronized (this) {
            int finalRowsDeleted = 0;

            try {
                final String hashKey = Globals.genHash(selection);
                final String coordinatorNode = getCoordinator(hashKey);

                RamanMessage ramanMessage = new RamanMessage(coordinatorNode, MY_EMULATOR_NODE);
                ramanMessage.setReplicaList(getCoordinatorReplicas(coordinatorNode));

                myMessage = Globals.MSG_DELETE_LOOKUP_IN_REPLICA;

                if (Globals.GLOBAL_QUERY.equalsIgnoreCase(selection)) {
                    ramanMessage.setReplicaList(Globals.LIST_REPLICAS_GLOBAL);

                    //  Delete all <key, value> pairs stored in the entire Dynamo Ring. Means run Local Delete on all nodes
                    myMessage = Globals.MSG_GLOBAL_DELETE_REQUEST;
                    Log.v(Globals.TAG, "Origin Node : " + MY_EMULATOR_NODE + " is processing Global Delete");

                } else if (MY_EMULATOR_NODE.equalsIgnoreCase(coordinatorNode)) {
                    Log.v(Globals.TAG, "Coordinator Node : " + coordinatorNode + " is doing Delete lookup in its Replicas");

                } else {
                    Log.v(Globals.TAG, "Origin Node : " + MY_EMULATOR_NODE + " is doing Delete lookup in the Coordinator's Replicas on behalf of the Coordinator Node : " + coordinatorNode);
                }

                ramanMessage.setMessage(myMessage);
                ramanMessage.setKey(selection);

                notifyDynamoRing(ramanMessage);
                deleteCountDownLatch = new CountDownLatch(1);

                try {
                    Log.v(Globals.TAG, "Waiting for response for successful " + myMessage + " from all the associated nodes. Main Thread Paused...");

                    deleteCountDownLatch.await();  //main thread is waiting on CountDownLatch to be count down by another thread
                    Log.v(Globals.TAG, "Got response for " + myMessage + ". Main Thread Resumed. Application is starting now !!!");

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (Globals.GLOBAL_QUERY.equalsIgnoreCase(selection)) {
                    finalRowsDeleted = deleteRequestResponseMap.get(selection).getRowsDeleted();

                    // Delete all <key, value> pairs stored in local partition of the node
                    rowsDeleted = db.delete(TABLE_NAME, null, null);
                    finalRowsDeleted += rowsDeleted;

                } else {
                    //Todo check versioning and pick the higher version object
                    finalRowsDeleted = deleteRequestResponseMap.get(selection).getRowsDeleted();

                    if (ramanMessage.getReplicaList().contains(MY_EMULATOR_NODE)) {
                        //  Delete a particular <key, value> pair represented by the selection
                        rowsDeleted = db.delete(TABLE_NAME, COL_KEY_FIELD + "=?", new String[]{selection});

                        if (finalRowsDeleted < rowsDeleted) {
                            finalRowsDeleted = rowsDeleted;
                        }
                    }

                }
            } finally {
                Log.v(Globals.TAG, "****** Raman inside delete() END. Rows deleted : " + finalRowsDeleted + ". Returning response to the User *****");
                return finalRowsDeleted;
            }
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[]
            selectionArgs) {
        Log.v(Globals.TAG, "***** Raman inside update() :-  Uri : " + uri + " , ContentValues : " + values.toString() + " , Selection : " + selection + " *****");

        return 0;
    }

    @Override
    public String getType(Uri uri) {
        Log.v(Globals.TAG, "****** Raman inside getType(). Uri : " + uri + " *****");

        return null;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket socket = null;

            while (true) {
                try {
                    Log.v(Globals.TAG, "Server is listening on the node : " + MY_EMULATOR_NODE);

                    socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);

                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    final String msg = bufferedReader.readLine();
                    final String receivedMsg[] = msg.split(" : ");

                    final String coordinatorNode = receivedMsg[0];
                    final String senderNode = receivedMsg[1];
                    final String receiverNode = receivedMsg[2];
                    final String message = receivedMsg[3];

                    /**
                     * This is "Wake up early" mode, which means wakeup as soon get message from any of the node in the ring
                     * This will help to maintain consistency during concurrent operation that happen even before all nodes are up
                     *
                     * Last Node 5562 is the in-charge of sending wake up message to all the nodes,
                     * but there may be a case when some nodes have still not joined the ring, and the message will not reach to the receiver
                     * So in that case this latch will help
                     */
                    if (null != nodeJoinCountDownLatch && nodeJoinCountDownLatch.getCount() == 1) {
                        nodeJoinCountDownLatch.countDown();
                    }

                    Log.v(Globals.TAG, "Received msg :- Coordinator Node : " + coordinatorNode + ", Sender Node : " + senderNode + ", Receiver Node : " + receiverNode + ", Message : " + message);

                    if (Globals.MSG_QUORUM_REPLICATION.equalsIgnoreCase(message)) {
                        ContentValues cv = new ContentValues();

                        final String key = receivedMsg[4];
                        final String value = receivedMsg[5];

                        cv.put(COL_KEY_FIELD, key);
                        cv.put(COL_VALUE_FIELD, value);

                        holdOperations("server insert");
                        Uri resultUri = insertToDB(Globals.mUri, cv);

                        if (null == resultUri) {
                            Log.v(Globals.TAG, "Replica Node : " + MY_EMULATOR_NODE + " could not insert the key-value pair : (" + key + ", " + value + ")");
                        }

                        OutputStream outputStream = socket.getOutputStream();
                        PrintWriter printWriter = new PrintWriter(outputStream, true);

                        printWriter.println(MY_EMULATOR_NODE);
                        Log.v(Globals.TAG, "Replica Node : " + MY_EMULATOR_NODE + " sent Quorum Replication Success Response back to the Origin/Coordinator Node : " + senderNode);

                        printWriter.close();

                    } else if (Globals.MSG_LAST_NODE_JOINED.equalsIgnoreCase(message)) {
                        if (null != nodeJoinCountDownLatch && nodeJoinCountDownLatch.getCount() == 1) {
                            nodeJoinCountDownLatch.countDown();
                        }

                    } else if (Globals.MSG_QUERY_LOOKUP_IN_REPLICA.equalsIgnoreCase(message)) {
                        final String key = receivedMsg[4];

                        // Sets the list of tables to query
                        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
                        queryBuilder.setTables(TABLE_NAME);

                        holdOperations("server query");

                        Cursor cursor = queryBuilder.query(db, null, COL_KEY_FIELD + "=?", new String[]{key}, null, null, null);
                        String queryResponse;

                        OutputStream outputStream = socket.getOutputStream();
                        PrintWriter printWriter = new PrintWriter(outputStream, true);

                        if (null == cursor) {
                            Log.v(Globals.TAG, "Replica Node : " + MY_EMULATOR_NODE + " could not find the value for the specific key : " + key + " in its DB");

                            // Return Empty key value pair
                            queryResponse = Globals.EMPTY_QUERY_RESPONSE;
                        } else {
                            // Return key value pair separated by a comma ","
                            queryResponse = getKeyValueFromCursor(cursor, false);

                            if (null == queryResponse || "".equalsIgnoreCase(queryResponse) || " ".equalsIgnoreCase(queryResponse)) {
                                queryResponse = Globals.EMPTY_QUERY_RESPONSE;
                            }
                        }

                        printWriter.println(queryResponse);
                        Log.v(Globals.TAG, "Replica Node : " + MY_EMULATOR_NODE + " sent Query lookup Success Response : " + queryResponse + " back to the Origin/Coordinator Node : " + senderNode);

                        printWriter.close();

                    } else if (Globals.MSG_DELETE_LOOKUP_IN_REPLICA.equalsIgnoreCase(message)) {
                        final String key = receivedMsg[4];

                        holdOperations("server delete");

                        int rowsDeleted = db.delete(TABLE_NAME, COL_KEY_FIELD + "=?", new String[]{key});

                        if (-1 == rowsDeleted) {
                            rowsDeleted = 0;
                            Log.v(Globals.TAG, "Replica Node : " + MY_EMULATOR_NODE + " failed to perform the delete operation for the specific key : " + key);
                        }

                        OutputStream outputStream = socket.getOutputStream();
                        PrintWriter printWriter = new PrintWriter(outputStream, true);

                        printWriter.println(rowsDeleted + "");
                        Log.v(Globals.TAG, "Replica Node : " + MY_EMULATOR_NODE + " sent Delete lookup Success Response back to the Origin/Coordinator Node : " + senderNode + ". Rows deleted : " + rowsDeleted);

                        printWriter.close();

                    } else if (Globals.MSG_GLOBAL_DUMP_REQUEST.equalsIgnoreCase(message) || Globals.MSG_FAILURE_RECOVERY.equalsIgnoreCase(message)) {
                        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
                        queryBuilder.setTables(TABLE_NAME);

                        holdOperations("server global query");

                        Cursor cursor = queryBuilder.query(db, null, null, null, null, null, null);
                        String queryResponse;

                        if (null == cursor) {
                            Log.v(Globals.TAG, "Node : " + MY_EMULATOR_NODE + " could not find any data in its DB");
                            queryResponse = Globals.EMPTY_QUERY_RESPONSE;
                        } else {
                            // Return all key-value pairs separated by a colon ":" where individual key-value pair is separated by a comma ","
                            queryResponse = getKeyValueFromCursor(cursor, true);

                            if (null == queryResponse || "".equalsIgnoreCase(queryResponse) || " ".equalsIgnoreCase(queryResponse)) {
                                queryResponse = Globals.EMPTY_QUERY_RESPONSE;
                            }
                        }

                        OutputStream outputStream = socket.getOutputStream();
                        PrintWriter printWriter = new PrintWriter(outputStream, true);

                        printWriter.println(queryResponse);
                        Log.v(Globals.TAG, "Node : " + MY_EMULATOR_NODE + " sent " + message + " Response : " + queryResponse + " back to the Origin/Coordinator Node : " + senderNode);

                        printWriter.close();

                    } else if (Globals.MSG_GLOBAL_DELETE_REQUEST.equalsIgnoreCase(message)) {
                        holdOperations("server global delete");

                        int rowsDeleted = db.delete(TABLE_NAME, null, null);

                        if (-1 == rowsDeleted) {

                            rowsDeleted = 0;
                            Log.v(Globals.TAG, "Node : " + MY_EMULATOR_NODE + " failed to perform the delete operation");
                        }

                        OutputStream outputStream = socket.getOutputStream();
                        PrintWriter printWriter = new PrintWriter(outputStream, true);

                        printWriter.println(rowsDeleted + "");
                        Log.v(Globals.TAG, "Node : " + MY_EMULATOR_NODE + " sent Global Delete Success Response back to the Origin/Coordinator Node : " + senderNode + ". Rows deleted : " + rowsDeleted);

                        printWriter.close();
                    }
                } catch (SocketTimeoutException e) {
                    Log.v(Globals.TAG, "ServerTask SocketTimeoutException");

                } catch (IOException e) {
                    Log.v(Globals.TAG, "Error in Server Task IO Exception");

                } finally {
                    if (null != socket && !socket.isClosed()) {
                        Log.v(Globals.TAG, "Closing the socket accepted by the Server");
                        try {
                            socket.close();
                        } catch (IOException e) {
                            Log.v(Globals.TAG, "Error in Server Task IO Exception when closing socket");
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private String getKeyValueFromCursor(Cursor cursor, boolean isGlobalDump) {
        CopyOnWriteArrayList<String> keyValueList = new CopyOnWriteArrayList<String>();

        try {
            if (cursor == null) {
                Log.v(Globals.TAG, "Passed cursor is null");
                throw new Exception();
            }

            if (cursor.moveToFirst()) {
                do {
                    int keyIndex = cursor.getColumnIndex(COL_KEY_FIELD);
                    int valueIndex = cursor.getColumnIndex(COL_VALUE_FIELD);

                    if (keyIndex == -1 || valueIndex == -1) {
                        Log.v(Globals.TAG, "Wrong columns");
                        throw new Exception();
                    } else {
                        String strKey = cursor.getString(keyIndex);
                        String strValue = cursor.getString(valueIndex);

                        keyValueList.add(strKey + "," + strValue);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != cursor && !cursor.isClosed()) {
                cursor.close();
            }

            int size = keyValueList.size();

            if (isGlobalDump) {
                int counter = 0;
                StringBuffer keyValueBuffer = new StringBuffer();

                for (String keyValuesPair : keyValueList) {
                    ++counter;
                    keyValueBuffer.append(keyValuesPair);
                    if (counter != size) {
                        keyValueBuffer.append(" : ");
                    }
                }
                return keyValueBuffer.toString();

            } else {
                if (size > 1) {
                    Log.v(Globals.TAG, "Cursor have more than 1 Key-Value pairs for single key query. Value returned may not be correct");
                    return keyValueList.get(size - 1);

                } else if (size <= 0) {
                    Log.v(Globals.TAG, "Cursor have No Key-Value pairs for the specific request");
                    return null;

                } else {
                    return keyValueList.get(0);
                }

            }
        }
    }

    private boolean isMyNodeReplica(final String key) {
        final String hashKey = Globals.genHash(key);
        final String coordinatorNode = getCoordinator(hashKey);

        if (getCoordinatorReplicas(coordinatorNode).contains(MY_EMULATOR_NODE)) {
            return true;
        } else {
            return false;
        }
    }

    public void notifyDynamoRing(final RamanMessage ramanMessage) {
        Log.v(Globals.TAG, "Sending message : " + ramanMessage.getMessage());
        new ClientTask(ramanMessage).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {
        private final RamanMessage ramanMessage;

        ClientTask(final RamanMessage ramanMessage) {
            this.ramanMessage = ramanMessage;
        }

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                if (Globals.MSG_QUORUM_REPLICATION.equalsIgnoreCase(ramanMessage.getMessage())) {
                    final int numOfReplica = ramanMessage.getReplicaList().size();
                    Socket[] sockets = new Socket[numOfReplica];

                    for (int i = 0; i < numOfReplica; i++) {
                        final String receiverNode = ramanMessage.getReplicaList().get(i);

                        if (MY_EMULATOR_NODE.equalsIgnoreCase(receiverNode)) {
                            // To improve network traffic do not send message to my node
                            Log.v(Globals.TAG, "Got dummy response for " + ramanMessage.getMessage() + " from Coordinator Node : " + receiverNode);
                        } else {
                            sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    (Integer.parseInt(receiverNode) * 2));
                            sockets[i].setTcpNoDelay(true);

                            OutputStream outputStream = sockets[i].getOutputStream();
                            PrintWriter printWriter = new PrintWriter(outputStream, true);

                            printWriter.println(ramanMessage.getCoordinatorNode() + " : " + ramanMessage.getSenderNode() + " : " + receiverNode + " : " + ramanMessage.getMessage() + " : " + ramanMessage.getKey() + " : " + ramanMessage.getValue());
                            Log.v(Globals.TAG, "Sent msg :-  Coordinator Node : " + ramanMessage.getCoordinatorNode() + ", Sender Node : " + ramanMessage.getSenderNode() + ", Receiver Node : " + receiverNode + ", Message : " + ramanMessage.getMessage() + ", Key : " + ramanMessage.getKey() + ", Value : " + ramanMessage.getValue());

                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(sockets[i].getInputStream()));
                            final String senderNode = bufferedReader.readLine();

                            Log.v(Globals.TAG, "Got response for " + ramanMessage.getMessage() + " from Replica Node : " + receiverNode);
                            if (null == senderNode) {
                                Log.v(Globals.TAG, "Replica Node : " + receiverNode + " detected to be failed");
                                FAILURE_NODE = receiverNode;
                            }

                            printWriter.close();
                            sockets[i].close();
                        }
                    }

                    if (MY_EMULATOR_NODE.equalsIgnoreCase(ramanMessage.getCoordinatorNode())) {
                        Log.v(Globals.TAG, "Notifying insertCountDownLatch of the Coordinator Node " + ramanMessage.getCoordinatorNode() + " to resume its paused Main or Server thread");
                    } else {
                        Log.v(Globals.TAG, "Notifying insertCountDownLatch of the Origin Node " + MY_EMULATOR_NODE + " who processed replication request on behalf of Coordinator Node : " + ramanMessage.getCoordinatorNode() + " to resume its paused Main thread");
                    }

                    insertCountDownLatch.countDown();

                } else if (Globals.MSG_LAST_NODE_JOINED.equalsIgnoreCase(ramanMessage.getMessage())) {

                    final int numOfReplica = ramanMessage.getReplicaList().size();
                    Socket[] sockets = new Socket[numOfReplica];

                    for (int i = 0; i < numOfReplica; i++) {
                        final String receiverNode = ramanMessage.getReplicaList().get(i);

                        if (Globals.REMOTE_NODE_5562.equalsIgnoreCase(receiverNode)) {
                            // To improve network traffic do not send message to my node
                            Log.v(Globals.TAG, "Got dummy response for " + ramanMessage.getMessage() + " from My Node : " + receiverNode);

                        } else {
                            sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    (Integer.parseInt(receiverNode) * 2));
                            sockets[i].setTcpNoDelay(true);

                            OutputStream outputStream = sockets[i].getOutputStream();
                            PrintWriter printWriter = new PrintWriter(outputStream, true);

                            printWriter.println(ramanMessage.getCoordinatorNode() + " : " + ramanMessage.getSenderNode() + " : " + receiverNode + " : " + ramanMessage.getMessage());
                            Log.v(Globals.TAG, "Sent msg :-  Coordinator Node : " + ramanMessage.getCoordinatorNode() + ", Sender Node : " + ramanMessage.getSenderNode() + ", Receiver Node : " + receiverNode + ", Message : " + ramanMessage.getMessage());

                            printWriter.close();
                            sockets[i].close();
                        }
                    }

                    Log.v(Globals.TAG, "Notifying nodeJoinCountDownLatch of the Last Node " + MY_EMULATOR_NODE + " to resume its paused Main or Server thread");

                    nodeJoinCountDownLatch.countDown();

                } else if (Globals.MSG_FAILURE_RECOVERY.equalsIgnoreCase(ramanMessage.getMessage())) {

                    final int numOfReplica = ramanMessage.getReplicaList().size();
                    Socket[] sockets = new Socket[numOfReplica];

                    for (int i = 0; i < numOfReplica; i++) {
                        final String receiverNode = ramanMessage.getReplicaList().get(i);

                        sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                (Integer.parseInt(receiverNode) * 2));
                        sockets[i].setTcpNoDelay(true);

                        OutputStream outputStream = sockets[i].getOutputStream();
                        PrintWriter printWriter = new PrintWriter(outputStream, true);

                        printWriter.println(ramanMessage.getCoordinatorNode() + " : " + ramanMessage.getSenderNode() + " : " + receiverNode + " : " + ramanMessage.getMessage());
                        Log.v(Globals.TAG, "Sent msg :-  Coordinator Node : " + ramanMessage.getCoordinatorNode() + ", Sender Node : " + ramanMessage.getSenderNode() + ", Receiver Node : " + receiverNode + ", Message : " + ramanMessage.getMessage());

                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(sockets[i].getInputStream()));
                        final String queryResponse = bufferedReader.readLine();

                        // if queryResponse is NULL, than than means the receiver node is not up yet
                        Log.v(Globals.TAG, "Got response : " + queryResponse + " for : " + ramanMessage.getMessage() + " from Replica Node : " + receiverNode);

                        if (null != queryResponse && !Globals.EMPTY_QUERY_RESPONSE.equalsIgnoreCase(queryResponse)) {
                            // Separate all Key-Value pairs
                            String[] response = queryResponse.split(" : ");

                            for (String keyValuePair : response) {
                                String key = keyValuePair.split(",")[0];
                                String value = keyValuePair.split(",")[1];

                                if (isMyNodeReplica(key)) {
                                    Log.v(Globals.TAG, "Failure Node : " + MY_EMULATOR_NODE + " inserting the lost key-value pair : (" + key + ", " + value + ")");

                                    ContentValues cv = new ContentValues();
                                    cv.put(COL_KEY_FIELD, key);
                                    cv.put(COL_VALUE_FIELD, value);

                                    Uri resultUri = insertToDB(Globals.mUri, cv);

                                    if (null == resultUri) {
                                        Log.v(Globals.TAG, "Insertion Failed");
                                    }
                                }
                            }
                        }

                        printWriter.close();
                        sockets[i].close();

                    }

                    Log.v(Globals.TAG, "Notifying failureRecoveryCountDownLatch of the Failed Node " + MY_EMULATOR_NODE + " to resume its paused Main or Server thread");

                    failureRecoveryCountDownLatch.countDown();

                } else if (Globals.MSG_QUERY_LOOKUP_IN_REPLICA.equalsIgnoreCase(ramanMessage.getMessage())
                        || Globals.MSG_GLOBAL_DUMP_REQUEST.equalsIgnoreCase(ramanMessage.getMessage())) {

                    final int numOfReplica = ramanMessage.getReplicaList().size();
                    Socket[] sockets = new Socket[numOfReplica];

                    RamanMessage responseRamanMessage = new RamanMessage(ramanMessage.getCoordinatorNode(), ramanMessage.getSenderNode(), ramanMessage.getMessage());

                    for (int i = 0; i < numOfReplica; i++) {
                        final String receiverNode = ramanMessage.getReplicaList().get(i);

                        if (MY_EMULATOR_NODE.equalsIgnoreCase(receiverNode)) {
                            // To improve network traffic do not send message to my node
                            Log.v(Globals.TAG, "Got dummy response for " + ramanMessage.getMessage() + " from Coordinator Node : " + receiverNode);

                        } else {
                            sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    (Integer.parseInt(receiverNode) * 2));
                            sockets[i].setTcpNoDelay(true);

                            OutputStream outputStream = sockets[i].getOutputStream();
                            PrintWriter printWriter = new PrintWriter(outputStream, true);

                            printWriter.println(ramanMessage.getCoordinatorNode() + " : " + ramanMessage.getSenderNode() + " : " + receiverNode + " : " + ramanMessage.getMessage() + " : " + ramanMessage.getKey());
                            Log.v(Globals.TAG, "Sent msg :-  Coordinator Node : " + ramanMessage.getCoordinatorNode() + ", Sender Node : " + ramanMessage.getSenderNode() + ", Receiver Node : " + receiverNode + ", Message : " + ramanMessage.getMessage() + ", Key : " + ramanMessage.getKey());

                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(sockets[i].getInputStream()));
                            final String queryResponse = bufferedReader.readLine();

                            // if queryResponse is NULL, than than means the receiver node is not up yet
                            Log.v(Globals.TAG, "Got response : " + queryResponse + " for : " + ramanMessage.getMessage() + " from Replica Node : " + receiverNode);

                            if (null != queryResponse && !Globals.EMPTY_QUERY_RESPONSE.equalsIgnoreCase(queryResponse)) {
                                if (Globals.MSG_GLOBAL_DUMP_REQUEST.equalsIgnoreCase(ramanMessage.getMessage())) {
                                    // Separate all Key-Value pairs
                                    String[] response = queryResponse.split(" : ");

                                    for (String keyValuePair : response) {
                                        String key = keyValuePair.split(",")[0];
                                        String value = keyValuePair.split(",")[1];

                                        responseRamanMessage.addQueryResponse(key, value);
                                    }
                                } else {
                                    String key = queryResponse.split(",")[0];
                                    String value = queryResponse.split(",")[1];

                                    responseRamanMessage.addQueryResponse(key, value);
                                }
                            }

                            printWriter.close();
                            sockets[i].close();
                        }
                    }

                    queryRequestResponseMap.put(ramanMessage.getKey(), responseRamanMessage);

                    if (MY_EMULATOR_NODE.equalsIgnoreCase(ramanMessage.getCoordinatorNode())) {
                        Log.v(Globals.TAG, "Notifying queryCountDownLatch of the Coordinator Node " + MY_EMULATOR_NODE + " to resume its paused Main or Server thread");
                    } else {
                        Log.v(Globals.TAG, "Notifying queryCountDownLatch of the Origin Node " + MY_EMULATOR_NODE + " who processed the Query request on behalf of Coordinator Node : " + ramanMessage.getCoordinatorNode() + " to resume its paused Main thread");
                    }

                    queryCountDownLatch.countDown();

                } else if (Globals.MSG_DELETE_LOOKUP_IN_REPLICA.equalsIgnoreCase(ramanMessage.getMessage())
                        || Globals.MSG_GLOBAL_DELETE_REQUEST.equalsIgnoreCase(ramanMessage.getMessage())) {

                    final int numOfReplica = ramanMessage.getReplicaList().size();
                    Socket[] sockets = new Socket[numOfReplica];

                    RamanMessage responseRamanMessage = new RamanMessage(ramanMessage.getCoordinatorNode(), ramanMessage.getSenderNode(), ramanMessage.getMessage());
                    int rowsDeleted = 0;

                    for (int i = 0; i < numOfReplica; i++) {
                        final String receiverNode = ramanMessage.getReplicaList().get(i);

                        if (MY_EMULATOR_NODE.equalsIgnoreCase(receiverNode)) {
                            // To improve network traffic do not send message to my node

                            Log.v(Globals.TAG, "Got dummy response for " + ramanMessage.getMessage() + " from Coordinator Node : " + receiverNode);

                        } else {
                            sockets[i] = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    (Integer.parseInt(receiverNode) * 2));
                            sockets[i].setTcpNoDelay(true);

                            OutputStream outputStream = sockets[i].getOutputStream();
                            PrintWriter printWriter = new PrintWriter(outputStream, true);

                            printWriter.println(ramanMessage.getCoordinatorNode() + " : " + ramanMessage.getSenderNode() + " : " + receiverNode + " : " + ramanMessage.getMessage() + " : " + ramanMessage.getKey());
                            Log.v(Globals.TAG, "Sent msg :-  Coordinator Node : " + ramanMessage.getCoordinatorNode() + ", Sender Node : " + ramanMessage.getSenderNode() + ", Receiver Node : " + receiverNode + ", Message : " + ramanMessage.getMessage() + ", Key : " + ramanMessage.getKey());

                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(sockets[i].getInputStream()));
                            final String queryResponse = bufferedReader.readLine();

                            // if queryResponse is NULL, than than means the receiver node is not up yet
                            Log.v(Globals.TAG, "Got response : " + queryResponse + " for : " + ramanMessage.getMessage() + " from Replica Node : " + receiverNode);

                            if (null != queryResponse) {
                                final int nextRowsDeleted = Integer.parseInt(queryResponse);


                                if (Globals.MSG_GLOBAL_DELETE_REQUEST.equalsIgnoreCase(ramanMessage.getMessage())) {
                                    rowsDeleted += nextRowsDeleted;

                                } else {
                                    if (rowsDeleted < nextRowsDeleted) {
                                        rowsDeleted = nextRowsDeleted;
                                    }
                                }

                                printWriter.close();
                                sockets[i].close();
                            }
                        }

                    }

                    responseRamanMessage.setRowsDeleted(rowsDeleted);
                    deleteRequestResponseMap.put(ramanMessage.getKey(), responseRamanMessage);

                    if (MY_EMULATOR_NODE.equalsIgnoreCase(ramanMessage.getCoordinatorNode())) {
                        Log.v(Globals.TAG, "Notifying deleteCountDownLatch of the Coordinator Node " + MY_EMULATOR_NODE + " to resume its paused Main or Server thread");
                    } else {
                        Log.v(Globals.TAG, "Notifying deleteCountDownLatch of the Origin Node " + MY_EMULATOR_NODE + " who processed the Delete request on behalf of Coordinator Node : " + ramanMessage.getCoordinatorNode() + " to resume its paused Main thread");
                    }

                    deleteCountDownLatch.countDown();

                }

            } catch (UnknownHostException e) {
                Log.v(Globals.TAG, "ClientTask UnknownHostException");
            } catch (SocketTimeoutException e) {
                Log.v(Globals.TAG, "ClientTask UnknownHostException");
            } catch (EOFException e) {
                Log.v(Globals.TAG, "ClientTask EOFException");
            } catch (IOException e) {
                Log.v(Globals.TAG, "ClientTask IOException");
            }

            return null;
        }

    }

    private boolean isFreshInstall() {
        final SharedPreferences prefs = mContext.getSharedPreferences(Globals.PREFS_NAME, Context.MODE_PRIVATE);

        return prefs.getBoolean(Globals.IS_FRESH_INSTALL, true);
    }

    private void setSharedPref() {
        final SharedPreferences prefs = mContext.getSharedPreferences(Globals.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean(Globals.IS_FRESH_INSTALL, false);
        editor.commit();
    }
}