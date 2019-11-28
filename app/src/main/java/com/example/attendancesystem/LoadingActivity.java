package com.example.attendancesystem;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.core.auth.StitchUser;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.core.auth.providers.anonymous.AnonymousCredential;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import com.victor.loading.rotate.RotateLoading;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoadingActivity extends AppCompatActivity implements Serializable{

    private static final int PERMISSION_READ_PHONE_REQ = 0;
    private boolean PERMISSION_DONE = false;
    private boolean PERMISSION_SUCCESS = false;
    private int CheckResult = 0;
    private RotateLoading load_ico;
    private TextView version;
    private Checker checker;
    private TextView status;
    private StitchAppClient Client;
    private boolean ServerResult = false;
    private String phone_imei, phone_id;
    private boolean Finished_Auth = false, Auth_Result = false, Acc_Active = false;
    private String[] Auth_Data = new String[5];
    private String Key_Users;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_main);

        load_ico = (RotateLoading) findViewById(R.id.icon_loading);
        version = (TextView) findViewById(R.id.load_version);
        status = (TextView) findViewById(R.id.current_stat);

        version.setText("Version " + BuildConfig.VERSION_NAME + " Build " + BuildConfig.VERSION_CODE);

        checker = new Checker(this);
        load_ico.start();
        Client = null;

        checker.CheckerAction(new TriggerCheck() {
            @Override
            public void beginCheck(String message) {
                status.setText(message);
            }

            @Override
            public void onCheck(String message) {
                status.setText(message);
            }

            @Override
            public void endCheck(String message, int statusCode, int err_site) {
                status.setText(message);

                if(statusCode == 1){
                    Intent return_intent = getIntent();
                    return_intent.putExtra("results", 1);
                    setResult(RESULT_OK, return_intent);

                    finish();
                }
                else if(statusCode == 2){
                    Intent return_intent = getIntent();
                    return_intent.putExtra("results", 0);
                    return_intent.putExtra("err_code", message);
                    return_intent.putExtra("err_site", err_site);
                    setResult(RESULT_OK, return_intent);

                    finish();
                }
            }
        });

        try {
            Key_Users = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        }
        catch(Exception e){
            Log.d("Key Exception", e.getMessage());
        }

        checker.start();
    }

    @Override
    public void onBackPressed(){
        this.CheckResult = 0;

        Intent return_intent = getIntent();
        return_intent.putExtra("results", this.CheckResult);
        return_intent.putExtra("err_code", "");
        setResult(RESULT_OK, return_intent);
        finish();
    }

    private void ReqChangePhoneState(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)){
            new AlertDialog.Builder(LoadingActivity.this)
                    .setTitle("Permission Request")
                    .setMessage(getString(R.string.permission_read_phone_state_rationale))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //re-request
                            ActivityCompat.requestPermissions(LoadingActivity.this,
                                    new String[]{Manifest.permission.READ_PHONE_STATE},
                                    PERMISSION_READ_PHONE_REQ);
                        }
                    })
                    .setIcon(R.drawable.ic_warning)
                    .show();
        }
        else{
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_READ_PHONE_REQ);
        }
    }

    @NonNull
    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] permission, @NonNull int[] grantRes ){
        if(req == PERMISSION_READ_PHONE_REQ){
            if(grantRes.length == 1 && grantRes[0] == PackageManager.PERMISSION_GRANTED){
                PhonePermissionGrantedStuff();
                PERMISSION_SUCCESS = true;
            }

            PERMISSION_DONE = true;
        }
    }

    private boolean PhonePermissionGrantedStuff() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        try{
            String IMEI = tm.getImei(0);
            String Serial = Build.getSerial();

            SharedPreferences pref = EncryptedSharedPreferences.create(
                    "user_credentials",
                    Key_Users,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            SharedPreferences.Editor editor = pref.edit();
            editor.putString("dev_imei", IMEI);
            editor.putString("dev_serial", Serial);

            phone_imei = IMEI;
            phone_id = Serial;

            return editor.commit();
        }
        catch(SecurityException | NullPointerException c){
            return false;
        }
        catch(Exception e){
            return false;
        }
    }

    class Checker extends Thread{
        private TriggerCheck listener;
        private Context ctx;

        public Checker(Context ctx){
            listener = null;
            this.ctx = ctx;
        }

        public void CheckerAction(TriggerCheck listener){
            this.listener = listener;
        }

        @Override
        public void run(){
            int tests = 0;
            boolean test_status = true;
            int error_site = -1;
            String err_msg = "";

            while(tests < 4 && !Thread.interrupted()){
                try {
                    Thread.sleep(500);

                    switch (tests) {
                        case 0:
                            listener.beginCheck("Checking Connectivity");

                            if(CheckConnectivity() == false) {
                                test_status = false;
                                error_site = 0;
                                err_msg = "No network access";
                            }

                            break;

                        case 1:
                            listener.onCheck("Checking Server");

                            if(CheckServer() == false){
                                test_status = false;
                                error_site = 2;
                                err_msg = "Server unable to initialize.";
                            }

                            break;


                        case 2:
                            listener.onCheck("Getting Credentials");
                            if(getCredentials() == false){
                                test_status = false;
                                error_site = 3;
                                err_msg = "Phone Permission Not Granted";
                            }

                            break;

                        case 3:
                            listener.onCheck("Authenticating To System");
                            if(loginToSystem() == false){
                                test_status = false;
                                error_site = 4;
                            }
                            else if(Acc_Active == false){
                                test_status = false;
                                error_site = 5;
                            }

                            break;

                    }
                }
                catch(Exception e){
                    test_status = false;
                    System.out.println(e.getMessage());
                    break;
                }

                if(test_status == false) break;

                tests++;
            }

            if(test_status){
                listener.endCheck("Passed", 1, error_site);
            }
            else{
                listener.endCheck("Failed - " + err_msg, 2, error_site);
            }
        }


    }

    private boolean CheckConnectivity(){
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        try{
            NetworkInfo info = cm.getActiveNetworkInfo();
            boolean state = info != null && info.isConnected();

            return state;
        }
        catch(NullPointerException e){
            return false;
        }
    }

    private boolean CheckServer(){

        if(!Stitch.hasAppClient(getString(R.string.mongodb_app_id)))
            Stitch.initializeDefaultAppClient(getString(R.string.mongodb_app_id));

        final StitchAppClient client = Stitch.getDefaultAppClient();

        client.getAuth().loginWithCredential(new AnonymousCredential()).addOnCompleteListener(
                (@NonNull Task<StitchUser> task) -> {
                    if(task.isSuccessful()){
                        Log.d("Attendance_Sys (DB)", String.format(
                                "logged in as user %s with provider %s",
                                task.getResult().getId(),
                                task.getResult().getLoggedInProviderType()));
                        Client = client;
                        ServerResult = true;
                    }
                    else{
                        Log.e("Attendance_Sys (DB)", "failed to log in", task.getException());
                        Client = null;
                        ServerResult = true;
                    }
                }
        );

        while(ServerResult == false);

        return Client != null;
    }

    private boolean getCredentials(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED){
            ReqChangePhoneState();

            while(PERMISSION_DONE == false){}

            return PERMISSION_SUCCESS;
        }
        else{
            return PhonePermissionGrantedStuff();
        }
    }

    private boolean loginToSystem(){
        StitchAppClient client = Stitch.getDefaultAppClient();

        RemoteMongoClient mongodb = client.getServiceClient(RemoteMongoClient.factory, "AttendanceDB");

        RemoteMongoCollection<Document> collection_bind = mongodb.getDatabase("Users").getCollection("Binds");
        RemoteMongoCollection<Document> collection_usr = mongodb.getDatabase("Users").getCollection("Accounts");
        RemoteMongoCollection<Document> collection_logs = mongodb.getDatabase("Activity").getCollection("Logs");

        Document query = new Document("dev_id", phone_id)
               .append("imei", phone_imei);

        final RemoteFindOptions options = new RemoteFindOptions()
                .sort(new Document().append("acc_id", -1));

        Log.d("finder", "finding user "+phone_id+" and imei "+phone_imei);

        final Task<Document> auth_query = collection_bind.findOne(query, options);

        auth_query.addOnCompleteListener((@NonNull Task<Document> task) -> {
            if(task.getResult() == null){
                Log.d("Auth Failed", "No user available");

                Finished_Auth = true;
            }
            else if(task.isSuccessful()){
                Document doc = task.getResult();

                final ObjectId user_id = (ObjectId) doc.get("acc_id");
                Document query2 = new Document("_id", user_id);
                Document projection = new Document()
                        .append("name", 1)
                        .append("role", 1)
                        .append("log_date", 1)
                        .append("acc_stat", 1);

                RemoteFindOptions options2 = new RemoteFindOptions()
                        .projection(projection);

                final Task<Document> res_query = collection_usr.findOne(query2, options2);
                res_query.addOnCompleteListener((@NonNull Task<Document> task2) -> {
                    if(task2.getResult() == null){
                        Log.d("Auth Failed", "Data retrieval failed");

                        Auth_Result = false;
                    }
                    else if(task2.isSuccessful()){
                        Document data = task2.getResult();

                        if((int) data.get("acc_stat") == 1){
                            Acc_Active = true;

                            Auth_Data[0] = (String) data.get("name");
                            Auth_Data[1] = String.valueOf(data.get("role"));
                            Auth_Data[2] = (data.get("log_date") == null)? "": (String)data.get("log_date");
                            Auth_Data[3] = String.valueOf(data.get("acc_stat"));
                            Auth_Data[4] = user_id.toHexString();

                            try {
                                SharedPreferences pref = EncryptedSharedPreferences.create(
                                        "user_credentials",
                                        Key_Users,
                                        this,
                                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                                );

                                SharedPreferences.Editor editor = pref.edit();

                                editor.putString("usr_name", Auth_Data[0]);
                                editor.putString("usr_role", Auth_Data[1]);
                                editor.putString("prev_log", Auth_Data[2]);
                                editor.putString("user_id", Auth_Data[4]);

                                editor.commit();
                            }
                            catch(Exception e){}

                            LocalDateTime time = LocalDateTime.now();
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            String date_time = formatter.format(time);

                            Document filters = new Document("_id", user_id);
                            Document updates = new Document("$set",
                                    new Document().append("log_date", date_time)
                            );

                            collection_usr.updateOne(filters, updates);

                            Document newLog = new Document()
                                    .append("log_date", date_time)
                                    .append("sess_type", "SESS_SYS_LOGIN")
                                    .append("sess_date", "")
                                    .append("sess_token", "")
                                    .append("usr_id", user_id.toHexString())
                                    .append("auth_token", "")
                                    .append("auth_type", "SYS_AUTO_VERIFY")
                                    .append("dev_id", phone_id);

                            collection_logs.insertOne(newLog);
                        }

                        Auth_Result = true;

                    }
                    else{
                        Log.d("Auth Error", task2.getException().getMessage());
                        Auth_Result = false;
                    }

                    Finished_Auth = true;
                });

            }
            else{
                Log.d("Auth Error", task.getException().getMessage());
                Auth_Result = false;

                Finished_Auth = true;
            }
        });

        while(Finished_Auth == false);

        return Auth_Result;
    }

    private String byteToHex(byte[] hash){
        StringBuffer hexString = new StringBuffer();
        for(int i=0; i<hash.length; i++){
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1)
                hexString.append('0');

            hexString.append(hex);
        }

        return hexString.toString();
    }
}
