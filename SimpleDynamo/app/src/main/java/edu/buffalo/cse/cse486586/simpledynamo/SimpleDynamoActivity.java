package edu.buffalo.cse.cse486586.simpledynamo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

class RamanKey {
    private String hashNode;
    private String strNode;

    RamanKey(String hashNode, String strNode) {
        this.hashNode = hashNode;
        this.strNode = strNode;
    }

    public void setHashNode(String hashNode) {
        this.hashNode = hashNode;
    }

    public void setStrNode(String strNode) {
        this.strNode = strNode;
    }

    public String getHashNode() {
        return hashNode;
    }

    public String getStrNode() {
        return strNode;
    }

    public int hashCode() {
        return hashNode.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof RamanKey) {
            RamanKey ramanKey = (RamanKey) obj;
            return (ramanKey.getStrNode().equals(this.getStrNode()));
        } else {
            return false;
        }
    }
}

class RamanMessage {
    private String coordinatorNode;
    private String senderNode;
    private String receiverNode;

    private String message;

    private String key;
    private String value;

    private ArrayList<String> replicaList;

    private int rowsDeleted;
    private boolean responseReceived;

    private String jsonResponse;
    private HashMap<String, String> queryResponseMap = new HashMap<String, String>();

    // Initiator Message Type 1
    RamanMessage(String coordinatorNode, String senderNode) {
        this.coordinatorNode = coordinatorNode;
        this.senderNode = senderNode;
    }

    // Initiator Message Type 2
    RamanMessage(String coordinatorNode, String senderNode, String message) {
        this.coordinatorNode = coordinatorNode;
        this.senderNode = senderNode;
        this.message = message;
    }

    // Normal Request Message
    RamanMessage(String coordinatorNode, String senderNode, String receiverNode, String message) {
        this.coordinatorNode = coordinatorNode;
        this.senderNode = senderNode;
        this.receiverNode = receiverNode;
        this.message = message;
    }

    // For Query Request
    RamanMessage(String coordinatorNode, String senderNode, String receiverNode, String message, String key) {
        this.coordinatorNode = coordinatorNode;
        this.senderNode = senderNode;
        this.receiverNode = receiverNode;
        this.message = message;
        this.key = key;
    }

    // For Insert Request
    RamanMessage(String coordinatorNode, String senderNode, String receiverNode, String message, String key, String value) {
        this.coordinatorNode = coordinatorNode;
        this.senderNode = senderNode;
        this.receiverNode = receiverNode;
        this.message = message;
        this.key = key;
        this.value = value;
    }

    public void setCoordinatorNode(String coordinatorNode) {
        this.coordinatorNode = coordinatorNode;
    }

    public void setSenderNode(String senderNode) {
        this.senderNode = senderNode;
    }

    public void setReceiverNode(String receiverNode) {
        this.receiverNode = receiverNode;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setReplicaList(ArrayList<String> replicaList) {
        this.replicaList = replicaList;
    }

    public void setRowsDeleted(int rowsDeleted) {
        this.rowsDeleted = rowsDeleted;
    }

    public void setResponseReceived(boolean responseReceived) {
        this.responseReceived = responseReceived;
    }

    public void setJsonResponse(String jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    public void addQueryResponse(String key, String value) {
        queryResponseMap.put(key, value);
    }

    public String getCoordinatorNode() {
        return coordinatorNode;
    }

    public String getSenderNode() {
        return senderNode;
    }

    public String getReceiverNode() {
        return receiverNode;
    }

    public String getMessage() {
        return message;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public ArrayList<String> getReplicaList() {
        return replicaList;
    }

    public int getRowsDeleted() {
        return rowsDeleted;
    }

    public boolean getResponseReceived() {
        return responseReceived;
    }

    public String getJsonResponse() {
        return jsonResponse;
    }

    public HashMap<String, String> getQueryResponseMap() {
        return queryResponseMap;
    }
}

public class SimpleDynamoActivity extends Activity implements View.OnClickListener {
    private Resources res;

    private Button btnDump, btnDelete, btnTestInsert, btnTestQuery;
    private TextView mTextView, txtPrevNode, txtNextNode, txtDynamoRingNodes;
    private EditText mEditText;

    private String MY_PORT, MY_EMULATOR_NODE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_dynamo);
        res = getResources();

        mTextView = (TextView) findViewById(R.id.textView1);
        mTextView.setMovementMethod(new ScrollingMovementMethod());

        txtPrevNode = (TextView) findViewById(R.id.txt_prev_node);
        txtNextNode = (TextView) findViewById(R.id.txt_next_node);
        txtDynamoRingNodes = (TextView) findViewById(R.id.txt_dynamo_ring_nodes);

        mEditText = (EditText) findViewById(R.id.edit_txt);

        btnDump = (Button) findViewById(R.id.button1);
        btnDelete = (Button) findViewById(R.id.button2);
        btnTestInsert = (Button) findViewById(R.id.btn_test_insert);
        btnTestQuery = (Button) findViewById(R.id.btn_test_query);

        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);

        MY_EMULATOR_NODE = String.valueOf((Integer.parseInt(portStr)));
        MY_PORT = String.valueOf((Integer.parseInt(portStr) * 2));

        LocalBroadcastManager.getInstance(this).registerReceiver(dynamoRingUpdateListener, new IntentFilter(Globals.DYNAMO_RING_UPDATE_LISTENER));
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnDump.setOnClickListener(this);
        btnDelete.setOnClickListener(this);
        btnTestInsert.setOnClickListener(this);
        btnTestQuery.setOnClickListener(this);

        txtDynamoRingNodes.setText(Globals.LIST_REPLICAS_GLOBAL.toString());
    }

