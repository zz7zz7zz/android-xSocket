package com.open.net.client.impl.nio;

import com.open.net.client.impl.nio.processor.SocketCrwProcessor;
import com.open.net.client.structures.IConnectResultListener;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.TcpAddress;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接器
 */

public final class NioConnector {

    private NioClient mClient;

    private TcpAddress[] tcpArray   = null;
    private int          index      = -1;

    private SocketCrwProcessor mConnectProcessor = null;
    private Thread mConnectProcessorThread = null;

    private BaseMessageProcessor mMessageProcessor = null;
    private IConnectResultListener mConnectStatusListener = null;

    public NioConnector(NioClient mClient, TcpAddress[] tcpArray, BaseMessageProcessor mMessageProcessor, IConnectResultListener mConnectStatusListener) {
        this.mClient = mClient;
        this.tcpArray = tcpArray;
        this.mMessageProcessor = mMessageProcessor;
        this.mConnectStatusListener = mConnectStatusListener;
    }

    //-------------------------------------------------------------------------------------------
    public void setConnectAddress(TcpAddress[] tcpArray ){
        this.tcpArray = tcpArray;
    }

    //-------------------------------------------------------------------------------------------
    public void checkConnect(){
        //1.没有连接,需要进行重连
        //2.在连接不成功，并且也不在重连中时，需要进行重连;
        if(null == mConnectProcessor){
            startConnect();
        }else if(!mConnectProcessor.isConnected() && !mConnectProcessor.isConnecting()){
            startConnect();
        }else{
            if(mConnectProcessor.isConnected()){
                mConnectProcessor.wakeUp();
            }else{
                //说明正在重连中
            }
        }
    }

    //-------------------------------------------------------------------------------------------
    public synchronized void connect() {
        startConnect();
    }

    public synchronized void reconnect(){
        stopConnect(true);
        //reset the ip/port index of tcpArray
        if(index+1 >= tcpArray.length || index+1 < 0){
            index = -1;
        }
        startConnect();
    }

    public synchronized void disconnect(){
        stopConnect(true);
    }

    private synchronized void startConnect(){
        //已经在连接中就不再进行连接
        if(null != mConnectProcessor && !mConnectProcessor.isClosed()){
            return;
        }

        index++;
        if(index < tcpArray.length && index >= 0){
            stopConnect(false);
            mConnectProcessor = new SocketCrwProcessor(mClient, tcpArray[index].ip,tcpArray[index].port, mMessageProcessor,mConnectStatusListener);
            mConnectProcessorThread =new Thread(mConnectProcessor);
            mConnectProcessorThread.start();
        }else{
            index = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            mClient.clearUnreachableMessages();
        }
    }

    private synchronized void stopConnect(boolean isCloseByUser){
        try {

            if(null != mConnectProcessor) {
                mConnectProcessor.setCloseByUser(isCloseByUser);
                mConnectProcessor.close();
            }
            mConnectProcessor = null;

            if( null!= mConnectProcessorThread && mConnectProcessorThread.isAlive() ) {
                mConnectProcessorThread.interrupt();
            }
            mConnectProcessorThread =null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
