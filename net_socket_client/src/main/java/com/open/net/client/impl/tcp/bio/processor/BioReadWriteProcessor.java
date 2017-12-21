package com.open.net.client.impl.tcp.bio.processor;

import com.open.net.client.structures.BaseClient;
import com.open.net.client.impl.tcp.bio.BioConnectListener;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连/读/写 处理器
 */

public class BioReadWriteProcessor {

    private String TAG = "BioReadWriteProcessor";

    private static int G_SOCKET_ID = 0;

    private int     mSocketId;
    private String  mIp             = "192.168.1.1";
    private int     mPort           = 9999;
    private long    connect_timeout = 10000;

    private BaseClient mClient;
    private BioConnectListener mConnectStatusListener;

    //------------------------------------------------------------------------------------------
    private Socket mSocket =null;
    private OutputStream mOutputStream =null;
    private InputStream mInputStream =null;

    private ConnectRunnable mConnectProcessor;
    private WriteRunnable mWriteProcessor;
    private ReadRunnable mReadProcessor;

    private Thread mConnectThread =null;
    private Thread mWriteThread =null;
    private Thread mReadThread =null;

    private int r_w_count = 2;//读写线程是否都退出了

    public BioReadWriteProcessor(String mIp, int mPort, long   connect_timeout, BaseClient mClient, BioConnectListener mConnectionStatusListener) {
        G_SOCKET_ID++;

        this.mSocketId = G_SOCKET_ID;
        this.mIp = mIp;
        this.mPort = mPort;
        this.connect_timeout = connect_timeout;
        this.mClient = mClient;
        this.mConnectStatusListener = mConnectionStatusListener;
    }

    //------------------------------------------------------------------------------------------
    public void start(){
        mConnectProcessor = new ConnectRunnable();
        mConnectThread = new Thread(mConnectProcessor);
        mConnectThread.start();
    }

    public synchronized void close(){

        wakeUp();

        try {
            if(null!= mOutputStream) {
                mOutputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            mOutputStream =null;
        }

        try {
            if(null!= mInputStream) {
                mInputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            mInputStream =null;
        }

        try {
            if(null!= mSocket) {
                mSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            mSocket =null;
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
                mConnectStatusListener.onConnectFailed(BioReadWriteProcessor.this);
            }
        }
    }

    //-------------------------------------------------------------------------------------------
    private class ConnectRunnable implements Runnable{

        @Override
        public void run() {
            boolean connectRet = false;
            try {

                mSocket  =new Socket();
                mSocket.connect(new InetSocketAddress(mIp, mPort), (int) connect_timeout);

                mOutputStream 	= mSocket.getOutputStream();
                mInputStream 	= mSocket.getInputStream();

                mWriteProcessor = new WriteRunnable();
                mReadProcessor = new ReadRunnable();

                mWriteThread =new Thread(mWriteProcessor);
                mReadThread =new Thread(mReadProcessor);

                mWriteThread.start();
                mReadThread.start();

                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectSuccess(BioReadWriteProcessor.this,mOutputStream,mInputStream);
                }
                connectRet = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            if(!connectRet){
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectFailed(BioReadWriteProcessor.this);
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