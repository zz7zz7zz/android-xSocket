package com.open.net.client.impl.tcp.nio.processor;

import com.open.net.client.impl.tcp.nio.NioConnectListener;
import com.open.net.client.structures.BaseClient;

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

public final class NioReadWriteProcessor {

    private String TAG = "NioReadWriteProcessor";

    private static int G_SOCKET_ID = 0;

    private int     mSocketId;
    private String  mIp             = "192.168.1.1";
    private int     mPort           = 9999;
    private long    connect_timeout = 10000;

    private BaseClient mClient;
    private NioConnectListener mNioConnectListener;

    //------------------------------------------------------------------------------------------
    private SocketChannel mSocketChannel;
    private Selector   mSelector;

    private ConnectRunnable mConnectProcessor;
    private Thread mConnectThread =null;

    private boolean closed = false;

    public NioReadWriteProcessor(String mIp, int mPort, long   connect_timeout , BaseClient mClient, NioConnectListener mNioConnectListener) {
        G_SOCKET_ID++;

        this.mSocketId = G_SOCKET_ID;
        this.mIp = mIp;
        this.mPort = mPort;
        this.connect_timeout = connect_timeout;
        this.mClient = mClient;
        this.mNioConnectListener = mNioConnectListener;
    }

    public void start(){
        mConnectProcessor = new ConnectRunnable();
        mConnectThread = new Thread(mConnectProcessor);
        mConnectThread.start();
    }

    public synchronized void close(){
        closed = true;

        if(null!= mSocketChannel) {
            try {
                SelectionKey key = mSocketChannel.keyFor(mSelector);
                if(null != key){
                    key.cancel();
                }
                mSocketChannel.socket().close();
                mSocketChannel.close();
                mSocketChannel = null;
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        if(null != mSelector){
            try {
                mSelector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSelector = null;
        }

        wakeUp();
    }

    public void wakeUp(){
        if(null !=mConnectProcessor){
            mConnectProcessor.wakeUp();
        }
    }

    public void onSocketExit(int exit_code){
        System.out.println(TAG + "onSocketExit mSocketId " + mSocketId + " exit_code " + exit_code);

        close();
        if(null != mNioConnectListener){
            mNioConnectListener.onConnectFailed(NioReadWriteProcessor.this);
        }
    }

    private class ConnectRunnable implements Runnable {

        public void wakeUp(){
            if(null != mSelector){
                mSelector.wakeup();
            }
        }

        @Override
        public void run() {
            try {

                mSelector = SelectorProvider.provider().openSelector();
                mSocketChannel = SocketChannel.open();
                mSocketChannel.configureBlocking(false);

                InetSocketAddress address=new InetSocketAddress(mIp, mPort);
                mSocketChannel.connect(address);
                mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT,mClient);

                //处理连接
                boolean isConnectSuccess = connect(connect_timeout);

                //开始读写
                if(isConnectSuccess){
                    boolean isExit = false;
                    while(!isExit) {

                        int readKeys = mSelector.select();
                        if(readKeys > 0){
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
                        }

                        if(isExit || closed){
                            break;
                        }

                        if(!mClient.mWriteMessageQueen.mWriteQueen.isEmpty()) {
                            SelectionKey key= mSocketChannel.keyFor(mSelector);
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            onSocketExit(1);
        }

        private boolean connect(long connect_timeout) throws IOException {
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

        private boolean finishConnection(SelectionKey key){
            boolean connectRet = true;
            try{
                boolean result;
                SocketChannel socketChannel = (SocketChannel) key.channel();
                result= socketChannel.finishConnect();//没有网络的时候也返回true;连不上的情况下会抛出java.net.ConnectException: Connection refused
                if(result) {
                    key.interestOps(SelectionKey.OP_READ);
                    if(null != mNioConnectListener){
                        mNioConnectListener.onConnectSuccess(NioReadWriteProcessor.this,mSocketChannel);
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
                connectRet = false;
            }
            return connectRet;
        }

    }
}