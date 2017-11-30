package com.open.net.client.structures;

import com.open.net.client.structures.message.Message;

import java.util.LinkedList;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   数据回调
 */

public abstract class BaseMessageProcessor {

    //----------------------------------发数据------------------------------------------------
    public final void send(BaseClient mClient,byte[] src , int offset , int length){
        Message msg = mClient.mWriteMessageQueen.build(src,offset,length);
        mClient.addWriteMessage(msg);
    }

    //----------------------------------收数据------------------------------------------------
    public final void onReceive(BaseClient mClient,byte[] src , int offset , int length) {
        Message msg = mClient.mReadMessageQueen.build(src,offset,length);
        mClient.addReadMessage(msg);
    }

    public final void onProcessReceivedMessage(BaseClient mClient){
        onReceive(mClient,mClient.mReadMessageQueen.mQueen);

        Message msg = mClient.pollReadMessage();
        while (null != msg){
            mClient.removeReadMessageId(msg);
            msg = mClient.pollReadMessage();
        }
    }

    //请不要去操作这个表的数据，只能读，不能增删改
    public abstract void onReceive(BaseClient mClient,LinkedList<Message> mQueen);

}
