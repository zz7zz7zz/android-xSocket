package com.open.net.listener;

/**
 * 数据接收回调
 * Created by Administrator on 2017/11/17.
 */

public interface IConnectReceiveListener {

    void onConnectionReceive(byte[] array);

}
