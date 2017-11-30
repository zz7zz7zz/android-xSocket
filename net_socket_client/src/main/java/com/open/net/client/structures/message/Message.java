package com.open.net.client.structures.message;


/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   信息
 */

public final class Message {

	private static long G_MESSAGE_ID = 0;
	public long msgId;

	public int src_reuse_type;
	public int dst_reuse_type;

	public byte[] data;//共享数组中的数据
	public int capacity;//数组中的容量
	public int block_index;//在块中的索引
	public int offset;//在数组的偏移量
	public int length;//有效长度

	public Message() {
		reset();
	}

	public void reset() {
		++G_MESSAGE_ID;

		msgId = G_MESSAGE_ID;
		src_reuse_type = 0;
		dst_reuse_type = 0;

		data = null;
		capacity = 0;
		block_index = 0;
		offset = 0;
		length = 0;
	}
}
