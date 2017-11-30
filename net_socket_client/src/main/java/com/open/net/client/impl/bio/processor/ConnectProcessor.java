package com.open.net.client.impl.bio.processor;

import com.open.net.client.impl.bio.BioClient;
import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.BaseClient;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * author       :   Administrator
 * created on   :   2017/11/30
 * description  :
 */

public class ConnectProcessor implements Runnable {

    private final int STATE_CLOSE			= 1<<1;//socket关闭
    private final int STATE_CONNECT_START	= 1<<2;//开始连接server
    private final int STATE_CONNECT_SUCCESS	= 1<<3;//连接成功
    private final int STATE_CONNECT_FAILED	= 1<<4;//连接失败

    private String mIp ="192.168.1.1";
    private int    mPort =9999;

    private IConnectStatusListener mConnectStatusListener;
    private boolean isClosedByUser = false;

    private int state = STATE_CLOSE;

    private BaseClient mClient;
    private WriteRunnable mWriteProcessor;
    private Thread mWriteThread =null;
    private Thread mReadThread =null;


    public ConnectProcessor(BaseClient mClient , String ip, int port, IConnectStatusListener mConnectionStatusListener) {
        this.mClient = mClient;
        this.mIp = ip;
        this.mPort = port;
        this.mConnectStatusListener = mConnectionStatusListener;
    }

    public boolean isClosed(){
        return state == STATE_CLOSE;
    }

    public boolean isConnected(){
        return state == STATE_CONNECT_SUCCESS;
    }

    public boolean isConnecting(){
        return state == STATE_CONNECT_START;
    }

    public void setCloseByUser(boolean isClosedByUser){
        this.isClosedByUser = isClosedByUser;
    }

    public void close(){
        try {
            if(state!=STATE_CLOSE)
            {
                mClient.close();

                try {
                    if(null!= mWriteThread && mWriteThread.isAlive())
                    {
                        mWriteThread.interrupt();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    mWriteThread =null;
                }

                try {
                    if(null!= mReadThread && mReadThread.isAlive())
                    {
                        mReadThread.interrupt();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    mReadThread =null;
                }

                state=STATE_CLOSE;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //-------------------------------------------------------------------------------------------
    public void wakeUp(){
        if(null != mWriteProcessor){
            mWriteProcessor.wakeup();
        }
    }

    public void run() {
        try {
            isClosedByUser = false;
            state=STATE_CONNECT_START;
            Socket mSocket =new Socket();
            mSocket.connect(new InetSocketAddress(mIp, mPort), 15*1000);
            ((BioClient)mClient).init(mSocket);

            mWriteProcessor = new WriteRunnable();
            mWriteThread =new Thread(mWriteProcessor);
            mReadThread =new Thread(new ReadRunnable());
            mWriteThread.start();
            mReadThread.start();
            state=STATE_CONNECT_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            state=STATE_CONNECT_FAILED;
        }finally {
            if(!(state == STATE_CONNECT_SUCCESS || isClosedByUser)) {
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionFailed();
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
                while( state == STATE_CONNECT_SUCCESS) {
                    if(!mClient.write()){
                        break;
                    }
                    synchronized (lock) {
                        lock.wait();
                    }
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("client close when write");
            if(!isClosedByUser){
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionFailed();
                }
            }
        }
    }

    private class ReadRunnable implements Runnable{
        public void run() {
            mClient.read();
            System.out.println("client close when read");
            if(!isClosedByUser){
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionFailed();
                }
            }
        }
    }

}