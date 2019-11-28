package com.example.attendancesystem;

public interface TriggerCheck{
    void beginCheck(String message);

    void onCheck(String message);

    void endCheck(String message, int statusCode, int err_site);
}