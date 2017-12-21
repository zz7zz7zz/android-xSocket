package com.open.net.client.impl.tcp.bio;

import com.open.net.client.impl.tcp.bio.processor.BioReadWriteProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接状态回调
 */

public interface BioConnectListener {

    void onConnectSuccess(BioReadWriteProcessor mSocketProcessor, OutputStream mOutputStream , InputStream mInputStream) throws IOException;

    void onConnectFailed(BioReadWriteProcessor mSocketProcessor);

}