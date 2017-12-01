package com.open.net.client.structures;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   Server地址
 */

public class UdpAddress {
    public String  ip;
    public int     port;

    public UdpAddress(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}