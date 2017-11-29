package com.open.net.data;

/**
 * Created by Administrator on 2017/11/17.
 */

public class TcpAddress {
    public String ip;
    public int     port;

    public TcpAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}