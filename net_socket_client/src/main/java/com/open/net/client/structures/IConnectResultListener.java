package com.open.net.client.structures;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接状态回调
 */

public interface IConnectResultListener {

    void onConnectionSuccess();

    void onConnectionFailed();

}