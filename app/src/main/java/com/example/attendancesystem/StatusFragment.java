package com.example.attendancesystem;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;

import org.bson.Document;

public class StatusFragment extends Fragment {

    private String UserID;
    private String m_key;
    private TextView txt_username;
    private TextView txt_logged;
    private TextView txt_stats;
    private TextView txt_sessID;
    private TextView txt_sessDate;
    private RemoteMongoCollection<Document> collection_session;
    private Handler handler;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        StitchAppClient client = Stitch.getDefaultAppClient();

        RemoteMongoClient mongodb = client.getServiceClient(RemoteMongoClient.factory, "AttendanceDB");
        collection_session = mongodb.getDatabase("Activity").getCollection("Session");

        handler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // Defines the xml file for the fragment
        return inflater.inflate(R.layout.status_fragment, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        txt_username = (TextView) view.findViewById(R.id.user_name);
        txt_logged = (TextView) view.findViewById(R.id.last_log);
        txt_stats = (TextView) view.findViewById(R.id.punch_stats);
        txt_sessID = (TextView) view.findViewById(R.id.sess_id);
        txt_sessDate = (TextView) view.findViewById(R.id.punch_date);

        try{ m_key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        }catch(Exception e){
            Log.e("key_err", e.toString()); }

        GetAccInfo();

        SessionThread sess = new SessionThread(this.UserID);
        sess.run();
    }

    private class SessionThread extends Thread{
        private String UserID;

        public SessionThread(String UserID){
            this.UserID = UserID;
        }

        @Override
        public void run() {
            Document query = new Document()
                    .append("usr_id", this.UserID);

            final Task<Document> task_query = collection_session.findOne(query);

            task_query.addOnCompleteListener((@NonNull Task<Document> result) -> {
                if(result.getResult() == null){
                    txt_stats.setText("Punch Card Status: Not Servicing");
                    txt_sessID.setText("Session ID: [Please punch in first.]");
                    txt_sessDate.setText("Punch Date: [Please punch in first.]");
                }
                else if(result.isSuccessful()){
                    Document data = result.getResult();

                    txt_stats.setText("Punch Card Status: In working");
                    txt_sessID.setText("Session ID:\n" + data.getString("sess_token"));
                    txt_sessDate.setText("Punch Date: " + data.getString("sess_date"));
                }
                else{
                    Log.e("InfoError", result.getException().getMessage());
                }
            });

            task_query.addOnFailureListener((@NonNull Exception e) -> {
                Log.e("InfoError", e.getMessage());
            });
        }
    }

    private void GetAccInfo(){
        try{
            SharedPreferences pref = EncryptedSharedPreferences.create(
                    "user_credentials",
                    m_key,
                    getContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            txt_logged.setText("Last Login: " + pref.getString("prev_log", null));
            txt_username.setText("Logged In As: " + pref.getString("usr_name", null));
            UserID = pref.getString("user_id", null);
        }
        catch(Exception e){

        }
    }
}
