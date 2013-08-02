package com.boyaa.push.lib.service;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.Context;
import android.util.Log;

import com.boyaa.push.lib.util.NetworkUtil;

/**
 * 
 * @author Administrator
 *
 */
public class Client {
	
	private final int STATE_OPEN=1;//socket打开
	private final int STATE_CLOSE=1<<1;//socket关闭
	private final int STATE_CONNECT_START=1<<2;//开始连接server
	private final int STATE_CONNECT_SUCCESS=1<<3;//连接成功
	private final int STATE_CONNECT_FAILED=1<<4;//连接失败
	private final int STATE_CONNECT_WAIT=1<<5;//等待连接
	
	private String IP="192.168.1.100";
	private int PORT=60000;
	
	private int state=STATE_CONNECT_START;
	
	private Socket socket=null;
	private OutputStream outStream=null;
	private InputStream inStream=null;
	
	private Thread conn=null;
	private Thread send=null;
	private Thread rec=null;
	
	private Context context;
	private ISocketResponse respListener;
	private LinkedBlockingQueue<Packet> requestQueen=new LinkedBlockingQueue<Packet>();
	private final Object lock=new Object();
	private final String TAG="Client";
	
	public int send(Packet in)
	{
		requestQueen.add(in);
		synchronized (lock) 
		{
			lock.notifyAll();
		}
		return in.getId();
	}
	
	public void cancel(int reqId)
	{
		 Iterator<Packet> mIterator=requestQueen.iterator();
		 while (mIterator.hasNext()) 
		 {
			 Packet packet=mIterator.next();
			 if(packet.getId()==reqId)
			 {
				 mIterator.remove();
			 }
		}
	}
	
	public Client(Context context,ISocketResponse respListener)
	{
		this.context=context;
		this.respListener=respListener;
	}
	
	public boolean isNeedConn()
	{
		return !((state==STATE_CONNECT_SUCCESS)&&(null!=send&&send.isAlive())&&(null!=rec&&rec.isAlive()));
	}
	
	public void open()
	{
		reconn();
	}
	
	public void open(String host,int port)
	{
		this.IP=host;
		this.PORT=port;
		reconn();
	}
	
	private long lastConnTime=0;
	public synchronized void reconn()
	{
		if(System.currentTimeMillis()-lastConnTime<2000)
		{
			return;
		}
		lastConnTime=System.currentTimeMillis();
		
		close();
		state=STATE_OPEN;
		conn=new Thread(new Conn());
		conn.start();
	}
	
	public synchronized void close()
	{
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
					if(null!=conn&&conn.isAlive())
					{
						conn.interrupt();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}finally{
					conn=null;
				}
				
				try {
					if(null!=send&&send.isAlive())
					{
						send.interrupt();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}finally{
					send=null;
				}
				
				try {
					if(null!=rec&&rec.isAlive())
					{
						rec.interrupt();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}finally{
					rec=null;
				}
				
				state=STATE_CLOSE;
			}
			requestQueen.clear();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private class Conn implements Runnable
	{
		public void run() {
Log.v(TAG,"Conn :Start");
			try {
					while(state!=STATE_CLOSE)
					{
						try {
							state=STATE_CONNECT_START;
							socket=new Socket();
							socket.connect(new InetSocketAddress(IP, PORT), 15*1000);
							state=STATE_CONNECT_SUCCESS;
						} catch (Exception e) {
							e.printStackTrace();
							state=STATE_CONNECT_FAILED;
						}
						
						if(state==STATE_CONNECT_SUCCESS)
						{
							try {
								outStream=socket.getOutputStream();
								inStream=socket.getInputStream();
							} catch (IOException e) {
								e.printStackTrace();
							}
							
							send=new Thread(new Send());
							rec=new Thread(new Rec());
							send.start();
							rec.start();
							break;
						}
						else
						{
							state=STATE_CONNECT_WAIT;
							//如果有网络没有连接上，则定时取连接，没有网络则直接退出
							if(NetworkUtil.isNetworkAvailable(context))
							{
								try {
										Thread.sleep(15*1000);
								} catch (InterruptedException e) {
									e.printStackTrace();
									break;
								}
							}
							else
							{
								break;
							}
						}
					}
			} catch (Exception e) {
				e.printStackTrace();
			}

Log.v(TAG,"Conn :End");
		}
	}
	
	private class Send implements Runnable
	{
		public void run() {
Log.v(TAG,"Send :Start");
			try {
					while(state!=STATE_CLOSE&&state==STATE_CONNECT_SUCCESS&&null!=outStream)
					{
								Packet item;
								while(null!=(item=requestQueen.poll()))
								{
									outStream.write(item.getPacket());
									outStream.flush();
									item=null;
								}
								
Log.v(TAG,"Send :woken up AAAAAAAAA");
								synchronized (lock)
								{
									lock.wait();
								}
Log.v(TAG,"Send :woken up BBBBBBBBBB");
					}
			}catch(SocketException e1) 
			{
				e1.printStackTrace();//发送的时候出现异常，说明socket被关闭了(服务器关闭)java.net.SocketException: sendto failed: EPIPE (Broken pipe)
				reconn();
			} 
			catch (Exception e) {
Log.v(TAG,"Send ::Exception");
				e.printStackTrace();
			}
			
Log.v(TAG,"Send ::End");
		}
	}
	
	private class Rec implements Runnable
	{
		public void run() {
Log.v(TAG,"Rec :Start");
			
			try {
					while(state!=STATE_CLOSE&&state==STATE_CONNECT_SUCCESS&&null!=inStream)
					{
Log.v(TAG,"Rec :---------");
							byte[] bodyBytes=new byte[5];
							int offset=0;
							int length=5;
							int read=0;
							
							while((read=inStream.read(bodyBytes, offset, length))>0)
							{
								if(length-read==0)
								{
									if(null!=respListener)
									{
										respListener.onSocketResponse(new String(bodyBytes));
									}
									
									offset=0;
									length=5;
									read=0;
									continue;
								}
								offset+=read;
								length=5-offset;
							}
							
							reconn();//走到这一步，说明服务器socket断了
							break;
					}
			}
			catch(SocketException e1) 
			{
				e1.printStackTrace();//客户端主动socket.close()会调用这里 java.net.SocketException: Socket closed
			} 
			catch (Exception e2) {
Log.v(TAG,"Rec :Exception");
				e2.printStackTrace();
			}
			
Log.v(TAG,"Rec :End");
		}
	}
}
