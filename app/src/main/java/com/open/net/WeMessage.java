package com.open.net;

import com.open.net.data.Message;

/**
 * Created by Administrator on 2017/11/17.
 */

public class WeMessage extends Message {

    public String msg;

    public WeMessage(String msg) {
        this.msg = msg;
    }

    @Override
    public byte[] getPacket() {
        return msg.getBytes();
    }
}
