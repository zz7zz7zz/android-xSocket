package com.open.net.client.impl.bio.processor;

import com.open.net.client.structures.BaseClient;
import com.open.net.client.impl.bio.IBioConnectListener;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连/读/写 处理器
 */

public class SocketProcessor {

    private String mIp    = "192.168.1.1";
    private int    mPort  = 9999;

    private long connect_token;
    private IBioConnectListener mConnectStatusListener;

    private ConnectRunnable mConnectProcessor;
    private WriteRunnable mWriteProcessor;
    private ReadRunnable mReadProcessor;

    private Thread mConnectThread =null;
    private Thread mWriteThread =null;
    private Thread mReadThread =null;

    private int r_w_count = 2;//读写线程是否都退出了

    private BaseClient mClient;

    public SocketProcessor(long connect_token ,BaseClient mClient , String mIp, int mPort, IBioConnectListener mConnectionStatusListener) {
        this.connect_token = connect_token;
        this.mClient = mClient;
        this.mIp = mIp;
        this.mPort = mPort;
        this.mConnectStatusListener = mConnectionStatusListener;
    }

    public void start(){
        mConnectProcessor = new ConnectRunnable();
        mConnectThread = new Thread(mConnectProcessor);
        mConnectThread.start();
    }

    public synchronized void close(){
        wakeUp();

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
        System.out.println("client onClose when " + (exit_code == 1 ? "onWrite" : "onRead") + " isWriterReaderExit " + isWriterReaderExit);
        close();
        if(isWriterReaderExit){
            if(null != mConnectStatusListener){
                mConnectStatusListener.onConnectFailed(connect_token);
            }
        }
    }

    //-------------------------------------------------------------------------------------------
    private class ConnectRunnable implements Runnable{

        @Override
        public void run() {
            boolean connectRet = false;
            try {

                Socket mSocket  =new Socket();
                mSocket.connect(new InetSocketAddress(mIp, mPort), 15*1000);

                mWriteProcessor = new WriteRunnable();
                mReadProcessor = new ReadRunnable();

                mWriteThread =new Thread(mWriteProcessor);
                mReadThread =new Thread(mReadProcessor);

                mWriteThread.start();
                mReadThread.start();

                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectSuccess(connect_token,mSocket);
                }
                connectRet = true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            if(!connectRet){
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectFailed(connect_token);
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