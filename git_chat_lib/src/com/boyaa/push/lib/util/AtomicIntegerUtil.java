package com.boyaa.push.lib.util;
/**
 * 
 */


import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Administrator
 *
 */
public final class AtomicIntegerUtil {
	
	private static final AtomicInteger mAtomicInteger=new AtomicInteger();
	
	public static int  getIncrementID()
	{
		return mAtomicInteger.getAndIncrement();
	}
}
