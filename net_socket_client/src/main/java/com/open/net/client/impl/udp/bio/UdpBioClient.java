package com.open.net.client.impl.udp.bio;

import com.open.net.client.GClient;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.IConnectListener;
import com.open.net.client.structures.UdpAddress;
import com.open.net.client.structures.message.Message;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * author       :   long
 * created on   :   2017/12/4
 * description  :   UDP 客户端
 */

public class UdpBioClient extends BaseClient{

    static {
        GClient.init();
    }

    //-------------------------------------------------------------------------------------------
    private UdpBioConnector mConnector;

    public UdpBioClient(BaseMessageProcessor mMessageProcessor, IConnectListener mConnectListener) {
        super(mMessageProcessor);
        mConnector = new UdpBioConnector(this,mConnectListener);
    }
    //-------------------------------------------------------------------------------------------
    public void setConnectAddress(UdpAddress[] tcpArray ){
        mConnector.setConnectAddress(tcpArray);
    }

    public void connect(){
        mConnector.connect();
    }

    public void disconnect(){
        mConnector.disconnect();
    }

    public void reconnect(){
        mConnector.reconnect();
    }

    //-------------------------------------------------------------------------------------------
    private DatagramSocket mSocket;
    private DatagramPacket mWriteDatagramPacket ;
    private DatagramPacket mReadDatagramPacket ;
    public byte[] mWriteBuff  = new byte[65500];
    public byte[] mReadBuff   = new byte[65500];

    public void init(DatagramSocket mSocket, DatagramPacket mWriteDatagramPacket, DatagramPacket mReadDatagramPacket){
        this.mSocket = mSocket;
        this.mWriteDatagramPacket = mWriteDatagramPacket;
        this.mReadDatagramPacket = mReadDatagramPacket;
    }

    @Override
    public void onCheckConnect() {
        mConnector.checkConnect();
    }

    @Override
    public void onClose() {
        mSocket = null;
    }

    @Override
    public boolean onRead() {
        try {
            while(true){
                mSocket.receive(mReadDatagramPacket);
                if(null!= mMessageProcessor) {
                    mMessageProcessor.onReceiveData(this, mReadDatagramPacket.getData(),mReadDatagramPacket.getOffset(),mReadDatagramPacket.getLength());
                    mMessageProcessor.onReceiveDataCompleted(this);
                }
                mReadDatagramPacket.setLength(mReadDatagramPacket.getData().length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(null!= mMessageProcessor) {
            mMessageProcessor.onReceiveDataCompleted(this);
        }

        return true;
    }

    @Override
    public boolean onWrite() {
        boolean writeRet = true;
        Message msg= pollWriteMessage();
        try{
            while(null != msg) {
                mWriteDatagramPacket.setData(msg.data,msg.offset,msg.length);
                mSocket.send(mWriteDatagramPacket);
                removeWriteMessage(msg);
                msg= pollWriteMessage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //退出客户端的时候需要把要写给该客户端的数据清空
        if(!writeRet){
            if(null != msg){
                removeWriteMessage(msg);
            }
            msg= pollWriteMessage();
            while (null != msg) {
                removeWriteMessage(msg);
                msg= pollWriteMessage();
            }
        }

        return writeRet;
    }
}
