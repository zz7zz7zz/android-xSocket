package com.open.net.client.structures;


import com.open.net.client.structures.message.Message;
import com.open.net.client.structures.message.MessageReadQueen;
import com.open.net.client.structures.message.MessageWriteQueen;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   客户连接对象
 */

public abstract class BaseClient {

    public MessageReadQueen mReadMessageQueen   = new MessageReadQueen();
    public MessageWriteQueen mWriteMessageQueen  = new MessageWriteQueen();

    //--------------------------------------------------------------------------------------
    public void clear(){
        Message msg = pollWriteMessage();
        while (null != msg) {
            removeWriteMessage(msg);
            msg = pollWriteMessage();
        }
    }
    //--------------------------------------------------------------------------------------
    public void addReadMessage(Message msg) {
        mReadMessageQueen.put(msg);
    }

    public Message pollReadMessage(){
        return mReadMessageQueen.mQueen.poll();
    }

    public void removeReadMessageId(Message msg){
        mReadMessageQueen.remove(msg);
    }

    //--------------------------------------------------------------------------------------
    public void addWriteMessage(Message msg) {
        mWriteMessageQueen.put(msg);
    }

    public Message pollWriteMessage(){
        return mWriteMessageQueen.mQueen.poll();
    }

    public void removeWriteMessage(Message msg){
        mWriteMessageQueen.remove(msg);
    }

}
