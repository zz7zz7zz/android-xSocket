package com.open.net.client.structures.message;

import com.open.net.client.structures.pools.MessagePool;

import java.util.LinkedList;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   消息缓存，防止内存碎片
 */

public final class MessageBuffer {

    private static final int KB = 1024;
    private static final int MB = 1024*KB;

    //块大小
    private static int capacity_small  = 8   * KB;
    private static int capacity_middle = 256 * KB;
    private static int capacity_large  = 1   * MB;

    //块数量
    private static int block_size_small  = 5;
    private static int block_size_middle = 2;
    private static int block_size_large  = 0;

    //buffer依赖的数组
    private byte[] buffer_small  ;
    private byte[] buffer_middle ;
    private byte[] buffer_large  ;

    //buffer可用性
    private MessageBufferTracker buffer_small_tracker;
    private MessageBufferTracker buffer_middle_tracker;
    private MessageBufferTracker buffer_large_tracker;

    //临时可缓存对象
    private final int MAX_QUEEN_SIZE = 8;
    private LinkedList<byte[]> mLimitQueen = new LinkedList<>();


    public static void init (int buffer_small_capacity ,int buffer_middle_capacity, int buffer_large_capacity,
                             int buffer_small_block_size, int buffer_middle_block_size, int buffer_large_block_size) {

        capacity_small     = buffer_small_capacity;
        capacity_middle    = buffer_middle_capacity;
        capacity_large =    buffer_large_capacity;

        block_size_small   = buffer_small_block_size;
        block_size_middle  = buffer_middle_block_size;
        block_size_large   = buffer_large_block_size;

    }

    public MessageBuffer() {
        buffer_small    = new byte[block_size_small  * capacity_small];
        buffer_middle   = new byte[block_size_middle * capacity_middle];
        buffer_large    = new byte[block_size_large  * capacity_large];

        buffer_small_tracker    = new MessageBufferTracker(block_size_small);
        buffer_middle_tracker   = new MessageBufferTracker(block_size_middle);
        buffer_large_tracker    = new MessageBufferTracker(block_size_large);
    }

    public Message build(byte[] src,int offset, int length){
        Message msg = build(length);
        System.arraycopy(src,offset,msg.data,0,length);
        msg.length = length;
        return msg;
    }

    private Message build(int length){
        Message ret = MessagePool.get();

        if(length <= capacity_small){
            ret.src_reuse_type = 1;
        }else if(length <= capacity_middle){
            ret.src_reuse_type = 2;
        }else if(length <= capacity_large){
            ret.src_reuse_type = 3;
        }else{
            ret.src_reuse_type = 4;
        }

        if(length <= capacity_small){
            int index = buffer_small_tracker.get();
            if(index != -1){
                ret.data = buffer_small;
                ret.capacity = capacity_small;
                ret.block_index   = index;
                ret.offset   = capacity_small * index;
                ret.length   = 0;
                ret.dst_reuse_type = 1;
                return ret;
            }
        }

        if(length <= capacity_middle){
            int index = buffer_middle_tracker.get();
            if(index != -1){
                ret.data = buffer_middle;
                ret.capacity = capacity_middle;
                ret.block_index   = index;
                ret.offset   = capacity_middle * index;
                ret.length   = 0;
                ret.dst_reuse_type = 2;
                return ret;
            }
        }

        if(length <= capacity_large){
            int index = buffer_large_tracker.get();
            if(index != -1){
                ret.data = buffer_large;
                ret.capacity = capacity_large;
                ret.block_index   = index;
                ret.offset   = capacity_large * index;
                ret.length   = 0;
                ret.dst_reuse_type = 3;
                return ret;
            }
        }

        int limitQueenSize = mLimitQueen.size();
        for (int i = 0;i<limitQueenSize;i++) {
            if(length <= mLimitQueen.get(i).length){
                ret.data = mLimitQueen.remove(i);
                ret.capacity = ret.data.length;
                ret.block_index   = -1;
                ret.offset   = 0;
                ret.length   = 0;
                ret.dst_reuse_type = 4;
                return ret;
            }
        }

        //要么数据体太大，要么数据体全部用完了,自由创建数据
        ret.data = new byte[length];
        ret.capacity = length;
        ret.block_index   = -1;
        ret.offset   = 0;
        ret.length   = 0;
        ret.dst_reuse_type = 4;

        return ret;
    }

    public void release(Message msg){
        if(msg.dst_reuse_type == 1){
            buffer_small_tracker.release(msg.block_index);
            MessagePool.put(msg);
        }else if(msg.dst_reuse_type == 2){
            buffer_middle_tracker.release(msg.block_index);
            MessagePool.put(msg);
        }else if(msg.dst_reuse_type == 3){
            buffer_large_tracker.release(msg.block_index);
            MessagePool.put(msg);
        }else{
            if(MAX_QUEEN_SIZE>0){
                while (mLimitQueen.size() >= MAX_QUEEN_SIZE){
                    mLimitQueen.poll();
                }
                mLimitQueen.add(msg.data);
                msg.reset();
            }else{
                mLimitQueen.clear();
                msg.reset();
            }
        }
    }


    public class MessageBufferTracker{

        private int block_size;

        private byte[] available;
        private int userd_count;
        private int next_avaliable_index;

        public MessageBufferTracker(int block_size){

            this.block_size = block_size;

            available = new byte[block_size];
            for (int i = 0;i<block_size;i++){
                available[i] = 1;
            }
            userd_count = 0;
            next_avaliable_index = 0;
        }

        private int findFreeBlockIndex(){
            if(userd_count < block_size){
                for (int i = next_avaliable_index; i>=0 && i < block_size; i++) {
                    if(available[i] == 1){
                        return i;
                    }
                }

                for (int i=0;i>=0 && i< next_avaliable_index;i++){
                    if(available[i] == 1){
                        return i;
                    }
                }
            }
            return -1;
        }

        public int get(){
            int index = findFreeBlockIndex();
            if(index != -1){
                available[index] = 0;//设置为已用
                userd_count ++;//已用条数+1

                next_avaliable_index++;//寻找下一条可能可用的位置
                if(next_avaliable_index >= block_size){
                    next_avaliable_index = 0;
                }
            }
            return index;
        }

        public void release(int index){
            if(available[index] == 0){
                available[index] = 1;
                userd_count --;
            }
        }
    }
}
