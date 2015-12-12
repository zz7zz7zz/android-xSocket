package com.boyaa.chat.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

import com.boyaa.chat.R;
import com.boyaa.push.lib.service.Client;
import com.boyaa.push.lib.service.ISocketResponse;
import com.boyaa.push.lib.service.Packet;

public class MainActivity extends Activity {

	private Client user=null;
	private EditText ip,port,sendContent,recContent;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		initView();
		user=new Client(this.getApplicationContext(),socketListener);
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
		
		ip.setText("192.168.1.100");
		port.setText("60000");
	}
	
	private ISocketResponse socketListener=new ISocketResponse() {
		
		@Override
		public void onSocketResponse(final String txt) {
			runOnUiThread(new Runnable() {
				public void run() {
					recContent.getText().append(txt).append("\r\n");
				}
			});
		}
	};
	
	private OnClickListener listener=new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			switch(v.getId())
			{
				case R.id.open:
//					user.open();
					user.open(ip.getText().toString(), Integer.valueOf(port.getText().toString()));
					break;
					
				case R.id.close:
					user.close();
					break;
					
				case R.id.reconn:
					user.reconn();
					break;
					
				case R.id.send:
					Packet packet=new Packet();
					packet.pack(sendContent.getText().toString());
					user.send(packet);
					sendContent.setText("");
					break;
					
				case R.id.clear:
					recContent.setText("");
					break;
			}
		}
	};
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(keyCode==KeyEvent.KEYCODE_BACK)
		{
			android.os.Process.killProcess(android.os.Process.myPid());
		}
		return super.onKeyDown(keyCode, event);
	}
	
}
