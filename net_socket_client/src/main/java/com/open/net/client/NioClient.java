package com.open.net.client;

import com.open.net.data.Message;
import com.open.net.data.TcpAddress;
import com.open.net.listener.BaseMessageProcessor;
import com.open.net.listener.IConnectStatusListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Administrator on 2017/11/17.
 */

public class NioClient{

    private final String TAG="NioClient";

    private TcpAddress[] tcpArray;
    private int index = -1;
    private BaseMessageProcessor mConnectReceiveListener;

    //无锁队列
    private ConcurrentLinkedQueue<Message> mMessageQueen = new ConcurrentLinkedQueue();
    private Thread mConnectionThread;
    private NioConnection mConnection;

    private IConnectStatusListener mConnectStatusListener = new IConnectStatusListener() {
        @Override
        public void onConnectionSuccess() {

        }

        @Override
        public void onConnectionFailed() {
            connect();//try to connect next ip port
        }
    };

    public NioClient(TcpAddress[] tcpArray, BaseMessageProcessor mConnectionReceiveListener) {
        this.tcpArray = tcpArray;
        this.mConnectReceiveListener = mConnectionReceiveListener;
    }

    public void setConnectAddress(TcpAddress[] tcpArray ){
        this.tcpArray = tcpArray;
    }

    public void sendMessage(Message msg){
        //1.没有连接,需要进行重连
        //2.在连接不成功，并且也不在重连中时，需要进行重连;
        if(null == mConnection){
            mMessageQueen.add(msg);
            startConnect();
        }else if(!mConnection.isConnected() && !mConnection.isConnecting()){
            mMessageQueen.add(msg);
            startConnect();
        }else{
            mMessageQueen.add(msg);
            if(mConnection.isConnected()){
                mConnection.selector.wakeup();
            }else{
                //说明正在重连中
            }
        }
    }

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
        if(null != mConnection && !mConnection.isClosed()){
            return;
        }

        index++;
        if(index < tcpArray.length && index >= 0){
            stopConnect(false);
            mConnection = new NioConnection(tcpArray[index].ip,tcpArray[index].port,mMessageQueen, mConnectStatusListener, mConnectReceiveListener);
            mConnectionThread =new Thread(mConnection);
            mConnectionThread.start();
        }else{
            index = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            mMessageQueen.clear();
        }
    }

    private synchronized void stopConnect(boolean isCloseByUser){
        try {

            if(null != mConnection) {
                mConnection.setCloseByUser(isCloseByUser);
                mConnection.close();
            }
            mConnection = null;

            if( null!= mConnectionThread && mConnectionThread.isAlive() ) {
                mConnectionThread.interrupt();
            }
            mConnectionThread =null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class NioConnection implements Runnable {

        private final String TAG = "NioConnection";

        private final int STATE_CLOSE           =   1<<1;//socket关闭
        private final int STATE_CONNECT_START   =   1<<2;//开始连接server
        private final int STATE_CONNECT_SUCCESS =   1<<3;//连接成功
        private final int STATE_CONNECT_FAILED  =   1<<4;//连接失败

        private String ip ="192.168.1.1";
        private int port =9999;

        private Selector selector;
        private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
        private SocketChannel socketChannel;

        private int state= STATE_CLOSE;
        private ConcurrentLinkedQueue<Message> mMessageQueen;
        private IConnectStatusListener mConnectStatusListener;
        private BaseMessageProcessor mMessageProcessor;
        private boolean isClosedByUser = false;

        public NioConnection(String ip, int port, ConcurrentLinkedQueue<Message> queen, IConnectStatusListener mNioConnectionListener, BaseMessageProcessor mConnectReceiveListener) {
            this.ip = ip;
            this.port = port;
            this.mMessageQueen = queen;
            this.mConnectStatusListener = mNioConnectionListener;
            this.mMessageProcessor = mConnectReceiveListener;
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
            if(state != STATE_CLOSE){
                if(null!=socketChannel)
                {
                    try {
                        SelectionKey key = socketChannel.keyFor(selector);
                        if(null != key){
                            key.cancel();
                        }
                        selector.close();
                        socketChannel.socket().close();
                        socketChannel.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
                state = STATE_CLOSE;
            }
        }

    //-------------------------------------------------------------------------------------------

        private boolean finishConnection(SelectionKey key) throws IOException {
            boolean result;
            SocketChannel socketChannel = (SocketChannel) key.channel();
            result= socketChannel.finishConnect();//没有网络的时候也返回true
            if(result) {
                key.interestOps(SelectionKey.OP_READ);
                state=STATE_CONNECT_SUCCESS;
                this.mConnectStatusListener.onConnectionSuccess();
            }
            return result;
        }

        private boolean read(SelectionKey key) {
            boolean readRet = true;
            try{
                SocketChannel socketChannel = (SocketChannel) key.channel();
                readBuffer.clear();
                int numRead;
                numRead = socketChannel.read(readBuffer);
                if (numRead == -1) {
                    key.channel().close();
                    key.cancel();
                    readRet = false;
                }else if(numRead > 0){
                    if(null != mMessageProcessor){
                        mMessageProcessor.onReceive(readBuffer.array(),0,numRead);
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
                readRet = false;
            }

            return readRet;
        }

        private boolean write(SelectionKey key) {
            SocketChannel socketChannel = (SocketChannel) key.channel();
            Message msg = mMessageQueen.poll();
            while (null != msg){
                try {
                    ByteBuffer buf= ByteBuffer.wrap(msg.getPacket());
                    socketChannel.write(buf);
                } catch (IOException e) {
                    e.printStackTrace();
                    try {
                        key.cancel();
                        key.channel().close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    return false;
                }
                msg = mMessageQueen.poll();
            }
            key.interestOps(SelectionKey.OP_READ);
            return true;
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
                selector= SelectorProvider.provider().openSelector();
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);

                InetSocketAddress address=new InetSocketAddress(ip, port);
                socketChannel.connect(address);
                socketChannel.register(selector, SelectionKey.OP_CONNECT);

                boolean isExit = false;
                while(state != STATE_CLOSE && !isExit) {

                    selector.select();
                    Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                    while (selectedKeys.hasNext()) {
                        SelectionKey key =  selectedKeys.next();
                        selectedKeys.remove();

                        if (!key.isValid()) {
                            continue;
                        }

                        if (key.isConnectable()) {

                            finishConnection(key);

                        }else if (key.isReadable()) {

                            boolean ret = read(key);
                            if(!ret){
                                isExit = true;
                                break;
                            }

                        }else if (key.isWritable()) {
                            boolean ret = write(key);
                            if(!ret){
                                isExit = true;
                                break;
                            }
                        }
                    }

                    if(isExit){
                        break;
                    }

                    if(!mMessageQueen.isEmpty()) {
                        SelectionKey key=socketChannel.keyFor(selector);
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
}
