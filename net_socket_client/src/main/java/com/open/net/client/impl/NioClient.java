package com.open.net.client.impl;

import com.open.net.client.structures.Message;
import com.open.net.client.structures.TcpAddress;
import com.open.net.client.listener.IMessageProcessor;
import com.open.net.client.listener.IConnectStatusListener;

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
 * author       :   long
 * created on   :   2017/11/30
 * description  :   NioClient
 */

public class NioClient{

    private final String TAG="NioClient";

    private TcpAddress[] tcpArray;
    private int index = -1;
    private IMessageProcessor mConnectReceiveListener;

    //无锁队列
    private ConcurrentLinkedQueue<Message> mWriteMessageQueen = new ConcurrentLinkedQueue();
    private Thread mConnectionThread;
    private NioConnection mConnection;

    private IConnectStatusListener mConnectStatusListener = null;

    public NioClient(TcpAddress[] tcpArray, IMessageProcessor mConnectionReceiveListener) {
        this.tcpArray = tcpArray;
        this.mConnectReceiveListener = mConnectionReceiveListener;
    }

    public void setConnectAddress(TcpAddress[] tcpArray ){
        this.tcpArray = tcpArray;
    }

    public void setConnectStatusListener(IConnectStatusListener mConnectStatusListener){
        this.mConnectStatusListener = mConnectStatusListener;
    }

    public void sendMessage(Message msg){
        //1.没有连接,需要进行重连
        //2.在连接不成功，并且也不在重连中时，需要进行重连;
        if(null == mConnection){
            mWriteMessageQueen.add(msg);
            startConnect();
        }else if(!mConnection.isConnected() && !mConnection.isConnecting()){
            mWriteMessageQueen.add(msg);
            startConnect();
        }else{
            mWriteMessageQueen.add(msg);
            if(mConnection.isConnected()){
                mConnection.mSelector.wakeup();
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
            mConnection = new NioConnection(tcpArray[index].ip,tcpArray[index].port, mWriteMessageQueen, mConnectStatusListener, mConnectReceiveListener);
            mConnectionThread =new Thread(mConnection);
            mConnectionThread.start();
        }else{
            index = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            mWriteMessageQueen.clear();
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

        private Selector mSelector;
        private ByteBuffer mReadByteBuffer  = ByteBuffer.allocate(256*1024);
        private ByteBuffer mWriteByteBuffer = ByteBuffer.allocate(256*1024);
        private SocketChannel mSocketChannel;

        private int state= STATE_CLOSE;
        private ConcurrentLinkedQueue<Message> mMessageQueen;
        private IConnectStatusListener mConnectStatusListener;
        private IMessageProcessor mMessageProcessor;
        private boolean isClosedByUser = false;

        public NioConnection(String ip, int port, ConcurrentLinkedQueue<Message> queen, IConnectStatusListener mNioConnectionListener, IMessageProcessor mConnectReceiveListener) {
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
                if(null!= mSocketChannel)
                {
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
                if(null != mConnectStatusListener){
                    mConnectStatusListener.onConnectionSuccess();
                }
            }
            return result;
        }

        private boolean read(SelectionKey key) {
            boolean readRet = true;
            try{
                SocketChannel mSocketChannel = (SocketChannel) key.channel();
                mReadByteBuffer.clear();
                int readTotalLength = 0;
                int readReceiveLength = 0;
                while (true){
                    int readLength = mSocketChannel.read(mReadByteBuffer);//客户端关闭连接后，此处将抛出异常/或者返回-1
                    if(readLength == -1){
                        readRet = false;
                        break;
                    }
                    readReceiveLength += readLength;
                    //如果一次性读满了，则先回调一次，然后接着读剩下的，目的是为了一次性读完单个通道的数据
                    if(readReceiveLength == mReadByteBuffer.capacity()){
                        mReadByteBuffer.flip();
                        if(mReadByteBuffer.remaining() > 0){
                            this.mMessageProcessor.onReceive(mReadByteBuffer.array(), 0 , mReadByteBuffer.remaining());
                        }
                        mReadByteBuffer.clear();
                        readReceiveLength = 0;
                    }

                    if(readLength > 0){
                        readTotalLength += readLength;
                    }else {
                        break;
                    }
                }

                mReadByteBuffer.flip();
                if(mReadByteBuffer.remaining() > 0){
                    this.mMessageProcessor.onReceive(mReadByteBuffer.array(), 0 , mReadByteBuffer.remaining());
                }
                mReadByteBuffer.clear();

            }catch (Exception e){
                e.printStackTrace();
                readRet = false;
            }

            return readRet;
        }

        private boolean write(SelectionKey key) {
            boolean writeRet = true;
            try {
                SocketChannel socketChannel = (SocketChannel) key.channel();
                Message msg = mMessageQueen.poll();
                while (null != msg){
                    //如果消息块的大小超过缓存的最大值，则需要分段写入后才丢弃消息，不能在数据未完全写完的情况下将消息丢弃;avoid BufferOverflowException
                    if(mWriteByteBuffer.capacity() < msg.length){

                        int offset = 0;
                        int leftLength = msg.length;
                        int writtenTotalLength;

                        while(true){

                            int putLength = leftLength > mWriteByteBuffer.capacity() ? mWriteByteBuffer.capacity() : leftLength;
                            mWriteByteBuffer.put(msg.data,offset,putLength);
                            mWriteByteBuffer.flip();
                            offset      += putLength;
                            leftLength  -= putLength;

                            int writtenLength   = socketChannel.write(mWriteByteBuffer);//客户端关闭连接后，此处将抛出异常
                            writtenTotalLength  = writtenLength;

                            while(writtenLength > 0 && mWriteByteBuffer.hasRemaining()){
                                writtenLength       = socketChannel.write(mWriteByteBuffer);
                                writtenTotalLength += writtenLength;
                            }
                            mWriteByteBuffer.clear();

                            if(leftLength <=0){
                                break;
                            }
                        }
                    }else{
                        mWriteByteBuffer.put(msg.data,0,msg.length);
                        mWriteByteBuffer.flip();

                        int writtenLength      = socketChannel.write(mWriteByteBuffer);//客户端关闭连接后，此处将抛出异常
                        int writtenTotalLength = writtenLength;

                        while(writtenLength > 0 && mWriteByteBuffer.hasRemaining()){
                            writtenLength       = socketChannel.write(mWriteByteBuffer);
                            writtenTotalLength += writtenLength;
                        }
                        mWriteByteBuffer.clear();
                    }

                    msg = mMessageQueen.poll();
                }

            } catch (IOException e) {
                e.printStackTrace();
                writeRet = false;
            }
            key.interestOps(SelectionKey.OP_READ);
            return writeRet;
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
                mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT);

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

                            boolean ret = read(key);
                            if(!ret){
                                isExit = true;
                                key.cancel();
                                key.channel().close();
                                break;
                            }

                        }else if (key.isWritable()) {
                            boolean ret = write(key);
                            if(!ret){
                                isExit = true;
                                key.cancel();
                                key.channel().close();
                                break;
                            }
                        }
                    }

                    if(isExit){
                        break;
                    }

                    if(!mMessageQueen.isEmpty()) {
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
}
