package com.example.attendancesystem;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoCollection;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private String UserID;
    private String m_key;
    private RecyclerView listView;
    private RecyclerView.LayoutManager viewMgr;
    private RemoteMongoCollection<Document> collection_logs;
    private AlertDialog loading_dialog;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        StitchAppClient client = Stitch.getDefaultAppClient();

        RemoteMongoClient mongodb = client.getServiceClient(RemoteMongoClient.factory, "AttendanceDB");
        collection_logs = mongodb.getDatabase("Activity").getCollection("Logs");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState){
        return inflater.inflate(R.layout.history_fragment, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        listView = (RecyclerView) view.findViewById(R.id.history_list);

        viewMgr = new LinearLayoutManager(getContext());

        listView.setHasFixedSize(true);
        listView.setAdapter(new ListAdpt(new ArrayList<HistoryLogModel>()));
        listView.setLayoutManager(viewMgr);
        SetLoadingDialog();

        try{
            m_key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            GetUserID();

            DataRetrieval list_process = new DataRetrieval(this.UserID);
            list_process.run();
        }catch(Exception e){
            Log.e("key_err", e.toString());
        }

    }

    private void SetLoadingDialog(){
        TextView message = new TextView(getActivity());

        message.setText("History List Loading...");
        message.setGravity(Gravity.CENTER);
        message.setPadding(10,30,10,10);
        message.setTextSize(16);

        loading_dialog = new AlertDialog.Builder(getContext())
                .setView(message)
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
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

    public class DataRetrieval extends Thread{
        private String UserID;
        private List<HistoryLogModel> list_data;

        public DataRetrieval(String UserID){
            this.UserID = UserID;
            list_data = new ArrayList<HistoryLogModel>();
        }

        @Override
        public void run(){
            Document query = new Document()
                    .append("usr_id", this.UserID);

            RemoteFindIterable findResult = collection_logs
                    .find(query)
                    .sort(new Document().append("log_date", -1))
                    .limit(10);

            final Task<List<Document>> logs_query = findResult.into(new ArrayList<Document>());

            logs_query.addOnCompleteListener((@NonNull Task<List<Document>> task2)->{
                if(task2.isSuccessful()){
                    List<Document> item = task2.getResult();

                    for(Document row : item){
                        HistoryLogModel logs = new HistoryLogModel();

                        logs.setmLog_date(row.getString("log_date"));
                        logs.setmSess_date(row.getString("sess_date"));
                        logs.setmSess_type(row.getString("sess_type"));
                        logs.setmSess_token(row.getString("sess_token"));
                        logs.setmUsr_id(row.getString("usr_id"));
                        logs.setmAuth_token(row.getString("auth_token"));
                        logs.setmAuth_type(row.getString("auth_type"));
                        logs.setmDev_id(row.getString("dev_id"));

                        list_data.add(logs);
                    }

                    Log.i("Data Size: ", String.valueOf(list_data.size()));

                    SetListView();
                }
                else{
                    Log.e("Error", "Error getting data!");

                    Toast.makeText(getContext(), "Error!", Toast.LENGTH_SHORT).show();
                }
            });

            logs_query.addOnFailureListener((@NonNull Exception why) -> {
                Log.e("Error", why.toString());

                Toast.makeText(getContext(), "Error!", Toast.LENGTH_SHORT).show();
            });
        }

        private void SetListView(){
            ListAdpt adpt = new ListAdpt(list_data);

            listView.setAdapter(adpt);

            loading_dialog.hide();
        }
    }

    public class ListAdpt extends RecyclerView.Adapter<ListAdpt.ListHolder>{
        private List<HistoryLogModel> mData;

        public class ListHolder extends RecyclerView.ViewHolder{
            public TextView mLogDate;
            public TextView mPunchMode;
            public TextView mPunchDate;
            public TextView mDevTarget;
            public ImageView mIconType;
            public ImageView mIconQR;
            public LinearLayout mContainer;


            public ListHolder(View itemView){
                super(itemView);

                mLogDate = (TextView) itemView.findViewById(R.id.hist_date_log);
                mPunchMode = (TextView) itemView.findViewById(R.id.hist_punch_mode);
                mPunchDate = (TextView) itemView.findViewById(R.id.hist_punch_date);
                mDevTarget = (TextView) itemView.findViewById(R.id.hist_dev_target);
                mIconType = (ImageView) itemView.findViewById(R.id.hist_icon_type);
                mIconQR = (ImageView) itemView.findViewById(R.id.hist_icon_qr);
                mContainer = (LinearLayout) itemView.findViewById(R.id.list_container);
            }
        }

        public ListAdpt(List<HistoryLogModel> dataset){
            this.mData = dataset;
        }

        @Override
        public ListAdpt.ListHolder onCreateViewHolder(ViewGroup parent, int viewType){
            Context ctx = parent.getContext();
            LayoutInflater inflater = LayoutInflater.from(ctx);

            View historyView = inflater.inflate(R.layout.history_list_item, parent, false);

            ListHolder holder = new ListHolder(historyView);

            return holder;
        }

        @Override
        public void onBindViewHolder(ListAdpt.ListHolder viewHolder, int pos){
            HistoryLogModel model = mData.get(pos);

            TextView log_date = viewHolder.mLogDate;
            TextView punch_mode = viewHolder.mPunchMode;
            TextView punch_date = viewHolder.mPunchDate;
            TextView dev_target = viewHolder.mDevTarget;
            ImageView icon_type = viewHolder.mIconType;
            ImageView icon_qr = viewHolder.mIconQR;
            LinearLayout container = viewHolder.mContainer;

            if(model.getmAuth_type().equals("AUTH_QR"))
                icon_qr.setColorFilter(Color.rgb(4, 0, 255));
            else
                icon_qr.setColorFilter(Color.rgb(176, 0, 212));

            if(pos % 2 == 1){
                container.setBackgroundColor(Color.GRAY);
                log_date.setTextColor(Color.WHITE);
                punch_date.setTextColor(Color.WHITE);
                punch_mode.setTextColor(Color.WHITE);
                dev_target.setTextColor(Color.WHITE);
                icon_type.setColorFilter(Color.WHITE);
            }
            else{
                container.setBackgroundColor(Color.WHITE);
                log_date.setTextColor(Color.BLACK);
                punch_date.setTextColor(Color.BLACK);
                punch_mode.setTextColor(Color.BLACK);
                dev_target.setTextColor(Color.BLACK);
            }

            log_date.setText(model.getmLog_date());

            switch (model.getmSess_type()){
                case "SESS_PUNCH_IN":
                    if(model.getmAuth_type().equals("AUTH_QR"))
                        punch_mode.setText("Type: Attendance In (QR Punch)");
                    else
                        punch_mode.setText("Type: Attendance In");
                    break;

                case "SESS_PUNCH_OUT":
                    if(model.getmAuth_type().equals("AUTH_QR"))
                        punch_mode.setText("Type: Attendance Out (QR Punch)");
                    else
                        punch_mode.setText("Type: Attendance Out");
                    break;

                case "SESS_AUTO_OUT":
                    punch_mode.setText("Type: Auto Punch Out");
                    break;

                case "SESS_SYS_LOGIN":
                    punch_mode.setText("Type: Account Login");
                    break;
            }

            punch_date.setText("Session Date: " + (model.getmSess_date().length() == 0 ? "N/A":model.getmSess_date()));
            dev_target.setText("Punch Device: " + model.getmDev_id());

            switch (model.getmSess_type()){
                case "SESS_PUNCH_IN":
                    if(model.getmAuth_type().equals("AUTH_QR"))
                        icon_qr.setImageResource(R.drawable.ic_qr_scan);
                    else
                        icon_qr.setImageResource(R.drawable.ic_pin_verify);

                    icon_type.setImageResource(R.drawable.ic_punch_in);
                    break;

                case "SESS_PUNCH_OUT":
                    if(model.getmAuth_type().equals("AUTH_QR"))
                        icon_qr.setImageResource(R.drawable.ic_qr_scan);
                    else
                        icon_qr.setImageResource(R.drawable.ic_pin_verify);

                    icon_type.setImageResource(R.drawable.ic_punch_out);
                    break;

                case "SESS_AUTO_OUT":
                    icon_type.setImageResource(R.drawable.ic_punch_auto);
                    break;

                case "SESS_SYS_LOGIN":
                    icon_type.setImageResource(R.drawable.ic_setting_icon);
                    break;
            }
        }

        @Override
        public int getItemCount(){
            return mData.size();
        }
    }

}
