package com.open.net.client.impl.udpbio.processor;

import com.open.net.client.impl.udpbio.IUdpBioConnectListener;
import com.open.net.client.impl.udpbio.UDPBioClient;
import com.open.net.client.structures.BaseClient;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * author       :   Administrator
 * created on   :   2017/12/4
 * description  :
 */

public class SocketProcessor {

    private String mIp    = "192.168.1.1";
    private int    mPort  = 9999;

    private BaseClient mClient;
    private IUdpBioConnectListener mConnectStatusListener;

    private ReadRunnable mReadProcessor;
    private Thread       mReadThread =null;

    public SocketProcessor(String mIp, int mPort, BaseClient mClient,IUdpBioConnectListener mConnectionStatusListener) {
        this.mIp = mIp;
        this.mPort = mPort;
        this.mClient = mClient;
        this.mConnectStatusListener = mConnectionStatusListener;
    }

    public void start(){
        byte[] mWriteBuff  = ((UDPBioClient)mClient).mWriteBuff;
        byte[] mReadBuff  = ((UDPBioClient)mClient).mReadBuff;

        boolean connectRet;
        try {
            DatagramSocket mSocket = new DatagramSocket();  //创建套接字
            InetAddress address = InetAddress.getByName(mIp);//服务器地址
            DatagramPacket mWriteDatagramPacket = new DatagramPacket(mWriteBuff, mWriteBuff.length, address, mPort);//创建发送方的数据报信息
            DatagramPacket mReadDatagramPacket  = new DatagramPacket(mReadBuff, mReadBuff.length);//创建发送方的数据报信息

            if(null != mConnectStatusListener){
                mConnectStatusListener.onConnectSuccess(SocketProcessor.this,mSocket,mWriteDatagramPacket,mReadDatagramPacket);
            }
            connectRet = true;

            mReadProcessor = new ReadRunnable();
            mReadThread =new Thread(mReadProcessor);
            mReadThread.start();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            connectRet = false;
        } catch (SocketException e) {
            e.printStackTrace();
            connectRet = false;
        }

        if(!connectRet){
            if(null != mConnectStatusListener){
                mConnectStatusListener.onConnectFailed(SocketProcessor.this);
            }
        }
    }

    public synchronized void close(){
        try {
            if(null!= mReadThread && mReadThread.isAlive()) {
                mReadThread.interrupt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            mReadThread =null;
        }
    }

    public void wakeUp(){
        mClient.onWrite();
    }

    public synchronized void onSocketExit(){
        close();
        if(null != mConnectStatusListener){
            mConnectStatusListener.onConnectFailed(SocketProcessor.this);
        }
    }
    private class ReadRunnable implements Runnable{

        public void run() {
            mClient.onRead();
            onSocketExit();
        }
    }
}
