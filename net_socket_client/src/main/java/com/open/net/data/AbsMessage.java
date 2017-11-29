package com.open.net.data;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 
 * @author Administrator
 *
 */
public abstract class AbsMessage {

	protected abstract byte[] getPacket();

	public boolean write(OutputStream outStream){
		try {
			outStream.write(getPacket());
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean write(SocketChannel socketChannel){
		ByteBuffer buf= ByteBuffer.wrap(getPacket());
		try {
			socketChannel.write(buf);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
}
