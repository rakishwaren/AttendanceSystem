package com.example.attendancesystem;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

public class AuthenticationFailedActivity extends AppCompatActivity {

    private TextView err_message;
    private TextView build_version;
    private String Dev_IMEI, Dev_ID;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_error_main);

        int err_code = getIntent().getIntExtra("err_code", -1);

        err_message = (TextView) findViewById(R.id.err_message);
        build_version = (TextView) findViewById(R.id.load_version_02);
        build_version.setText("Version " + BuildConfig.VERSION_NAME + " Build " + (BuildConfig.VERSION_CODE));

        String msg = "";

        switch(err_code) {
            case 4:
                GetDeviceInfo();
                msg = "Error: Phone not bind to account\n" +
                        "Please seek Administrator to bind your phone to your account.\n\n" +
                        "Please show below information to administrator.\n" +
                        "Device ID: " + this.Dev_ID + "\n" +
                        "Device IMEI: " + this.Dev_IMEI;
                break;

            case 5:
                msg = "Error: Account Suspended\n" +
                        "Please seek Administrator to reactivate your account.";
                break;

            default:
                finishAffinity();
                break;
        }

        err_message.setText(msg);
        err_message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    }

    private void GetDeviceInfo() {
        try {
            String keys = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences pref = EncryptedSharedPreferences.create(
                "user_credentials",
                    keys,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            this.Dev_ID = pref.getString("dev_serial", null);
            this.Dev_IMEI = pref.getString("dev_imei", null);
        }
        catch(Exception e){
            this.Dev_IMEI = null;
            this.Dev_ID = null;
        }
    }

    @Override
    public void onBackPressed(){
        finishAffinity();
    }
}
