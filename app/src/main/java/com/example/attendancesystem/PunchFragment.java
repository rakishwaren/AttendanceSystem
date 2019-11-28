package com.example.attendancesystem;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteFindOptions;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PunchFragment extends Fragment {

    private String UserID;
    private String m_key;
    private Button next_btn;
    private Button qr_button;
    private EditText text_challenge;
    private Context Main_Context;
    private String[] ChallengeResponse;
    private RemoteMongoCollection<Document> collection_challenge;
    private Handler handler;
    private boolean isIPAddressProcessed = false;
    private AlertDialog loading_dialog;

    private int QR_REQUEST = 100;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        StitchAppClient client = Stitch.getDefaultAppClient();

        RemoteMongoClient mongodb = client.getServiceClient(RemoteMongoClient.factory, "AttendanceDB");
        collection_challenge = mongodb.getDatabase("Activity").getCollection("Challenges");

        handler = new Handler();
    }

    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        this.Main_Context = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState){
        return inflater.inflate(R.layout.punch_fragment, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        next_btn = (Button) view.findViewById(R.id.nextButton);
        text_challenge = (EditText) view.findViewById(R.id.challengeCode);
        qr_button = (Button) view.findViewById(R.id.qr_scan);

        next_btn.setOnClickListener(v -> {NextButtonPressed(v);});
        qr_button.setOnClickListener(v -> {QRButtonPressed(v);});

        try{
            m_key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            GetUserID();
        }catch(Exception e){
            Log.e("key_err", e.toString());
        }
    }

    @Override
    public void onStart(){
        super.onStart();
    }

    private void QRButtonPressed(View v){
        Intent cameras = new Intent(getContext(), QrScannerActivity.class);
        startActivityForResult(cameras, QR_REQUEST);
    }

    @Override
    public void onActivityResult(int req, int res, Intent data){
        if(req == QR_REQUEST){
            if(res == getActivity().RESULT_OK){
                String qr_string = data.getStringExtra("QR_DATA");

                String[] qr_data = qr_string.split("@");

                if(qr_data.length != 2){
                    Toast.makeText(getContext(), "Invalid QR Code", Toast.LENGTH_SHORT).show();
                }
                else if(qr_data[0].length() != 23 || qr_data[1].length() != 64){
                    Toast.makeText(getContext(), "Invalid QR Code", Toast.LENGTH_SHORT).show();
                }
                else{
                    QRProcess proc = new QRProcess(qr_data[1]);
                    proc.run();
                }
            }
        }
    }


    private void NextButtonPressed(View v) {
        String txt_challenge = text_challenge.getText().toString();

        if(txt_challenge.length() == 6){
            next_btn.setEnabled(false);
            text_challenge.setEnabled(false);

            next_btn.setText("Checking Challenge Code...");

            CheckThread ck = new CheckThread(txt_challenge);
            ck.run();
        }
        else{
            Toast.makeText(Main_Context, "Invalid challenge code!", Toast.LENGTH_SHORT).show();
            next_btn.setEnabled(true);
            text_challenge.setEnabled(true);
            next_btn.setText("Punch Attendance");
        }
    }

    private void GetUserID(){
        try{
            SharedPreferences pref = EncryptedSharedPreferences.create(
                    "user_credentials",
                    m_key,
                    getContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            UserID = pref.getString("user_id", null);
        }
        catch(Exception e){
            Log.e("Data error", e.toString());
        }
    }

    private void ResponseChallengeDialog(){
        new AlertDialog.Builder(Main_Context)
                .setTitle("Challenge Response")
                .setMessage("Please key in response code to device:\n" + ChallengeResponse[0])
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Fragment fm = new StatusFragment();
                        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
                        transaction.replace(R.id.frag_content, fm);
                        transaction.commit();
                    }
                })
                .setIcon(R.drawable.ic_warning)
                .show();
    }

    private class QRProcess extends Thread{
        private String challenge;

        public QRProcess(String challenge){
            this.challenge = challenge;
        }

        @Override
        public void run(){
            handler.post(() -> {
                SetLoadingDialog();
            });

            Document query = new Document()
                    .append("challenge_code", this.challenge);

            Document projection = new Document()
                    .append("_id", 1)
                    .append("challenge_code", 1);

            RemoteFindOptions opts = new RemoteFindOptions()
                    .projection(projection);

            final Task<Document> challenge_query = collection_challenge.findOne(query, opts);

            challenge_query.addOnCompleteListener((@NonNull Task<Document> task2)->{
                if(task2.isSuccessful()) {
                    Document res = task2.getResult();

                    ObjectId id = (ObjectId) res.get("_id");
                    String ip = getPublicIP();

                    Document filterDoc = new Document().append("_id", id);
                    Document updateDoc = new Document().append("$set",
                            new Document()
                                    .append("requestor", UserID)
                                    .append("requestor_ip", ip)
                                    .append("auth_status", "QR_OK")
                                    .append("auth_date", CurrentDateTime())
                    );

                    final Task<RemoteUpdateResult> updateTask = collection_challenge.updateOne(filterDoc, updateDoc);

                    updateTask.addOnCompleteListener((@NonNull Task<RemoteUpdateResult> task3) -> {
                        Fragment fm = new StatusFragment();
                        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
                        transaction.replace(R.id.frag_content, fm);
                        transaction.commit();
                    });
                }
                else {
                    Toast.makeText(Main_Context, "Invalid challenge code!", Toast.LENGTH_SHORT).show();
                }

                loading_dialog.hide();
            });

            challenge_query.addOnFailureListener((@NonNull Exception why) -> {
                Log.e("Error", why.toString());

                Toast.makeText(Main_Context, "Response Error!", Toast.LENGTH_SHORT).show();
                
                loading_dialog.hide();
            });
        }

    }

    private class CheckThread extends Thread{
        private String challenge;

        public CheckThread(String challenge){
            this.challenge = challenge;
        }

        @Override
        public void run(){
            String hashed_challenge = "";

            try{
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] encodedhash = digest.digest(challenge.getBytes(StandardCharsets.UTF_8));

                hashed_challenge = byteToHex(encodedhash);
            }
            catch(Exception e){
            }

            Document query = new Document()
                    .append("challenge_code", hashed_challenge);

            Document projection = new Document()
                    .append("_id", 1)
                    .append("challenge_code", 1);

            RemoteFindOptions opts = new RemoteFindOptions()
                    .projection(projection);

            final Task<Document> challenge_query = collection_challenge.findOne(query, opts);

            Log.i("Check hang", "check hang");

            challenge_query.addOnCompleteListener((@NonNull Task<Document> task2)->{
                Log.i("Check hang 2", "check hang 2");
                if(task2.getResult() == null) {
                    Toast.makeText(Main_Context, "Invalid challenge code!", Toast.LENGTH_SHORT).show();
                    next_btn.setEnabled(true);
                    text_challenge.setEnabled(true);
                    next_btn.setText("Punch Attendance");
                }
                else if(task2.isSuccessful()){
                    Document res = task2.getResult();

                    String[] response = generateResponse();
                    ObjectId id = (ObjectId) res.get("_id");
                    String ip = getPublicIP();

                    Document filterDoc = new Document().append("_id", id);
                    Document updateDoc = new Document().append("$set",
                            new Document()
                                    .append("response_code", response[1])
                                    .append("requestor", UserID)
                                    .append("requestor_ip", ip)
                                    .append("auth_status", "PHONE_AUTH")
                                    .append("auth_date", CurrentDateTime())
                    );

                    final Task<RemoteUpdateResult> updateTask = collection_challenge.updateOne(filterDoc, updateDoc);

                    ChallengeResponse = response;

                    handler.post(() -> {
                        ResponseChallengeDialog();
                    });
                }
                else {
                    Toast.makeText(Main_Context, "Invalid challenge code!", Toast.LENGTH_SHORT).show();
                    next_btn.setEnabled(true);
                    text_challenge.setEnabled(true);
                    next_btn.setText("Punch Attendance");
                }
            });

            challenge_query.addOnFailureListener((@NonNull Exception why) -> {
                Log.e("Error", why.toString());

                Toast.makeText(Main_Context, "Invalid challenge code!", Toast.LENGTH_SHORT).show();
                next_btn.setEnabled(true);
                text_challenge.setEnabled(true);
                next_btn.setText("Punch Attendance");
            });
        }

    }

    private void SetLoadingDialog(){
        TextView message = new TextView(getActivity());

        message.setText("Authenticating...");
        message.setGravity(Gravity.CENTER);
        message.setPadding(10,30,10,10);
        message.setTextSize(16);

        loading_dialog = new AlertDialog.Builder(getContext())
                .setView(message)
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
    }


    private String getPublicIP(){
        String value = null;
        ExecutorService es = Executors.newSingleThreadExecutor();

        Future<String> result = es.submit(() -> {
            try{
                URL url = new URL("http://whatismyip.akamai.com/");
                HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();

                try{
                    InputStream in = new BufferedInputStream(urlConn.getInputStream());
                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    StringBuilder builder = new StringBuilder();
                    String line;

                    while((line = r.readLine()) != null){
                        builder.append(line).append('\n');
                    }

                    urlConn.disconnect();
                    return builder.toString();
                }
                finally {
                    urlConn.disconnect();
                }
            }
            catch(IOException e){
                Log.e("Public IP: ", e.getMessage());
            }

            isIPAddressProcessed = true;
            return null;
        });

        try{
            value = result.get();

            while(isIPAddressProcessed == false);
        }
        catch(Exception e){}

        es.shutdown();

        return value;
    }

    private String[] generateResponse(){
        String[] data = new String[2];
        String generated;

        Random rn = new Random();

        int key_nums = rn.nextInt(99999999) + 1;

        if(String.valueOf(key_nums).length() == 8)
            generated = String.valueOf(key_nums);
        else if(String.valueOf(key_nums).length() == 7)
            generated = "0" + String.valueOf(key_nums);
        else if(String.valueOf(key_nums).length() == 6)
            generated = "00" + String.valueOf(key_nums);
        else if(String.valueOf(key_nums).length() == 5)
            generated = "000" + String.valueOf(key_nums);
        else if(String.valueOf(key_nums).length() == 4)
            generated = "0000" + String.valueOf(key_nums);
        else if(String.valueOf(key_nums).length() == 3)
            generated = "00000" + String.valueOf(key_nums);
        else if(String.valueOf(key_nums).length() == 2)
            generated = "000000" + String.valueOf(key_nums);
        else
            generated = "0000000" + String.valueOf(key_nums);

        data[0] = generated;

        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest((this.UserID + "_" + data[0]).getBytes(StandardCharsets.UTF_8));

            data[1] = byteToHex(encodedhash);
        }
        catch(Exception e){
            data[1] = "";
        }

        return data;
    }

    private String CurrentDateTime(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        return sdf.format(new Date());
    }

    private boolean isNumeric(String text){
        try{
            Integer.parseInt(text);

            return true;
        }
        catch(NumberFormatException e){
            return false;
        }
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


