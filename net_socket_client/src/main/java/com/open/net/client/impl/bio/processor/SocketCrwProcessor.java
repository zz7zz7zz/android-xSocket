package com.open.net.client.impl.bio.processor;

import com.open.net.client.impl.bio.BioClient;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.IConnectResultListener;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连/读/写 处理器
 */

public class SocketCrwProcessor implements Runnable {

    private final int STATE_CLOSE			= 1<<1;//socket关闭
    private final int STATE_CONNECT_START	= 1<<2;//开始连接server
    private final int STATE_CONNECT_SUCCESS	= 1<<3;//连接成功
    private final int STATE_CONNECT_FAILED	= 1<<4;//连接失败

    private String mIp ="192.168.1.1";
    private int    mPort =9999;

    private IConnectResultListener mConnectStatusListener;
    private boolean isClosedByUser = false;

    private int state = STATE_CLOSE;

    private BaseClient mClient;
    private WriteRunnable mWriteProcessor;
    private Thread mWriteThread =null;
    private Thread mReadThread =null;


    public SocketCrwProcessor(BaseClient mClient , String ip, int port, IConnectResultListener mConnectionStatusListener) {
        this.mClient = mClient;
        this.mIp = ip;
        this.mPort = port;
        this.mConnectStatusListener = mConnectionStatusListener;
    }

    public boolean isConnected(){
        return state == STATE_CONNECT_SUCCESS;
    }

    public boolean isConnecting(){
        return state == STATE_CONNECT_START;
    }

    public boolean isClosed(){
        return state == STATE_CLOSE;
    }

    public void setConnectStart(){
        state = STATE_CONNECT_START;
    }

    public void setCloseByUser(boolean isClosedByUser){
        this.isClosedByUser = isClosedByUser;
    }

    public synchronized void close(){
        try {
            if(state!=STATE_CLOSE) {
                state = STATE_CLOSE;
                wakeUp();
                mClient.onClose();

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void wakeUp(){
        if(null != mWriteProcessor){
            mWriteProcessor.wakeup();
        }
    }

    public synchronized void onSocketExit(int exit_code , SocketConnectToken mSocketConnectToken){

        mSocketConnectToken.onSocketExit();
        boolean isWriterReaderExit = mSocketConnectToken.isWriterReaderExit();
        System.out.println("client onClose when " + (exit_code == 1 ? "onWrite" : "onRead") + " isWriterReaderExit " + isWriterReaderExit);
        close();
        if(isWriterReaderExit){
            if(!isClosedByUser){
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionFailed();
                }
            }
        }
    }
    //-------------------------------------------------------------------------------------------

    public void run() {
        try {
            isClosedByUser = false;

            Socket mSocket =new Socket();
            mSocket.connect(new InetSocketAddress(mIp, mPort), 15*1000);
            ((BioClient)mClient).init(mSocket);
            state = STATE_CONNECT_SUCCESS;

            SocketConnectToken mSocketConnectToken = new SocketConnectToken();
            mWriteProcessor = new WriteRunnable(mSocketConnectToken);
            mWriteThread =new Thread(mWriteProcessor);
            mWriteThread.start();
            mReadThread =new Thread(new ReadRunnable(mSocketConnectToken));
            mReadThread.start();

            if(null != mConnectStatusListener){
                mConnectStatusListener.onConnectionSuccess();
            }
            return;
        } catch (Exception e) {
            e.printStackTrace();
            state=STATE_CONNECT_FAILED;
        }

        if(!(state == STATE_CONNECT_SUCCESS || isClosedByUser)) {
            if(null != mConnectStatusListener){
                mConnectStatusListener.onConnectionFailed();
            }
        }
    }

    //-------------------------------------------------------------------------------------------
    private class WriteRunnable implements Runnable {

        private final Object lock=new Object();
        private SocketConnectToken mSocketConnectToken;

        public WriteRunnable(SocketConnectToken mSocketConnectToken) {
            this.mSocketConnectToken = mSocketConnectToken;
        }

        public void wakeup(){
            synchronized (lock) {
                lock.notifyAll();
            }
        }

        public void run() {
            try {
                while( state == STATE_CONNECT_SUCCESS) {
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

            onSocketExit(1,mSocketConnectToken);
        }
    }

    private class ReadRunnable implements Runnable{

        private SocketConnectToken mSocketConnectToken;

        public ReadRunnable(SocketConnectToken mSocketConnectToken) {
            this.mSocketConnectToken = mSocketConnectToken;
        }

        public void run() {
            mClient.onRead();

            onSocketExit(2,mSocketConnectToken);
        }
    }

    private class SocketConnectToken {
        private int count = 2;//读写线程

        //当返回值等于0,说明完全退出
        public int onSocketExit(){
            count -- ;
            return count;
        }

        public boolean isWriterReaderExit(){
            return count == 0;
        }
    }
}