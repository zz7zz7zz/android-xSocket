package com.open.net;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

import com.open.net.client.impl.BioClient;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.message.Message;
import com.open.net.client.structures.TcpAddress;

import java.util.LinkedList;

public class BioClientConnectionActivity extends Activity {

	private BioClient mConnection =null;
	private EditText ip,port,sendContent,recContent;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.net_socket_connection);
		initView();
	}
	 
	
	private void initView()
	{
		findViewById(R.id.open).setOnClickListener(listener);
		findViewById(R.id.close).setOnClickListener(listener);
		findViewById(R.id.reconn).setOnClickListener(listener);
		findViewById(R.id.send).setOnClickListener(listener);
		findViewById(R.id.clear).setOnClickListener(listener);
		
		ip=(EditText) findViewById(R.id.ip);
		port=(EditText) findViewById(R.id.port);
		sendContent=(EditText) findViewById(R.id.sendContent);
		recContent=(EditText) findViewById(R.id.recContent);
		
		ip.setText("192.168.123.1");
		port.setText("9999");

		mConnection = new BioClient(new TcpAddress[]{new TcpAddress(ip.getText().toString(), Integer.valueOf(port.getText().toString()))}, mMessageProcessor);
		mConnection.setConnectStatusListener(new IConnectStatusListener() {
			@Override
			public void onConnectionSuccess() {

			}

			@Override
			public void onConnectionFailed() {
				mConnection.connect();//try to connect next ip port
			}
		});
	}
	
	private OnClickListener listener=new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			mConnection.setConnectAddress(new TcpAddress[]{new TcpAddress(ip.getText().toString(), Integer.valueOf(port.getText().toString()))});
			switch(v.getId())
			{
				case R.id.open:
					mConnection.connect();
					break;
					
				case R.id.close:
					mConnection.disconnect();
					break;
					
				case R.id.reconn:
					mConnection.reconnect();
					break;
					
				case R.id.send:
					Message msg=new Message();
					msg.data = sendContent.getText().toString().getBytes();
					msg.length = msg.data.length;
					mConnection.sendMessage(msg);
					sendContent.setText("");
					break;
					
				case R.id.clear:
					recContent.setText("");
					break;
			}
		}
	};

	private BaseMessageProcessor mMessageProcessor =new BaseMessageProcessor() {

		@Override
		public void onReceive(BaseClient mClient, final LinkedList<Message> mQueen) {
			for (int i = 0 ;i< mQueen.size();i++) {
				Message msg = mQueen.get(i);
				final String s = new String(msg.data,0,msg.length);
				runOnUiThread(new Runnable() {
					public void run() {

						recContent.getText().append(s).append("\r\n");
					}
				});
			}
		}
	};

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		mConnection.disconnect();
	}
}
