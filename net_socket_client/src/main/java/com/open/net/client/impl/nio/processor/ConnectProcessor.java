package com.open.net.client.impl.nio.processor;

import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.message.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
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

    private Selector mSelector;
    private ByteBuffer mReadByteBuffer  = ByteBuffer.allocate(256*1024);
    private ByteBuffer mWriteByteBuffer = ByteBuffer.allocate(256*1024);
    private SocketChannel mSocketChannel;

    private int state= STATE_CLOSE;
    private IConnectStatusListener mConnectStatusListener;
    private BaseMessageProcessor mMessageProcessor;
    private boolean isClosedByUser = false;

    private BaseClient mClient;

    public ConnectProcessor(BaseClient mClient,String ip, int port, IConnectStatusListener mNioConnectionListener, BaseMessageProcessor mMessageProcessor) {
        this.mClient = mClient;
        this.ip = ip;
        this.port = port;
        this.mConnectStatusListener = mNioConnectionListener;
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
                        this.mMessageProcessor.onReceive(mClient, mReadByteBuffer.array(), 0 , mReadByteBuffer.remaining());
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
                this.mMessageProcessor.onReceive(mClient, mReadByteBuffer.array(), 0 , mReadByteBuffer.remaining());
            }
            mReadByteBuffer.clear();

        }catch (Exception e){
            e.printStackTrace();
            readRet = false;
        }

        mMessageProcessor.onProcessReceivedMessage(mClient);

        return readRet;
    }

    private boolean write(SelectionKey key) {
        boolean writeRet = true;
        try {
            SocketChannel socketChannel = (SocketChannel) key.channel();
            Message msg = mClient.pollWriteMessage();
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

                mClient.removeWriteMessage(msg);
                msg = mClient.pollWriteMessage();
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