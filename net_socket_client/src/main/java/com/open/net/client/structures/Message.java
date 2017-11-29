package com.open.net.client.structures;


/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   信息
 */

public final class Message {

	public int src_reuse_type;
	public int dst_resue_type;

	public byte[] data;//共享数组中的数据
	public int capacity;//数组中的容量
	public int block_index;//在块中的索引
	public int offset;//在数组的偏移量
	public int length;//有效长度

}
