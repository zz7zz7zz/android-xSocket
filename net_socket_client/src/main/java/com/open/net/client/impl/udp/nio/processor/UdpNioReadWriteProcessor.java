package com.open.net.client.impl.udp.nio.processor;

import com.open.net.client.impl.udp.nio.UdpNioConnectListener;
import com.open.net.client.impl.udp.nio.UdpNioClient;
import com.open.net.client.structures.BaseClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   连/读/写 处理器
 */

public final class UdpNioReadWriteProcessor {

    private String TAG = "UdpNioReadWriteProcessor";

    private static int G_SOCKET_ID = 0;

    private int mSocketId;
    private String  mIp ="192.168.1.1";
    private int     mPort =9999;

    private BaseClient mClient;
    private UdpNioConnectListener mNioConnectListener;

    private DatagramChannel mSocketChannel;
    private Selector   mSelector;

    private ConnectRunnable mConnectProcessor;
    private Thread mConnectThread =null;

    private boolean closed = false;

    public UdpNioReadWriteProcessor(String mIp, int mPort , BaseClient mClient, UdpNioConnectListener mNioConnectListener) {
        G_SOCKET_ID++;

        this.mSocketId = G_SOCKET_ID;
        this.mIp = mIp;
        this.mPort = mPort;
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
                mSelector.close();
                mSocketChannel.socket().close();
                mSocketChannel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        mSocketChannel = null;
        mSelector = null;

        wakeUp();
    }

    public void wakeUp(){
        if(null !=mConnectProcessor){
            mConnectProcessor.wakeUp();
        }
    }

    public void onSocketExit(int exit_code){
        close();
        System.out.println(TAG + "onSocketExit mSocketId " + mSocketId + " exit_code " + exit_code);
        if(null != mNioConnectListener){
            mNioConnectListener.onConnectFailed(UdpNioReadWriteProcessor.this);
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

                mSelector = Selector.open();
                mSocketChannel = DatagramChannel.open();
                mSocketChannel.configureBlocking(false);

                InetSocketAddress address=new InetSocketAddress(mIp, mPort);
//                mSocketChannel.socket().bind(address);
                mSocketChannel.connect(address);//可以将DatagramChannel“连接”到网络中的特定地址的。由于UDP是无连接的，连接到特定地址并不会像TCP通道那样创建一个真正的连接。而是锁住DatagramChannel ，让其只能从特定地址收发数据。
                mSocketChannel.register(mSelector, SelectionKey.OP_READ,mClient);

                ((UdpNioClient)mClient).init(mSocketChannel);
                if(null != mNioConnectListener){
                    mNioConnectListener.onConnectSuccess(UdpNioReadWriteProcessor.this,mSocketChannel);
                }

                //开始读写
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

            } catch (Exception e) {
                e.printStackTrace();
            }

            onSocketExit(1);
        }
    }
}