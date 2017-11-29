package com.open.net.client.listener;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   数据回调
 */

public interface BaseMessageProcessor {

    void onReceive(byte[] src , int offset , int length);

}
