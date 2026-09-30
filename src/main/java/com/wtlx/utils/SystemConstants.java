package com.wtlx.utils;

public class SystemConstants {
    // 静态资源根目录（nginx 托管目录，同时也是后端静态映射的根）
    public static final String STATIC_ROOT = "D:\\nginx-1.22.0\\nginx-1.18.0-wtlx\\html\\wtlx";

    public static final String IMAGE_UPLOAD_DIR = STATIC_ROOT + "\\imgs\\blogs";

    public static final String VIDEO_UPLOAD_DIR = STATIC_ROOT + "\\video";
    //服务器文件上传地址
//    public static final String IMAGE_UPLOAD_DIR = "/www/server/nginx/html/wtlx/imgs/blogs";
//
//    public static final String VIDEO_UPLOAD_DIR = "/www/server/nginx/html/wtlx/video";

    //public static final String serverAddress = "http://193.112.194.61:9090/images/";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 100;
}
