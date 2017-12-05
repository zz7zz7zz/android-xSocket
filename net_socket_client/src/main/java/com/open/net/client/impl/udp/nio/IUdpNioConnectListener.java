package com.open.net.client.impl.udp.nio;

import com.open.net.client.impl.udp.nio.processor.SocketProcessor;

import java.io.IOException;
import java.nio.channels.DatagramChannel;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接状态回调
 */

public interface IUdpNioConnectListener {

    void onConnectSuccess(SocketProcessor mSocketProcessor, DatagramChannel socketChannel) throws IOException;

    void onConnectFailed(SocketProcessor mSocketProcessor);

}