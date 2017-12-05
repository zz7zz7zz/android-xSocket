package com.open.test.net;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;


public class NetMainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//去掉标题栏
        setContentView(R.layout.net_main);
        initView();
    }

    private void initView(){

        findViewById(R.id.net_tcp_bio).setOnClickListener(clickListener);
        findViewById(R.id.net_tcp_nio).setOnClickListener(clickListener);

        findViewById(R.id.net_ucp_bio).setOnClickListener(clickListener);
        findViewById(R.id.net_ucp_nio).setOnClickListener(clickListener);
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()){


                case R.id.net_tcp_bio:
                    startActivity(new Intent(getApplicationContext(), TcpBioClientConnectionActivity.class));
                    break;

                case R.id.net_tcp_nio:
                    startActivity(new Intent(getApplicationContext(),TcpNioClientConnectionActivity.class));
                    break;

                case R.id.net_ucp_bio:
                    startActivity(new Intent(getApplicationContext(),UdpBioClientConnectionActivity.class));
                    break;

                case R.id.net_ucp_nio:
                    startActivity(new Intent(getApplicationContext(),UdpNioClientConnectionActivity.class));
                    break;
            }
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN)
        {
            android.os.Process.killProcess(android.os.Process.myPid());
//			System.exit(0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
