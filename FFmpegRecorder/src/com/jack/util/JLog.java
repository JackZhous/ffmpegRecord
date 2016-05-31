package com.jack.util;

import android.util.Log;

public class JLog {
	
	public static boolean debug_mode = true;				//调试模式  默认为true
	
	public static void print(String msg){
		if(debug_mode){
			Log.i("jackzhous", "===="+msg+"====");
		}
	}
	
}
