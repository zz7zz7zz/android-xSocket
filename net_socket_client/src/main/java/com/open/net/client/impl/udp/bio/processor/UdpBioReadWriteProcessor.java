package com.open.net.client.impl.udp.bio.processor;

import com.open.net.client.impl.udp.bio.UdpBioConnectListener;
import com.open.net.client.impl.udp.bio.UdpBioClient;
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

public class UdpBioReadWriteProcessor {

    private String TAG = "UdpBioReadWriteProcessor";

    private static int G_SOCKET_ID = 0;

    private int     mSocketId;
    private String mIp    = "192.168.1.1";
    private int    mPort  = 9999;

    private BaseClient mClient;
    private UdpBioConnectListener mConnectStatusListener;

    private DatagramSocket mSocket;

    private ConnectRunnable mConnectProcessor;
    private WriteRunnable mWriteProcessor;
    private ReadRunnable mReadProcessor;

    private Thread       mConnectThread =null;
    private Thread       mWriteThread =null;
    private Thread       mReadThread =null;

    private int r_w_count = 2;//读写线程是否都退出了

    public UdpBioReadWriteProcessor(String mIp, int mPort, BaseClient mClient, UdpBioConnectListener mConnectionStatusListener) {
        G_SOCKET_ID++;

        this.mSocketId = G_SOCKET_ID;
        this.mIp = mIp;
        this.mPort = mPort;
        this.mClient = mClient;
        this.mConnectStatusListener = mConnectionStatusListener;
    }

    public void start(){
        mConnectProcessor = new ConnectRunnable();
        mConnectThread = new Thread(mConnectProcessor);
        mConnectThread.start();
    }

    public synchronized void close(){

        wakeUp();

        if(null != mSocket){
            mSocket.close();
        }

        try {
            if(null!= mConnectThread && mConnectThread.isAlive()) {
                mConnectThread.interrupt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            mConnectThread =null;
        }

        try {
            if(null!= mWriteThread && mWriteThread.isAlive()) {
                mWriteThread.interrupt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            mWriteThread =null;
        }

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
        if(null != mWriteProcessor){
            mWriteProcessor.wakeup();
        }
    }

    public synchronized void onSocketExit(int exit_code){

        --r_w_count;
        boolean isWriterReaderExit = (r_w_count <= 0);
        System.out.println(TAG + "onSocketExit mSocketId " + mSocketId + " exit_code " + exit_code + (exit_code == 1 ? " onWrite" : " onRead")+ " isWriterReaderExit " + isWriterReaderExit);
        close();
        if(isWriterReaderExit){
            if(null != mConnectStatusListener){
                mConnectStatusListener.onConnectFailed(UdpBioReadWriteProcessor.this);
            }
        }
    }

    private class ConnectRunnable implements Runnable{

        @Override
        public void run() {
            byte[] mWriteBuff  = ((UdpBioClient)mClient).mWriteBuff;
            byte[] mReadBuff   = ((UdpBioClient)mClient).mReadBuff;

            boolean connectRet;
            try {
                mSocket = new DatagramSocket();  //创建套接字
                InetAddress address = InetAddress.getByName(mIp);//服务器地址
                DatagramPacket mWriteDatagramPacket = new DatagramPacket(mWriteBuff, mWriteBuff.length, address, mPort);//创建发送方的数据报信息
                DatagramPacket mReadDatagramPacket  = new DatagramPacket(mReadBuff, mReadBuff.length);//创建发送方的数据报信息

                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectSuccess(UdpBioReadWriteProcessor.this,mSocket,mWriteDatagramPacket,mReadDatagramPacket);
                }
                connectRet = true;

                mWriteProcessor = new WriteRunnable();
                mReadProcessor = new ReadRunnable();

                mWriteThread =new Thread(mWriteProcessor);
                mReadThread =new Thread(mReadProcessor);

                mWriteThread.start();
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
                    mConnectStatusListener.onConnectFailed(UdpBioReadWriteProcessor.this);
                }
            }
        }
    }

    private class WriteRunnable implements Runnable {

        private final Object lock=new Object();

        public void wakeup(){
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        public void run() {
            try {
                while(true) {
                    if(!mClient.onWrite()){
                        break;
                    }
                    synchronized (lock) {
                        lock.wait();
                    }
                }
            }catch (Exception e) {
                e.printStackTrace();
            }

            onSocketExit(1);
        }
    }

    private class ReadRunnable implements Runnable{

        public void run() {
            mClient.onRead();
            onSocketExit(2);
        }
    }
}
