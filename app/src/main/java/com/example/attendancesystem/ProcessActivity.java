package com.example.attendancesystem;

import java.io.Serializable;
import java.lang.*;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.mongodb.stitch.android.core.Stitch;
import com.mongodb.stitch.android.core.StitchAppClient;

public class ProcessActivity extends AppCompatActivity implements Serializable {
    private LoadingSystem loading_sys;
    private MainSystem main_sys;
    private AuthErrorSystem err_sys;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        loading_sys = new LoadingSystem(this);

        loading_sys.start();
    }

    @Override
    public void onActivityResult(int req, int res, Intent data){
        if(req == 1){
            if(res == Activity.RESULT_OK){
                int response = data.getIntExtra("results", -1);

                switch(response){
                    case 1:
                        if(loading_sys != null){
                            loading_sys.interrupt();
                        }

                        main_sys = new MainSystem(this);

                        main_sys.start();
                        break;

                    case 0:
                    default:
                        String txt_res = data.getStringExtra("err_code");
                        int err_site = data.getIntExtra("err_site", -1);

                        if(err_site == 4 || err_site == 5){
                            err_sys = new AuthErrorSystem(this, err_site);

                            err_sys.start();
                        }
                        else{
                            ErrMessage(txt_res);
                        }

                        break;
                }

            }

        }
    }

    private void ErrMessage(String message){
        DialogInterface.OnClickListener dialogListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which){
                    case DialogInterface.BUTTON_POSITIVE:
                        finishAffinity();
                        System.exit(0);
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("Ok", dialogListener)
                .show();
    }

    @Override
    public void onDestroy(){
        try {
            String master_key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences pref = EncryptedSharedPreferences.create(
                    "user_credentials",
                    master_key,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    );

            SharedPreferences.Editor editor = pref.edit();
            editor.clear();
            editor.apply();

            StitchAppClient client = Stitch.getDefaultAppClient();
            client.getAuth().logout();
            client.close();

            getApplicationContext().getCacheDir().deleteOnExit();
        }
        catch(Exception e){

        }

        super.onDestroy();
    }

    class LoadingSystem extends Thread {
        private Context ctx;

        public LoadingSystem(Context ctx){
            this.ctx = ctx;
        }

        @Override
        public void run(){
            Intent i = new Intent(this.ctx, LoadingActivity.class);

            startActivityForResult(i, 1);

            while(!Thread.interrupted()){ }
        }
    }

    class MainSystem extends Thread {
        private Context ctx;

        public MainSystem(Context ctx){
            this.ctx = ctx;
        }

        @Override
        public void run(){
            Intent i = new Intent(this.ctx, MainActivity.class);

            startActivity(i);
        }
    }

    class AuthErrorSystem extends Thread{
        private Context ctx;
        private int err_code;

        public AuthErrorSystem(Context ctx, int err_code){
            this.ctx = ctx;
            this.err_code = err_code;
        }

        @Override
        public void run(){
            Intent i = new Intent(this.ctx, AuthenticationFailedActivity.class);
            i.putExtra("err_code", this.err_code);

            startActivity(i);
        }
    }
}
