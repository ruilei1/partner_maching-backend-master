package com.lei.usercenter.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户加入队伍请求体
 * @TableName team
 */
@Data
public class TeamJoinRequest implements Serializable {

    /**
     * id
     */
    private Long teamId;

    /**
     * 密码
     */
    private String password;


    private static final long serialVersionUID = 1L;
}