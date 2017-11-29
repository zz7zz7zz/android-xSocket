package com.open.net.client.listener;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接状态回调
 */

public interface IConnectStatusListener {

    void onConnectionSuccess();

    void onConnectionFailed();

}