package com.open.net.client.impl.nio;

import com.open.net.client.impl.nio.processor.SocketProcessor;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接状态回调
 */

public interface INioConnectListener {

    void onConnectSuccess(SocketProcessor mSocketProcessor, SocketChannel socketChannel, Selector mSelector) throws IOException;

    void onConnectFailed(SocketProcessor mSocketProcessor);

}