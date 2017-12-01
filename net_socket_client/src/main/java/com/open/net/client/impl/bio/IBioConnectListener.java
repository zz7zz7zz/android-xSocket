package com.open.net.client.impl.bio;

import java.io.IOException;
import java.net.Socket;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接状态回调
 */

public interface IBioConnectListener {

    void onConnectSuccess(long connect_token, Socket mSocket) throws IOException;

    void onConnectFailed(long connect_token);

}