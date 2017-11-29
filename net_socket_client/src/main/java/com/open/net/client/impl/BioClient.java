package com.open.net.client.impl;

import com.open.net.client.structures.Message;
import com.open.net.client.structures.TcpAddress;
import com.open.net.client.listener.BaseMessageProcessor;
import com.open.net.client.listener.IConnectStatusListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   BioClient
 */
public class BioClient {

	private final String TAG="BioClient";

	private TcpAddress[] tcpArray;
	private int index = -1;
	private BaseMessageProcessor mConnectReceiveListener;

	private ConcurrentLinkedQueue<Message> mWriteMessageQueen = new ConcurrentLinkedQueue();
	private Thread mConnectionThread =null;
	private BioConnection mConnection;

	private IConnectStatusListener mConnectStatusListener = null;

	public BioClient(TcpAddress[] tcpArray , BaseMessageProcessor mConnectReceiveListener) {
		this.tcpArray = tcpArray;
		this.mConnectReceiveListener = mConnectReceiveListener;
	}

	public void setConnectAddress(TcpAddress[] tcpArray ){
		this.tcpArray = tcpArray;
	}

	public void setConnectStatusListener(IConnectStatusListener mConnectStatusListener){
		this.mConnectStatusListener = mConnectStatusListener;
	}

	public void sendMessage(Message msg)
	{
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
				mConnection.mWriter.wakeup();
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
		if(null != mConnection && !mConnection.isClosed()){
			return;
		}

		index++;
		if(index < tcpArray.length && index >= 0){
			stopConnect(false);
			mConnection = new BioConnection(tcpArray[index].ip,tcpArray[index].port, mConnectStatusListener, mConnectReceiveListener);
			mConnectionThread =new Thread(mConnection);
			mConnectionThread.start();
		}else{
			index = -1;

			//循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
			mWriteMessageQueen.clear();
		}
	}

	private synchronized void stopConnect(boolean isCloseByUser)
	{
		try {

			if(null != mConnection) {
				mConnection.setCloseByUser(isCloseByUser);
				mConnection.close();
			}
			mConnection= null;

			if( null!= mConnectionThread && mConnectionThread.isAlive() ) {
				mConnectionThread.interrupt();
			}
			mConnectionThread =null;

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private class BioConnection implements Runnable
	{

		private final int STATE_CLOSE			= 1<<1;//socket关闭
		private final int STATE_CONNECT_START	= 1<<2;//开始连接server
		private final int STATE_CONNECT_SUCCESS	= 1<<3;//连接成功
		private final int STATE_CONNECT_FAILED	= 1<<4;//连接失败

		private String ip ="192.168.1.1";
		private int port =9999;
		private int state = STATE_CLOSE;
		private IConnectStatusListener mConnectStatusListener;
		private BaseMessageProcessor mMessageProcessor;
		private boolean isClosedByUser = false;

		private Socket socket=null;
		private OutputStream outStream=null;
		private InputStream inStream=null;
		private WriteRunnable mWriter;
		private Thread writeThread =null;
		private Thread readThread =null;


		public BioConnection(String ip, int port, IConnectStatusListener mConnectionStatusListener, BaseMessageProcessor mConnectReceiveListener) {
			this.ip = ip;
			this.port = port;
			this.mConnectStatusListener = mConnectionStatusListener;
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
			try {
				if(state!=STATE_CLOSE)
				{
					try {
						if(null!=socket)
						{
							socket.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}finally{
						socket=null;
					}

					try {
						if(null!=outStream)
						{
							outStream.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}finally{
						outStream=null;
					}

					try {
						if(null!=inStream)
						{
							inStream.close();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}finally{
						inStream=null;
					}

					try {
						if(null!= mConnectionThread && mConnectionThread.isAlive())
						{
							mConnectionThread.interrupt();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}finally{
						mConnectionThread =null;
					}

					try {
						if(null!= writeThread && writeThread.isAlive())
						{
							writeThread.interrupt();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}finally{
						writeThread =null;
					}

					try {
						if(null!= readThread && readThread.isAlive())
						{
							readThread.interrupt();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}finally{
						readThread =null;
					}

					state=STATE_CLOSE;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		//-------------------------------------------------------------------------------------------
		public boolean write(){
			boolean writeRet = false;
			try{
				Message msg= mWriteMessageQueen.poll();
				while(null != msg) {
					outStream.write(msg.getPacket());
					outStream.flush();
					msg= mWriteMessageQueen.poll();
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

				while((numRead=inStream.read(bodyBytes, 0, maximum_length))>0) {
					if(numRead > 0){
						if(null!= mMessageProcessor) {
							mMessageProcessor.onReceive(bodyBytes,0,numRead);
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

		public void run() {
			try {
                    isClosedByUser = false;
                    state=STATE_CONNECT_START;
                    socket=new Socket();
                    socket.connect(new InetSocketAddress(ip, port), 15*1000);

                    outStream=socket.getOutputStream();
                    inStream=socket.getInputStream();

                    mWriter = new WriteRunnable();
                    writeThread =new Thread(mWriter);
                    readThread =new Thread(new ReadRunnable());
                    writeThread.start();
                    readThread.start();

                    state=STATE_CONNECT_SUCCESS;

			} catch (Exception e) {
				e.printStackTrace();
                state=STATE_CONNECT_FAILED;
			}finally {
				if(!(state == STATE_CONNECT_SUCCESS || isClosedByUser)) {
					if(null != mConnectStatusListener){
						mConnectStatusListener.onConnectionFailed();
					}
				}
			}
		}


		private class WriteRunnable implements Runnable {

			private final Object lock=new Object();

			public void wakeup(){
				synchronized (lock)
				{
					lock.notifyAll();
				}
			}

			public void run() {
				try {
					while(state!=STATE_CLOSE && state== STATE_CONNECT_SUCCESS && null!=outStream ) {

						if(!write()){
							break;
						}

						synchronized (lock) {
							lock.wait();
						}
					}
				}catch (Exception e) {
					e.printStackTrace();
				}

				System.out.println("client close when write");

				if(!isClosedByUser){
					if(null != mConnectStatusListener){
						mConnectStatusListener.onConnectionFailed();
					}
				}
			}
		}

		private class ReadRunnable implements Runnable
		{
			public void run() {

				read();

				System.out.println("client close when read");

				if(!isClosedByUser){
					if(null != mConnectStatusListener){
						mConnectStatusListener.onConnectionFailed();
					}
				}
			}
		}
	}

}
