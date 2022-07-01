package com.aeasycredit.deployplugin.utils;

import java.util.Base64;

/**
 * Base64
 */
public class BASE64Utils {

	//转换为base64
    public static String encoder(String msg){
        return Base64.getEncoder().encodeToString(msg.getBytes());
    }
    //解码base64
    public static String decoder(String msg){
        return new String(Base64.getDecoder().decode(msg));
    }

}