package com.example.attendancesystem;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.Serializable;

public class MainActivity extends AppCompatActivity implements Serializable {

    private String DeviceID;
    private String DeviceIMEI;
    private BottomNavigationView footer_bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        GetDeviceInfo();

        footer_bar = (BottomNavigationView) findViewById(R.id.bottom_navigation);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        footer_bar.setOnNavigationItemSelectedListener((@NonNull MenuItem item) -> {
            int item_id = item.getItemId();

            for(int i=0; i<footer_bar.getMenu().size(); i++){
                MenuItem singles = footer_bar.getMenu().getItem(i);
                boolean isChecked = singles.getItemId() == item.getItemId();
                singles.setChecked(isChecked);
            }

            if(getSupportFragmentManager().findFragmentByTag("PUNCH_FRAG") instanceof PunchFragment){
                DialogInterface.OnClickListener dialogListener = (DialogInterface dialog, int which) -> {
                    switch(which){
                        case DialogInterface.BUTTON_POSITIVE:
                            switch (item_id) {
                                case R.id.action_history:
                                    FragChange(2);
                                    break;

                                case R.id.action_punch:
                                    FragChange(1);
                                    break;

                                case R.id.action_status:
                                default:
                                    FragChange(0);
                                    break;

                            }
                            break;

                        case DialogInterface.BUTTON_NEGATIVE:
                            for(int i=0; i<footer_bar.getMenu().size(); i++){
                                MenuItem singles = footer_bar.getMenu().getItem(i);
                                if(singles.getItemId() == R.id.action_punch){
                                    singles.setChecked(true);
                                    break;
                                }
                            }
                            break;
                    }
                };

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Are you sure get out NFC punch?")
                        .setPositiveButton("Yes", dialogListener)
                        .setNegativeButton("No", dialogListener)
                        .show();
            }
            else {


                switch (item_id) {
                    case R.id.action_history:
                        FragChange(2);
                        break;

                    case R.id.action_punch:
                        FragChange(1);
                        break;

                    case R.id.action_status:
                    default:
                        FragChange(0);
                        break;

                }
            }

            return true;
        });

        FragChange(0);
    }

    private void GetDeviceInfo(){
        try {
            String master_key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);

            SharedPreferences pref = EncryptedSharedPreferences.create(
                    "user_credentials",
                    master_key,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            DeviceID = pref.getString("dev_serial", null);
            DeviceIMEI = pref.getString("dev_imei", null);

            if(DeviceID == null || DeviceIMEI == null)
                throw new Exception("Data NULL error");

        }
        catch(Exception e){
            Toast.makeText(this, "Unable to Get device identification data!", Toast.LENGTH_LONG).show();
            finishAffinity();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.setting_about:{
                    AppInfo();
                }
                break;

            case R.id.setting_faq:{

                }
                break;

            case R.id.setting_guide:{
                }
                break;

            case R.id.setting_info:{
                    DeviceInfo();
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void ExitClicked(View v){
        DialogInterface.OnClickListener dialogListener = (DialogInterface dialog, int which) -> {
            switch(which){
                case DialogInterface.BUTTON_POSITIVE:
                    finishAffinity();
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure?")
                .setPositiveButton("Yes", dialogListener)
                .setNegativeButton("No", dialogListener)
                .show();
    }

    @Override
    public void onBackPressed(){
        DialogInterface.OnClickListener dialogListener = (DialogInterface dialog, int which) -> {
            switch(which){
                case DialogInterface.BUTTON_POSITIVE:
                    finishAffinity();
                    System.exit(0);
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure?")
                .setPositiveButton("Yes", dialogListener)
                .setNegativeButton("No", dialogListener)
                .show();
    }

    @Override
    public void onPause(){
        super.onPause();
    }

    private void FragChange(int frag_id){
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        switch(frag_id){
            case 1:
                transaction.replace(R.id.frag_content, new PunchFragment(), "PUNCH_FRAG");
                break;

            case 2:
                transaction.replace(R.id.frag_content, new HistoryFragment(), "HISTORY_FRAG");
                break;

            case 0:
            default:
                transaction.replace(R.id.frag_content, new StatusFragment(), "STAT_FRAG");
                break;
        }

        transaction.commit();
    }

    private void DeviceInfo(){
        String message = "Device Info\nDevice ID: " + DeviceID + "\nDevice IMEI: " + DeviceIMEI
                + "\n\nAll private data is encrypted with AES-GCM Algorithm.";

        DialogInterface.OnClickListener dialogListener = (DialogInterface dialog, int which) -> {
            switch(which){
                case DialogInterface.BUTTON_POSITIVE:
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("OK", dialogListener)
                .show();
    }

    private void AppInfo(){
        String message = "Application Info\nVersion: " + BuildConfig.VERSION_NAME + "\nBuild No: " + BuildConfig.VERSION_CODE
                + "\n\nAll private data is encrypted with AES-GCM Algorithm.";

        DialogInterface.OnClickListener dialogListener = (DialogInterface dialog, int which) -> {
            switch(which){
                case DialogInterface.BUTTON_POSITIVE:
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("OK", dialogListener)
                .show();
    }


}
