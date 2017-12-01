package com.open.net.client.structures.message;

import java.util.LinkedList;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   读队列
 */

public final class MessageReadQueen {

    public MessageBuffer      mReadMessageBuffer  = new MessageBuffer();
    public LinkedList<Message> mQueen = new LinkedList<>();//真正的消息队列

    public Message build(byte[] src , int offset , int length){
        Message msg = mReadMessageBuffer.build(src,offset,length);
        return msg;
    }

    public void put(Message msg){
        if(null != msg){
            mQueen.add(msg);
        }
    }

    public void remove(Message msg){
        if(null != msg){
            mQueen.remove(msg);
            mReadMessageBuffer.release(msg);
        }
    }
}
