package com.open.net.client.listener;

/**
 * 链接状态回调
 * Created by Administrator on 2017/11/17.
 */

public interface IConnectStatusListener {

    void onConnectionSuccess();

    void onConnectionFailed();

}