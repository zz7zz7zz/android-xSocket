package com.open.net.client.impl.udp.bio;

import com.open.net.client.impl.udp.bio.processor.SocketProcessor;

import java.net.DatagramPacket;
import java.net.DatagramSocket;


/**
 * author       :   Administrator
 * created on   :   2017/12/4
 * description  :
 */

public interface IUdpBioConnectListener {

    void onConnectSuccess(SocketProcessor mSocketProcessor , DatagramSocket mSocket , DatagramPacket mWriteDatagramPacket, DatagramPacket mReadDatagramPacket);

    void onConnectFailed(SocketProcessor mSocketProcessor);

}
