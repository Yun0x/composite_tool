package com.tool.util;

import java.util.List;

public class EmptyUtils {
	
	/**
	 * 功能描述: list 登陆null 或 list.size ==0  返回 true
	 *
	 * @author yangliu  2013-8-17 下午03:44:09
	 * 
	 * @param list list
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static boolean isEmpty(List list){
		return (list==null || list.size()==0);
	}
	
	/**
	 * 功能描述: list 登陆null 或 list.size ==0  返回 true
	 *
	 * @author yangliu  2013-8-17 下午03:44:09
	 * 
	 * @param list list
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static boolean isNotEmpty(List list){
		return (list!=null && list.size()>0);
	}
	
	/**
	 * 功能描述: str 等于空
	 *
	 * @author yangliu  2013-8-17 下午03:44:09
	 * 
	 * @param list list
	 * @return
	 */
	public static boolean isEmpty(String str){
		return (str==null || "".equals(str));
	}
	
	/**
	 * 功能描述: str 不等于空
	 *
	 * @author yangliu  2013-8-17 下午03:44:09
	 * 
	 * @param list list
	 * @return
	 */
	public static boolean isNotEmpty(String str){
		return (str!=null && !str.equals(""));
	}
	
	/**
	 * 功能描述: Object 不等于空
	 *
	 * @author yangliu  2013-8-17 下午03:44:09
	 * 
	 * @param Object Object
	 * @return
	 */
	public static boolean isNotEmpty(Object obj){
		return obj!=null;
	}
	
	/**
	 * 功能描述: Object 等于空
	 *
	 * @author yangliu  2013-8-17 下午03:44:09
	 * 
	 * @param Object Object
	 * @return
	 */
	public static boolean isEmpty(Object obj){
		return obj==null;
	}
	
	/**
	 * 功能描述:
	 *
	 * @author yangliu  2013-8-19 下午04:11:33
	 * 
	 * @param strings 数组
	 * @return
	 */
	public static boolean isEmpty(Object[] strings){
		return (strings==null || strings.length==0);
	}
	
	/**
	 * 功能描述:
	 *
	 * @author yangliu  2013-8-19 下午04:12:01
	 * 
	 * @param strings
	 * @return
	 */
	public static boolean isNotEmpty(Integer num){
		return num!=null;
	}
	
	/**
	 * 功能描述:
	 *
	 * @author yangliu  2013-8-19 下午04:11:33
	 * 
	 * @param strings 数组
	 * @return
	 */
	public static boolean isEmpty(Integer num){
		return num==null;
	}
	
	/**
	 * 功能描述:
	 *
	 * @author yangliu  2013-8-19 下午04:12:01
	 * 
	 * @param strings
	 * @return
	 */
	public static boolean isNotEmpty(Object[] strings){
		return (strings!=null && strings.length>0);
	}
	
	


}
