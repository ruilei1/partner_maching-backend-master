package com.lei.usercenter.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页请求参数
 *
 */
@Data
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    protected int pageNum = 1;   //当前页数
    protected int pageSize = 10;  //页面大小

}
