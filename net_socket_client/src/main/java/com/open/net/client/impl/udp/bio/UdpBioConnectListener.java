package com.open.net.client.impl.udp.bio;

import com.open.net.client.impl.udp.bio.processor.UdpBioReadWriteProcessor;

import java.net.DatagramPacket;
import java.net.DatagramSocket;


/**
 * author       :   Administrator
 * created on   :   2017/12/4
 * description  :
 */

public interface UdpBioConnectListener {

    void onConnectSuccess(UdpBioReadWriteProcessor mSocketProcessor , DatagramSocket mSocket , DatagramPacket mWriteDatagramPacket, DatagramPacket mReadDatagramPacket);

    void onConnectFailed(UdpBioReadWriteProcessor mSocketProcessor);

}
