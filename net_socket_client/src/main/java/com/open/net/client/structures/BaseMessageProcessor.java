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
    public final void send(BaseClient mClient,byte[] src){
        this.send(mClient,src,0,src.length);
    }

    public final void send(BaseClient mClient,byte[] src , int offset , int length){
        mClient.onSendMessage(src,offset,length);
    }

    //----------------------------------收数据------------------------------------------------
    public final void onReceiveData(BaseClient mClient, byte[] src , int offset , int length) {
        mClient.onReceiveData(src,offset,length);
    }

    public final void onReceiveDataCompleted(BaseClient mClient){
        if(mClient.mReadMessageQueen.mReadQueen.size()>0){
            onReceiveMessages(mClient,mClient.mReadMessageQueen.mReadQueen);
            mClient.onReceiveMessageClear();
        }
    }

    //请不要去操作这个表的数据，只能读，不能增删改
    public abstract void onReceiveMessages(BaseClient mClient, LinkedList<Message> mQueen);

}
