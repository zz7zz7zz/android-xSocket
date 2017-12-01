package com.open.net.client.impl.nio.processor;

import com.open.net.client.impl.nio.NioClient;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.IConnectResultListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连/读/写 处理器
 */

public final class SocketCrwProcessor implements Runnable {

    private final String TAG = "SocketCrwProcessor";

    private final int STATE_CLOSE           =   1<<1;//socket关闭
    private final int STATE_CONNECT_START   =   1<<2;//开始连接server
    private final int STATE_CONNECT_SUCCESS =   1<<3;//连接成功
    private final int STATE_CONNECT_FAILED  =   1<<4;//连接失败

    private String ip ="192.168.1.1";
    private int port =9999;

    private SocketChannel mSocketChannel;
    private Selector mSelector;
    private int state= STATE_CLOSE;

    private BaseMessageProcessor mMessageProcessor;
    private IConnectResultListener mConnectStatusListener;
    private boolean isClosedByUser = false;

    private BaseClient mClient;

    public SocketCrwProcessor(BaseClient mClient, String ip, int port, BaseMessageProcessor mMessageProcessor, IConnectResultListener mNioConnectionListener) {
        this.mClient = mClient;
        this.ip = ip;
        this.port = port;
        this.mMessageProcessor = mMessageProcessor;
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
            mClient.onClose();
            state = STATE_CLOSE;
        }
    }

    //-------------------------------------------------------------------------------------------
    public void wakeUp(){
        if(null != mSelector){
            mSelector.wakeup();
        }
    }

    private boolean finishConnection(SelectionKey key){
        boolean connectRet = true;
        try{
            boolean result;
            SocketChannel socketChannel = (SocketChannel) key.channel();
            result= socketChannel.finishConnect();//没有网络的时候也返回true;连不上的情况下会抛出java.net.ConnectException: Connection refused
            if(result) {
                ((NioClient)mClient).init(mSocketChannel,mSelector,mMessageProcessor);
                key.interestOps(SelectionKey.OP_READ);
                state=STATE_CONNECT_SUCCESS;
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionSuccess();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            connectRet = false;
        }
        return connectRet;
    }

    private boolean connect(int connect_timeout) throws IOException {
        boolean isConnectSuccess = false;
        //连接
        int connectReady = 0;
        if(connect_timeout == -1){
            connectReady = mSelector.select();
        }else{
            connectReady = mSelector.select(connect_timeout);
        }
        if(connectReady > 0){
            Iterator<SelectionKey> selectedKeys = mSelector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                SelectionKey key = selectedKeys.next();
                selectedKeys.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isConnectable()) {
                    boolean ret = finishConnection(key);
                    isConnectSuccess = ret;
                    if(!ret){
                        key.cancel();
                        key.attach(null);
                        key.channel().close();
                        break;
                    }
                }
            }
        }else{
            isConnectSuccess = false;
            try{
                Iterator<SelectionKey> selectedKeys = mSelector.keys().iterator();
                while(selectedKeys.hasNext()){
                    SelectionKey key = selectedKeys.next();
                    key.cancel();
                    key.attach(null);
                    key.channel().close();
                }
            }catch(Exception e2){
                e2.printStackTrace();
            }
        }
        return isConnectSuccess;
    }

    @Override
    public void run() {
        try {
            state = STATE_CONNECT_START;
            mSelector = SelectorProvider.provider().openSelector();
            mSocketChannel = SocketChannel.open();
            mSocketChannel.configureBlocking(false);

            InetSocketAddress address=new InetSocketAddress(ip, port);
            mSocketChannel.connect(address);
            mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT,mClient);

            //处理连接
            boolean isConnectSuccess = connect(10000);

            //开始读写
            if(isConnectSuccess){
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

                        if (key.isReadable()) {
                            BaseClient mClient = (BaseClient) key.attachment();
                            boolean ret = mClient.onRead();
                            if(!ret){
                                isExit = true;
                                key.cancel();
                                key.attach(null);
                                key.channel().close();
                                break;
                            }

                        }else if (key.isWritable()) {
                            BaseClient mClient = (BaseClient) key.attachment();
                            boolean ret = mClient.onWrite();
                            if(!ret){
                                isExit = true;
                                key.cancel();
                                key.attach(null);
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
}