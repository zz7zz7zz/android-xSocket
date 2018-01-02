package com.open.net.client.impl.tcp.bio;

import com.open.net.client.GClient;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.IConnectListener;
import com.open.net.client.structures.TcpAddress;
import com.open.net.client.structures.message.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   BioClient
 */
public class BioClient extends BaseClient{

	static {
		GClient.init();
	}

	//-------------------------------------------------------------------------------------------
	private BioConnector mConnector;

	public BioClient(BaseMessageProcessor mMessageProcessor, IConnectListener mConnectListener) {
		super(mMessageProcessor);
		mConnector = new BioConnector(this,mConnectListener);
	}

	//-------------------------------------------------------------------------------------------
	public void setConnectAddress(TcpAddress[] tcpArray ){
		mConnector.setConnectAddress(tcpArray);
	}

	public void setConnectTimeout(long connect_timeout ){
		mConnector.setConnectTimeout(connect_timeout);
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

	public boolean isConnected(){
		return mConnector.isConnected();
	}

	//-------------------------------------------------------------------------------------------
	private OutputStream mOutputStream =null;
	private InputStream  mInputStream =null;

    public void init(OutputStream mOutputStream , InputStream mInputStream) throws IOException{
        this.mOutputStream 	= mOutputStream;
        this.mInputStream 	= mInputStream;
    }

	@Override
	public void onCheckConnect() {
		mConnector.checkConnect();
	}

	public void onClose(){
		mOutputStream = null;
		mInputStream = null;
	}

	public boolean onRead(){
		boolean readRet = false;
		try {
			int maximum_length = 64*1024;
			byte[] bodyBytes = new byte[maximum_length];
			int numRead;

			while((numRead= mInputStream.read(bodyBytes, 0, maximum_length))>0) {
				if(numRead > 0){
					if(null != mMessageProcessor) {
						mMessageProcessor.onReceiveData(this, bodyBytes,0,numRead);
						mMessageProcessor.onReceiveDataCompleted(this);
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();//客户端主动socket.stopConnect()会调用这里 java.net.SocketException: Socket closed
			readRet = false;
		}catch (IOException e1) {
			e1.printStackTrace();
			readRet = false;
		}catch (Exception e2) {
			e2.printStackTrace();
			readRet = false;
		}

		if(null != mMessageProcessor){
			mMessageProcessor.onReceiveDataCompleted(this);
		}

		//退出客户端的时候需要把要该客户端要写出去的数据清空
		if(!readRet){
			Message msg = pollWriteMessage();
			while (null != msg) {
				removeWriteMessage(msg);
				msg= pollWriteMessage();
			}
		}
		return false;
	}

	public boolean onWrite(){
		boolean writeRet = true;
		Message msg= pollWriteMessage();
		try{
			while(null != msg) {
				mOutputStream.write(msg.data,msg.offset,msg.length);
				mOutputStream.flush();
				removeWriteMessage(msg);
				msg= pollWriteMessage();
			}
		} catch (SocketException e) {
			e.printStackTrace();//客户端主动socket.stopConnect()会调用这里 java.net.SocketException: Socket closed
			writeRet = false;
		}catch (IOException e1) {
			e1.printStackTrace();//发送的时候出现异常，说明socket被关闭了(服务器关闭)java.net.SocketException: sendto failed: EPIPE (Broken pipe)
			writeRet = false;
		}catch (Exception e2) {
			e2.printStackTrace();
			writeRet = false;
		}

		//退出客户端的时候需要把该客户端要写出去的数据清空
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
