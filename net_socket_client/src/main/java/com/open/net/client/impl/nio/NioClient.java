package com.open.net.client.impl.nio;

import com.open.net.client.impl.nio.processor.ConnectProcessor;
import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.TcpAddress;
import com.open.net.client.structures.message.Message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   NioClient
 */

public class NioClient extends BaseClient{

    private final String TAG="NioClient";

    private TcpAddress[] tcpArray   = null;
    private int          index      = -1;

    private ConnectProcessor mConnectProcessor = null;
    private Thread mConnectProcessorThread = null;

    private BaseMessageProcessor    mMessageProcessor = null;
    private IConnectStatusListener  mConnectStatusListener = null;

    public NioClient(TcpAddress[] tcpArray, BaseMessageProcessor mMessageProcessor, IConnectStatusListener mConnectStatusListener) {
        this.tcpArray = tcpArray;
        this.mMessageProcessor = mMessageProcessor;
        this.mConnectStatusListener = mConnectStatusListener;
    }

    //-------------------------------------------------------------------------------------------
    public void setConnectAddress(TcpAddress[] tcpArray ){
        this.tcpArray = tcpArray;
    }

    //-------------------------------------------------------------------------------------------
    public void sendMessage(Message msg){
        //1.没有连接,需要进行重连
        //2.在连接不成功，并且也不在重连中时，需要进行重连;
        if(null == mConnectProcessor){
            addWriteMessage(msg);
            startConnect();
        }else if(!mConnectProcessor.isConnected() && !mConnectProcessor.isConnecting()){
            addWriteMessage(msg);
            startConnect();
        }else{
            addWriteMessage(msg);
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
            mConnectProcessor = new ConnectProcessor(this, tcpArray[index].ip,tcpArray[index].port, mConnectStatusListener, mMessageProcessor);
            mConnectProcessorThread =new Thread(mConnectProcessor);
            mConnectProcessorThread.start();
        }else{
            index = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            super.clear();
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

    //-------------------------------------------------------------------------------------------
    private SocketChannel mSocketChannel;
    private Selector   mSelector;
    private ByteBuffer mReadByteBuffer  = ByteBuffer.allocate(256*1024);
    private ByteBuffer mWriteByteBuffer = ByteBuffer.allocate(256*1024);

    public void init(SocketChannel socketChannel) throws IOException {

    }
    @Override
    public void close() {
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
    }

    public boolean read() {
        boolean readRet = true;
        try{
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
                        this.mMessageProcessor.onReceive(this, mReadByteBuffer.array(), 0 , mReadByteBuffer.remaining());
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
                this.mMessageProcessor.onReceive(this, mReadByteBuffer.array(), 0 , mReadByteBuffer.remaining());
            }
            mReadByteBuffer.clear();

        }catch (Exception e){
            e.printStackTrace();
            readRet = false;
        }

        mMessageProcessor.onProcessReceivedMessage(this);

        return readRet;
    }

    public boolean write() {
        boolean writeRet = true;
        try {
            Message msg = pollWriteMessage();
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

                        int writtenLength   = mSocketChannel.write(mWriteByteBuffer);//客户端关闭连接后，此处将抛出异常
                        writtenTotalLength  = writtenLength;

                        while(writtenLength > 0 && mWriteByteBuffer.hasRemaining()){
                            writtenLength       = mSocketChannel.write(mWriteByteBuffer);
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

                    int writtenLength      = mSocketChannel.write(mWriteByteBuffer);//客户端关闭连接后，此处将抛出异常
                    int writtenTotalLength = writtenLength;

                    while(writtenLength > 0 && mWriteByteBuffer.hasRemaining()){
                        writtenLength       = mSocketChannel.write(mWriteByteBuffer);
                        writtenTotalLength += writtenLength;
                    }
                    mWriteByteBuffer.clear();
                }

                removeWriteMessage(msg);
                msg = pollWriteMessage();
            }

        } catch (IOException e) {
            e.printStackTrace();
            writeRet = false;
        }
        return writeRet;
    }

    //-------------------------------------------------------------------------------------------


}
