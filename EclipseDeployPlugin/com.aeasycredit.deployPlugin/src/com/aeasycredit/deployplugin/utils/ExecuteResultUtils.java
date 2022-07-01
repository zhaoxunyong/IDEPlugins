package com.aeasycredit.deployplugin.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

public class ExecuteResultUtils {

	public static int getCode(String  result) {
		String codeStr = StringUtils.substringBefore(result, ":");
		return NumberUtils.toInt(codeStr, 1);
	}

	public static String getResult(String result) {
		return StringUtils.substringAfter(result, ":");
	}
}
