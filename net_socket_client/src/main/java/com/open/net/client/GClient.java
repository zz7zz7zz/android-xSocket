package com.open.net.client;

import com.open.net.client.structures.message.MessageBuffer;
import com.open.net.client.structures.pools.MessagePool;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   客户端全局数据
 */

public final class GClient {

    private static boolean isInitialized  = false;

    public static final void init(){
        if(!isInitialized){
            MessagePool.init(6);
            MessageBuffer.init(8 * MessageBuffer.KB, 64*MessageBuffer.KB, 1* MessageBuffer.MB, 5 , 2 , 0, 2);
            isInitialized = true;
        }
    }
}
