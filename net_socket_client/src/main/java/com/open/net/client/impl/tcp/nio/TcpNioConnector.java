package com.open.net.client.impl.tcp.nio;

import com.open.net.client.impl.tcp.nio.processor.SocketProcessor;
import com.open.net.client.structures.IConnectListener;
import com.open.net.client.structures.TcpAddress;

import java.io.IOException;
import java.nio.channels.SocketChannel;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连接器
 */

public final class TcpNioConnector {

    private final int STATE_CLOSE			= 0x1;//连接关闭
    private final int STATE_CONNECT_START	= 0x2;//连接开始
    private final int STATE_CONNECT_SUCCESS	= 0x3;//连接成功

    private long         connect_timeout = 10000;
    private TcpAddress[] mTcpAddress = null;
    private int          mConnectIndex = -1;
    private int          state = STATE_CLOSE;

    private TcpNioClient mClient;
    private IConnectListener mIConnectListener;
    private SocketProcessor mSocketProcessor;

    private ITcpNioConnectListener mProxyConnectStatusListener = new ITcpNioConnectListener() {
        @Override
        public synchronized void onConnectSuccess(SocketProcessor mSocketProcessor, SocketChannel socketChannel) throws IOException {
            if(mSocketProcessor != TcpNioConnector.this.mSocketProcessor){//两个请求都不是同一个，说明是之前连接了，现在重连了
                SocketProcessor dropProcessor = mSocketProcessor;
                if(null != dropProcessor){
                    dropProcessor.close();
                }
                return;
            }

            state = STATE_CONNECT_SUCCESS;
            mClient.init(socketChannel);

            if(null != mIConnectListener ){
                mIConnectListener.onConnectionSuccess();
            }
        }

        @Override
        public synchronized void onConnectFailed(SocketProcessor mSocketProcessor) {
            if(mSocketProcessor != TcpNioConnector.this.mSocketProcessor){//两个请求都不是同一个，说明是之前连接了，现在重连了
                SocketProcessor dropProcessor = mSocketProcessor;
                if(null != dropProcessor){
                    dropProcessor.close();
                }
                return;
            }

            state = STATE_CLOSE;
            connect();//try to connect next ip port

            if(null !=mIConnectListener ){
                mIConnectListener.onConnectionFailed();
            }
        }
    };

    public TcpNioConnector(TcpNioClient mClient, IConnectListener mConnectListener) {
        this.mClient = mClient;
        this.mIConnectListener = mConnectListener;
    }

    //-------------------------------------------------------------------------------------------
    public void setConnectAddress(TcpAddress[] tcpArray ){
        this.mConnectIndex = -1;
        this.mTcpAddress = tcpArray;
    }

    public void setConnectTimeout(long connect_timeout ){
        this.connect_timeout = connect_timeout;
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
        //reset the ip/port mConnectIndex of mTcpAddress
        if(mConnectIndex +1 >= mTcpAddress.length || mConnectIndex +1 < 0){
            mConnectIndex = -1;
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

        mConnectIndex++;
        if(mConnectIndex < mTcpAddress.length && mConnectIndex >= 0){
            state = STATE_CONNECT_START;
            mSocketProcessor = new SocketProcessor(mTcpAddress[mConnectIndex].ip, mTcpAddress[mConnectIndex].port, connect_timeout,mClient,mProxyConnectStatusListener);
            mSocketProcessor.start();
        }else{
            mConnectIndex = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            mClient.clearUnreachableMessages();
        }
    }

    private void stopConnect() {
        state = STATE_CLOSE;
        mClient.onClose();

        if(null != mSocketProcessor) {
            mSocketProcessor.close();
            mSocketProcessor = null;
        }
    }

}
