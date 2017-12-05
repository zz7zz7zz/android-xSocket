package com.open.net.client.impl.udp.bio;

import com.open.net.client.impl.udp.bio.processor.SocketProcessor;
import com.open.net.client.structures.IConnectListener;
import com.open.net.client.structures.TcpAddress;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * author       :   Administrator
 * created on   :   2017/12/4
 * description  :
 */

public class UDPBioConnector {

    private final int STATE_CLOSE			= 1<<1;//socket关闭
    private final int STATE_CONNECT_START	= 1<<2;//开始连接server
    private final int STATE_CONNECT_SUCCESS	= 1<<3;//连接成功
    private final int STATE_CONNECT_FAILED	= 1<<4;//连接失败

    private UDPBioClient mClient;
    private TcpAddress[]    tcpArray   = null;
    private int             index      = -1;

    private int state       = STATE_CLOSE;
    private SocketProcessor mSocketProcessor;
    private IConnectListener mIConnectListener;

    private IUdpBioConnectListener mProxyConnectStatusListener = new IUdpBioConnectListener() {

        @Override
        public void onConnectSuccess(SocketProcessor mSocketProcessor, DatagramSocket mSocket, DatagramPacket mWriteDatagramPacket, DatagramPacket mReadDatagramPacket) {
            if(mSocketProcessor != UDPBioConnector.this.mSocketProcessor){//两个请求都不是同一个，说明是之前连接了，现在重连了
                SocketProcessor dropProcessor = mSocketProcessor;
                if(null != dropProcessor){
                    dropProcessor.close();
                }
                return;
            }

            if(null !=mIConnectListener ){
                mIConnectListener.onConnectionSuccess();
            }

            mClient.init(mSocket,mWriteDatagramPacket,mReadDatagramPacket);
            state = STATE_CONNECT_SUCCESS;
        }

        @Override
        public synchronized void onConnectFailed(SocketProcessor mSocketProcessor) {
            if(mSocketProcessor != UDPBioConnector.this.mSocketProcessor){//两个请求都不是同一个，说明是之前连接了，现在重连了
                SocketProcessor dropProcessor = mSocketProcessor;
                if(null != dropProcessor){
                    dropProcessor.close();
                }
                return;
            }

            if(null !=mIConnectListener ){
                mIConnectListener.onConnectionFailed();
            }

            state = STATE_CONNECT_FAILED;
            connect();//try to connect next ip port
        }
    };

    public UDPBioConnector(UDPBioClient mClient, IConnectListener mIConnectListener) {
        this.mClient = mClient;
        this.mIConnectListener = mIConnectListener;
    }

    public void setConnectAddress(TcpAddress[] tcpArray ){
        this.index = -1;
        this.tcpArray = tcpArray;
    }

    //-------------------------------------------------------------------------------------------
    public boolean isConnected(){
        return state == STATE_CONNECT_SUCCESS;
    }

    public boolean isConnecting(){
        return state == STATE_CONNECT_START;
    }

    public boolean isClosed(){
        return state == STATE_CLOSE;
    }

    //-------------------------------------------------------------------------------------------
    public synchronized void connect() {
        startConnect();
    }

    public synchronized void reconnect(){
        stopConnect();
        //reset the ip/port index of tcpArray
        if(index+1 >= tcpArray.length || index+1 < 0){
            index = -1;
        }
        startConnect();
    }

    public synchronized void disconnect(){
        stopConnect();
    }

    //-------------------------------------------------------------------------------------------
    public void checkConnect() {
        //1.没有连接,需要进行重连
        //2.在连接不成功，并且也不在重连中时，需要进行重连;
        if(null == mSocketProcessor){
            startConnect();
        }else if(!isConnected() && !isConnecting()){
            startConnect();
        }else{
            if(isConnected()){
                mSocketProcessor.wakeUp();
            }else{
                //说明正在重连中
            }
        }
    }

    private void startConnect() {
        //非关闭状态(连接成功，或者正在重连中)
        if(!isClosed()){
            return;
        }

        index++;
        if(index < tcpArray.length && index >= 0){
            state = STATE_CONNECT_START;
            mSocketProcessor = new SocketProcessor(tcpArray[index].ip,tcpArray[index].port,mClient,mProxyConnectStatusListener);
            mSocketProcessor.start();
        }else{
            index = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            mClient.clearUnreachableMessages();
        }
    }

    private void stopConnect() {
        state = STATE_CLOSE;
        mClient.onClose();

        if(null != mSocketProcessor) {
            mSocketProcessor.close();
        }
        mSocketProcessor = null;
    }

}
