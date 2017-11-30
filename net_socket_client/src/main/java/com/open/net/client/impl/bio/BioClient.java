package com.open.net.client.impl.bio;

import com.open.net.client.impl.bio.processor.SocketCrwProcessor;
import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.TcpAddress;
import com.open.net.client.structures.message.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   BioClient
 */
public class BioClient extends BaseClient{

	private final String TAG = "BioClient";

	private TcpAddress[] 	tcpArray 	= null;
	private int 			index 		= -1;

	private SocketCrwProcessor mConnectProcessor;
	private Thread 				mConnectProcessorThread =null;

	private BaseMessageProcessor 	mMessageProcessor;
	private IConnectStatusListener 	mConnectStatusListener = null;

	public BioClient(TcpAddress[] tcpArray , BaseMessageProcessor mMessageProcessor, IConnectStatusListener mConnectStatusListener) {
		this.tcpArray = tcpArray;
		this.mMessageProcessor = mMessageProcessor;
		this.mConnectStatusListener = mConnectStatusListener;
	}

	public void setConnectAddress(TcpAddress[] tcpArray ){
		this.tcpArray = tcpArray;
	}

	//-------------------------------------------------------------------------------------------
	public void sendMessage(Message msg)
	{
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

    public synchronized void connect()
    {
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

	private synchronized void startConnect()
	{
		//已经在连接中就不再进行连接
		if(null != mConnectProcessor && !mConnectProcessor.isClosed()){
			return;
		}

		index++;
		if(index < tcpArray.length && index >= 0){
			stopConnect(false);
			mConnectProcessor = new SocketCrwProcessor(this,tcpArray[index].ip,tcpArray[index].port, mConnectStatusListener);
			mConnectProcessorThread =new Thread(mConnectProcessor);
			mConnectProcessorThread.start();
		}else{
			index = -1;

			//循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
			super.clear();
		}
	}

	private synchronized void stopConnect(boolean isCloseByUser)
	{
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
	private Socket mSocket =null;
	private OutputStream mOutputStream =null;
	private InputStream mInputStream =null;

	public void init(Socket socket) throws IOException{
		mSocket    		= socket;
		mOutputStream 	= socket.getOutputStream();
		mInputStream 	= socket.getInputStream();
	}

	public void close(){
		try {
				try {
					if(null!= mSocket)
					{
						mSocket.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}finally{
					mSocket =null;
				}

				try {
					if(null!= mOutputStream)
					{
						mOutputStream.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}finally{
					mOutputStream =null;
				}

				try {
					if(null!= mInputStream)
					{
						mInputStream.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}finally{
					mInputStream =null;
				}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean write(){
		boolean writeRet = false;
		try{
			Message msg= pollWriteMessage();
			while(null != msg) {
				mOutputStream.write(msg.data,0,msg.length);
				mOutputStream.flush();
				removeWriteMessage(msg);
				msg= pollWriteMessage();
			}
			writeRet = true;
		} catch (SocketException e) {
			e.printStackTrace();//客户端主动socket.stopConnect()会调用这里 java.net.SocketException: Socket closed
		}catch (IOException e1) {
			e1.printStackTrace();//发送的时候出现异常，说明socket被关闭了(服务器关闭)java.net.SocketException: sendto failed: EPIPE (Broken pipe)
		}catch (Exception e2) {
			e2.printStackTrace();
		}
		return writeRet;
	}

	public boolean read(){
		try {
			int maximum_length = 8192;
			byte[] bodyBytes=new byte[maximum_length];
			int numRead;

			while((numRead= mInputStream.read(bodyBytes, 0, maximum_length))>0) {
				if(numRead > 0){
					if(null!= mMessageProcessor) {
						mMessageProcessor.onReceive(this, bodyBytes,0,numRead);
						mMessageProcessor.onProcessReceivedMessage(this);
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();//客户端主动socket.stopConnect()会调用这里 java.net.SocketException: Socket closed
		}catch (IOException e1) {
			e1.printStackTrace();
		}catch (Exception e2) {
			e2.printStackTrace();
		}
		return false;
	}

}
