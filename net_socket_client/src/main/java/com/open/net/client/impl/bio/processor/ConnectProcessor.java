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

    private String ip ="192.168.1.1";
    private int port =9999;
    private int state = STATE_CLOSE;
    private IConnectStatusListener mConnectStatusListener;
    private BaseMessageProcessor mMessageProcessor;
    private boolean isClosedByUser = false;

    private Socket socket=null;
    private OutputStream outStream=null;
    private InputStream inStream=null;
    private WriteRunnable mWriter;
    private Thread writeThread =null;
    private Thread readThread =null;

    private BaseClient mClient;

    public ConnectProcessor(BaseClient mClient , String ip, int port, IConnectStatusListener mConnectionStatusListener, BaseMessageProcessor mMessageProcessor) {
        this.mClient = mClient;
        this.ip = ip;
        this.port = port;
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
                    if(null!=socket)
                    {
                        socket.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    socket=null;
                }

                try {
                    if(null!=outStream)
                    {
                        outStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    outStream=null;
                }

                try {
                    if(null!=inStream)
                    {
                        inStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    inStream=null;
                }

                try {
                    if(null!= writeThread && writeThread.isAlive())
                    {
                        writeThread.interrupt();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    writeThread =null;
                }

                try {
                    if(null!= readThread && readThread.isAlive())
                    {
                        readThread.interrupt();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally{
                    readThread =null;
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
                outStream.write(msg.data,0,msg.length);
                outStream.flush();
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

            while((numRead=inStream.read(bodyBytes, 0, maximum_length))>0) {
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
            socket=new Socket();
            socket.connect(new InetSocketAddress(ip, port), 15*1000);

            outStream=socket.getOutputStream();
            inStream=socket.getInputStream();

            mWriter = new WriteRunnable();
            writeThread =new Thread(mWriter);
            readThread =new Thread(new ReadRunnable());
            writeThread.start();
            readThread.start();

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
                while(state!=STATE_CLOSE && state== STATE_CONNECT_SUCCESS && null!=outStream ) {

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