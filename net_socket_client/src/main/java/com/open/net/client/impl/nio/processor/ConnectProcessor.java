package com.open.net.client.impl.nio.processor;

import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.BaseClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

/**
 * author       :   Administrator
 * created on   :   2017/11/30
 * description  :
 */

public class ConnectProcessor implements Runnable {

    private final String TAG = "ConnectProcessor";

    private final int STATE_CLOSE           =   1<<1;//socket关闭
    private final int STATE_CONNECT_START   =   1<<2;//开始连接server
    private final int STATE_CONNECT_SUCCESS =   1<<3;//连接成功
    private final int STATE_CONNECT_FAILED  =   1<<4;//连接失败

    private String ip ="192.168.1.1";
    private int port =9999;

    private SocketChannel mSocketChannel;
    private Selector mSelector;
    private int state= STATE_CLOSE;
    private IConnectStatusListener mConnectStatusListener;
    private boolean isClosedByUser = false;

    private BaseClient mClient;

    public ConnectProcessor(BaseClient mClient,String ip, int port, IConnectStatusListener mNioConnectionListener) {
        this.mClient = mClient;
        this.ip = ip;
        this.port = port;
        this.mConnectStatusListener = mNioConnectionListener;
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
        if(state != STATE_CLOSE){
            mClient.close();
            state = STATE_CLOSE;
        }
    }

    //-------------------------------------------------------------------------------------------
    public void wakeUp(){
        if(null != mSelector){
            mSelector.wakeup();
        }
    }

    private boolean finishConnection(SelectionKey key) throws IOException {
        boolean result;
        SocketChannel socketChannel = (SocketChannel) key.channel();
        result= socketChannel.finishConnect();//没有网络的时候也返回true
        if(result) {
            key.interestOps(SelectionKey.OP_READ);
            state=STATE_CONNECT_SUCCESS;
            if(null != mConnectStatusListener){
                mConnectStatusListener.onConnectionSuccess();
            }
        }
        return result;
    }

    private void setConnectionTimeout(long timeout){
        new Thread(new NioConnectStateWatcher(timeout)).start();
    }

    //-------------------------------------------------------------------------------------------

    @Override
    public void run() {
        try {
            state = STATE_CONNECT_START;
            setConnectionTimeout(10);
            mSelector = SelectorProvider.provider().openSelector();
            mSocketChannel = SocketChannel.open();
            mSocketChannel.configureBlocking(false);

            InetSocketAddress address=new InetSocketAddress(ip, port);
            mSocketChannel.connect(address);
            mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT,mClient);

            boolean isExit = false;
            while(state != STATE_CLOSE && !isExit) {

                mSelector.select();
                Iterator<SelectionKey> selectedKeys = mSelector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key =  selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isConnectable()) {

                        finishConnection(key);

                    }else if (key.isReadable()) {
                        BaseClient mClient = (BaseClient) key.attachment();
                        boolean ret = mClient.read();
                        if(!ret){
                            isExit = true;
                            key.cancel();
                            key.channel().close();
                            break;
                        }

                    }else if (key.isWritable()) {
                        BaseClient mClient = (BaseClient) key.attachment();
                        boolean ret = mClient.write();
                        if(!ret){
                            isExit = true;
                            key.cancel();
                            key.channel().close();
                            break;
                        }
                        key.interestOps(SelectionKey.OP_READ);
                    }
                }

                if(isExit){
                    break;
                }

                if(!mClient.mWriteMessageQueen.mQueen.isEmpty()) {
                    SelectionKey key= mSocketChannel.keyFor(mSelector);
                    key.interestOps(SelectionKey.OP_WRITE);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            close();
            if(!isClosedByUser){
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionFailed();
                }
            }
        }
    }

    private class NioConnectStateWatcher implements Runnable {

        public long timeout;//单位是秒

        public NioConnectStateWatcher(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public void run() {
            long start = System.nanoTime();
            while(true){
                if(isConnecting()){
                    if((System.nanoTime() - start)/1000000000 > timeout){
                        close();
                        break;
                    }else{
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                }else{
                    break;
                }
            }
        }
    }
}