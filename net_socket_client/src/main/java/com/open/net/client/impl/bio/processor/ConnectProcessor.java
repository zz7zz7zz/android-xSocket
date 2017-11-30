package com.open.net.client.impl.bio.processor;

import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.message.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

/**
 * author       :   Administrator
 * created on   :   2017/11/30
 * description  :
 */

public class ConnectProcessor implements Runnable
{

    private final int STATE_CLOSE			= 1<<1;//socket关闭
    private final int STATE_CONNECT_START	= 1<<2;//开始连接server
    private final int STATE_CONNECT_SUCCESS	= 1<<3;//连接成功
    private final int STATE_CONNECT_FAILED	= 1<<4;//连接失败

    private String mIp ="192.168.1.1";
    private int    mPort =9999;

    private IConnectStatusListener mConnectStatusListener;
    private BaseMessageProcessor mMessageProcessor;
    private boolean isClosedByUser = false;

    private int state = STATE_CLOSE;

    private Socket mSocket =null;
    private OutputStream mOutputStream =null;
    private InputStream mInputStream =null;
    private WriteRunnable mWriter;
    private Thread mWriteThread =null;
    private Thread mReadThread =null;

    private BaseClient mClient;

    public ConnectProcessor(BaseClient mClient , String ip, int port, IConnectStatusListener mConnectionStatusListener, BaseMessageProcessor mMessageProcessor) {
        this.mClient = mClient;
        this.mIp = ip;
        this.mPort = port;
        this.mConnectStatusListener = mConnectionStatusListener;
        this.mMessageProcessor = mMessageProcessor;
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

    public void setCloseByUser(boolean isClosedbyUser){
        this.isClosedByUser = isClosedbyUser;
    }

    public void close(){
        try {
            if(state!=STATE_CLOSE)
            {
                try {
                    if(null!= mSocket)
                    {
                        mSocket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    mSocket =null;
                }

                try {
                    if(null!= mOutputStream)
                    {
                        mOutputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    mOutputStream =null;
                }

                try {
                    if(null!= mInputStream)
                    {
                        mInputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    mInputStream =null;
                }

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
        if(null != mWriter){
            mWriter.wakeup();
        }
    }

    public boolean write(){
        boolean writeRet = false;
        try{
            Message msg= mClient.pollWriteMessage();
            while(null != msg) {
                mOutputStream.write(msg.data,0,msg.length);
                mOutputStream.flush();
                mClient.removeWriteMessage(msg);
                msg= mClient.pollWriteMessage();
            }
            writeRet = true;
        } catch (SocketException e) {
            e.printStackTrace();//客户端主动socket.stopConnect()会调用这里 java.net.SocketException: Socket closed
        }catch (IOException e1) {
            e1.printStackTrace();//发送的时候出现异常，说明socket被关闭了(服务器关闭)java.net.SocketException: sendto failed: EPIPE (Broken pipe)
        }catch (Exception e2) {
            e2.printStackTrace();
        }
        return writeRet;
    }

    public boolean read(){
        try {
            int maximum_length = 8192;
            byte[] bodyBytes=new byte[maximum_length];
            int numRead;

            while((numRead= mInputStream.read(bodyBytes, 0, maximum_length))>0) {
                if(numRead > 0){
                    if(null!= mMessageProcessor) {
                        mMessageProcessor.onReceive(mClient, bodyBytes,0,numRead);
                        mMessageProcessor.onProcessReceivedMessage(mClient);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();//客户端主动socket.stopConnect()会调用这里 java.net.SocketException: Socket closed
        }catch (IOException e1) {
            e1.printStackTrace();
        }catch (Exception e2) {
            e2.printStackTrace();
        }
        return false;
    }

    public void run() {
        try {
            isClosedByUser = false;
            state=STATE_CONNECT_START;
            mSocket =new Socket();
            mSocket.connect(new InetSocketAddress(mIp, mPort), 15*1000);

            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();

            mWriter = new WriteRunnable();
            mWriteThread =new Thread(mWriter);
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
            synchronized (lock)
            {
                lock.notifyAll();
            }
        }

        public void run() {
            try {
                while(state != STATE_CLOSE && state== STATE_CONNECT_SUCCESS && null!= mOutputStream) {

                    if(!write()){
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

    private class ReadRunnable implements Runnable
    {
        public void run() {

            read();

            System.out.println("client close when read");

            if(!isClosedByUser){
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionFailed();
                }
            }
        }
    }
}