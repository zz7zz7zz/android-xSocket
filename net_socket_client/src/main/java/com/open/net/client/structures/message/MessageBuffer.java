package com.open.net.client.structures.message;

import com.open.net.client.structures.pools.MessagePool;

import java.util.LinkedList;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   消息缓存，防止内存碎片
 */

public final class MessageBuffer {

    //----------------------------------------------------------------------------------------
    //单元大小
    public static final int KB = 1024;
    public static final int MB = 1024*KB;

    //定义单块消息体内存大小
    private static int capacity_small   = 8   * KB;
    private static int capacity_middle  = 128 * KB;
    private static int capacity_large   = 1   * MB;

    //块数量
    private static int size_small       = 5;
    private static int size_middle      = 2;
    private static int size_large       = 0;

    //buffer依赖的实际数组
    private byte[] buffer_small  ;
    private byte[] buffer_middle ;
    private byte[] buffer_large  ;

    //buffer可用性追踪
    private MessageBufferTracker tracker_buffer_small;
    private MessageBufferTracker tracker_buffer_middle;
    private MessageBufferTracker tracker_buffer_large;

    //----------------------------------------------------------------------------------------
    //临时可缓存对象
    private static int max_size_temporary_cache = 2;
    private LinkedList<byte[]> mTemporaryCacheList = new LinkedList<>();

    //----------------------------------------------------------------------------------------
    //复用类型
    private final int REUSE_SMALL   = 1;
    private final int REUSE_MIDDLE  = 2;
    private final int REUSE_LARGE   = 3;
    private final int REUSE_TEMP    = 4;

    //----------------------------------------------------------------------------------------
    public static void init (int capacity_small ,int capacity_middle, int capacity_large,
                             int size_small, int size_middle, int size_large , int max_temporary_cache_size) {

        MessageBuffer.capacity_small    = capacity_small;
        MessageBuffer.capacity_middle   = capacity_middle;
        MessageBuffer.capacity_large    = capacity_large;

        MessageBuffer.size_small        = size_small;
        MessageBuffer.size_middle       = size_middle;
        MessageBuffer.size_large        = size_large;
        MessageBuffer.max_size_temporary_cache = max_temporary_cache_size;
    }

    public MessageBuffer() {
        buffer_small    = new byte[size_small   * capacity_small];
        buffer_middle   = new byte[size_middle  * capacity_middle];
        buffer_large    = new byte[size_large   * capacity_large];

        tracker_buffer_small    = new MessageBufferTracker(size_small);
        tracker_buffer_middle   = new MessageBufferTracker(size_middle);
        tracker_buffer_large    = new MessageBufferTracker(size_large);
    }

    public Message build(byte[] src,int offset, int length){
        Message msg = build(length);
        System.arraycopy(src,offset,msg.data,msg.offset,length);
        msg.length = length;
        return msg;
    }

    private Message build(int length){
        Message ret = MessagePool.get();

        if(length <= capacity_small){
            ret.src_reuse_type = REUSE_SMALL;
        }else if(length <= capacity_middle){
            ret.src_reuse_type = REUSE_MIDDLE;
        }else if(length <= capacity_large){
            ret.src_reuse_type = REUSE_LARGE;
        }else{
            ret.src_reuse_type = REUSE_TEMP;
        }

        if(length <= capacity_small){
            int block_index = tracker_buffer_small.get();
            if(block_index != -1){
                ret.block_index     = block_index;
                ret.data            = buffer_small;
                ret.capacity        = capacity_small;
                ret.offset          = capacity_small * ret.block_index;
                ret.length          = 0;
                ret.dst_reuse_type  = REUSE_SMALL;
                return ret;
            }
        }

        if(length <= capacity_middle){
            int block_index = tracker_buffer_middle.get();
            if(block_index != -1){
                ret.block_index     = block_index;
                ret.data            = buffer_middle;
                ret.capacity        = capacity_middle;
                ret.offset          = capacity_middle * ret.block_index;
                ret.length          = 0;
                ret.dst_reuse_type  = REUSE_MIDDLE;
                return ret;
            }
        }

        if(length <= capacity_large){
            int block_index = tracker_buffer_large.get();
            if(block_index != -1){
                ret.block_index     = block_index;
                ret.data            = buffer_large;
                ret.capacity        = capacity_large;
                ret.offset          = capacity_large * ret.block_index;
                ret.length          = 0;
                ret.dst_reuse_type  = REUSE_LARGE;
                return ret;
            }
        }

        int mTemporaryCacheListSize = mTemporaryCacheList.size();
        for (int i = 0;i<mTemporaryCacheListSize;i++) {
            if(length <= mTemporaryCacheList.get(i).length){
                ret.block_index     = 0;
                ret.data            = mTemporaryCacheList.remove(i);
                ret.capacity        = ret.data.length;
                ret.offset          = 0;
                ret.length          = 0;
                ret.dst_reuse_type  = REUSE_TEMP;
                return ret;
            }
        }

        //要么数据体太大，要么数据体全部用完了,自由创建数据
        ret.block_index     = 0;
        ret.data            = new byte[length];
        ret.capacity        = ret.data.length;
        ret.offset          = 0;
        ret.length          = 0;
        ret.dst_reuse_type  = REUSE_TEMP;

        return ret;
    }

    public void release(Message msg){
        if(msg.dst_reuse_type == REUSE_SMALL){
            tracker_buffer_small.release(msg.block_index);
            MessagePool.put(msg);
        }else if(msg.dst_reuse_type == REUSE_MIDDLE){
            tracker_buffer_middle.release(msg.block_index);
            MessagePool.put(msg);
        }else if(msg.dst_reuse_type == REUSE_LARGE){
            tracker_buffer_large.release(msg.block_index);
            MessagePool.put(msg);
        }else if(msg.dst_reuse_type == REUSE_TEMP){
            if(max_size_temporary_cache >0){
                while (mTemporaryCacheList.size() >= max_size_temporary_cache){
                    mTemporaryCacheList.poll();
                }
                mTemporaryCacheList.add(msg.data);
                msg.reset();
            }else{
                mTemporaryCacheList.clear();
                msg.reset();
            }
        }else{
            //------------------do nothing------------------
        }
    }


    public class MessageBufferTracker{

        private int size;

        private byte[] array_available_index;//所有可用块的索引
        private int used_count;//已经使用的数量
        private int next_available_index;//下一个可以使用的位置

        public MessageBufferTracker(int size){
            this.size = size;
            array_available_index = new byte[size];
            for (int i = 0;i<size;i++){
                array_available_index[i] = 1;
            }
            used_count = 0;
            next_available_index = 0;
        }

        private int findAvailableIndex(){
            if(used_count < size){
                for (int i = next_available_index; i>=0 && i < size; i++) {
                    if(array_available_index[i] == 1){
                        return i;
                    }
                }

                for (int i = 0; i>=0 && i< next_available_index; i++){
                    if(array_available_index[i] == 1){
                        return i;
                    }
                }
            }
            return -1;
        }

        public int get(){
            int index = findAvailableIndex();
            if(index != -1){
                array_available_index[index] = 0;//设置为已用
                used_count++;//已用条数+1

                next_available_index++;//寻找下一条可能可用的位置
                if(next_available_index >= size){
                    next_available_index = 0;
                }
            }
            return index;
        }

        public void release(int index){
            if(index < 0 || index >= size){
                //------------------error action------------------
                return;
            }
            if(array_available_index[index] == 0){
                array_available_index[index] = 1;
                used_count--;
            }
        }
    }
}