    // Will be called whenever the next prev node gets change
    private BroadcastReceiver nextPrevNodeListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        }
    };

    // Will be called whenever the dynamo ring gets updated
    private BroadcastReceiver dynamoRingUpdateListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String strPrevNode = intent.getStringExtra(Globals.TXT_PREV_NODE);
            String strNextNode = intent.getStringExtra(Globals.TXT_NEXT_NODE);
            String strDynamoRingNodes = intent.getStringExtra(Globals.TXT_DYNAMO_RING_NODES);

            txtPrevNode.setText(strPrevNode);
            txtNextNode.setText(strNextNode);
            txtDynamoRingNodes.setText(strDynamoRingNodes);
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button1:
                if (mEditText.getText().toString().equalsIgnoreCase(Globals.LOCAL_QUERY)) {
                    // Local Dump
                    if (showDump(Globals.LOCAL_QUERY)) {
                        Log.v(Globals.TAG, "Local Dump Success");
                        mTextView.append("\nLocal Dump Success\n");
                    } else {
                        Log.v(Globals.TAG, "Local Dump Fail");
                        mTextView.append("\nLocal Dump Fail\n");
                    }
                } else if (mEditText.getText().toString().equalsIgnoreCase(Globals.GLOBAL_QUERY)) {
                    // Global Dump
                    if (showDump(Globals.GLOBAL_QUERY)) {
                        Log.v(Globals.TAG, "Global Dump Success");
                        mTextView.append("\nGlobal Dump Success\n");
                    } else {
                        Log.v(Globals.TAG, "Global Dump Fail");
                        mTextView.append("\nGlobal Dump Fail\n");
                    }
                } else {
                    mTextView.append("\nEntered Key is not correct. It can handle either @ or * queries\n");
                }

                break;

            case R.id.button2:
                deleteData(mEditText.getText().toString());
                break;

            case R.id.btn_test_insert:
                // Test Insert
                if (testInsert()) {
                    Log.v(Globals.TAG, "Insert Success");
                    mTextView.append("\nInsert Success\n");
                } else {
                    Log.v(Globals.TAG, "Insert Fail");
                    mTextView.append("\nInsert Fail\n");
                }
                break;

            case R.id.btn_test_query:
                // Test Query
                if (testQuery()) {
                    Log.v(Globals.TAG, "Query Success");
                    mTextView.append("\nQuery success\n");
                } else {
                    Log.v(Globals.TAG, "Query fail");
                    mTextView.append("\nQuery fail\n");
                }
                break;
        }

        mEditText.getText().clear();
    }

    private boolean showDump(String query) {
        Cursor resultCursor = null;
        boolean success = true;

        try {
            resultCursor = getContentResolver().query(Globals.mUri, null, query, null, null);

            if (resultCursor == null) {
                success = false;

                Log.v(Globals.TAG, "Result null");
                throw new Exception();
            }

            if (resultCursor.moveToFirst()) {
                do {
                    int keyIndex = resultCursor.getColumnIndex(Globals.KEY_FIELD);
                    int valueIndex = resultCursor.getColumnIndex(Globals.VALUE_FIELD);

                    if (keyIndex == -1 || valueIndex == -1) {
                        success = false;

                        Log.v(Globals.TAG, "Wrong columns");
                        resultCursor.close();
                        throw new Exception();
                    } else {
                        String strKey = resultCursor.getString(keyIndex);
                        String strValue = resultCursor.getString(valueIndex);

                        String displayedMsg = "\nKey : " + strKey + "\nValue : " + strValue;

                        // Displaying Color text so as to differentiate messages sent by different devices
                        String colorStrReceived = "<font color='" + getColor(MY_PORT) + "'>" + displayedMsg + "</font>";
                        mTextView.append("\n ");
                        mTextView.append(Html.fromHtml(colorStrReceived));
                    }

                } while (resultCursor.moveToNext());
            }
        } catch (Exception e) {
            success = false;

            Log.v(Globals.TAG, "Exception in showDump()");
            e.printStackTrace();
        } finally {
            if (null != resultCursor && !resultCursor.isClosed()) {
                resultCursor.close();
            }
        }

        return success;
    }

    private void deleteData(String query) {
        int rowsDeleted = 0;
        try {
            rowsDeleted = getContentResolver().delete(Globals.mUri, query, null);
            String displayedMsg = "\nDeleted : " + rowsDeleted + " rows";

            // Displaying Color text so as to differentiate messages sent by different devices
            String colorStrReceived = "<font color='" + getColor(MY_PORT) + "'>" + displayedMsg + "</font>";
            mTextView.append("\n ");
            mTextView.append(Html.fromHtml(colorStrReceived));
        } catch (Exception e) {
            mTextView.append("\nDelete Query Failed\n");
        }
    }

    private boolean testInsert() {
        try {
            String key = mEditText.getText().toString();
            ContentValues cv = new ContentValues();
            cv.put(Globals.KEY_FIELD, key);
            cv.put(Globals.VALUE_FIELD, key);

            getContentResolver().insert(Globals.mUri, cv);
        } catch (Exception e) {
            Log.e(Globals.TAG, e.toString());
            return false;
        }

        return true;
    }

    private boolean testQuery() {
        try {
            String key = mEditText.getText().toString();
            Cursor resultCursor = getContentResolver().query(Globals.mUri, null,
                    key, null, null);
            if (resultCursor == null) {
                Log.e(Globals.TAG, "Result null");
                throw new Exception();
            }

            int keyIndex = resultCursor.getColumnIndex(Globals.KEY_FIELD);
            int valueIndex = resultCursor.getColumnIndex(Globals.VALUE_FIELD);
            if (keyIndex == -1 || valueIndex == -1) {
                Log.e(Globals.TAG, "Wrong columns");
                resultCursor.close();
                throw new Exception();
            }

            resultCursor.moveToFirst();

            if (!(resultCursor.isFirst() && resultCursor.isLast())) {
                Log.e(Globals.TAG, "Wrong number of rows");
                resultCursor.close();
                throw new Exception();
            }

            String returnKey = resultCursor.getString(keyIndex);
            String returnValue = resultCursor.getString(valueIndex);
            if (!(returnKey.equals(key) && returnValue.equals(key))) {
                Log.e(Globals.TAG, "(key, value) pairs don't match\n");
                resultCursor.close();
                throw new Exception();
            }

            resultCursor.close();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private int getColor(String port) {
        int textColor = res.getColor(R.color.my_port);

        if (Globals.REMOTE_PORT0.contains(port)) {
            textColor = res.getColor(R.color.remote_port0);
        } else if (Globals.REMOTE_PORT1.contains(port)) {
            textColor = res.getColor(R.color.remote_port1);
        } else if (Globals.REMOTE_PORT2.contains(port)) {
            textColor = res.getColor(R.color.remote_port2);
        } else if (Globals.REMOTE_PORT3.contains(port)) {
            textColor = res.getColor(R.color.remote_port3);
        } else if (Globals.REMOTE_PORT4.contains(port)) {
            textColor = res.getColor(R.color.remote_port4);
        }

        return textColor;
    }


    public void onStop() {
        super.onStop();
        Log.v(Globals.TAG, "onStop()");
    }

}
