package com.lei.usercenter.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户解散队伍请求体
 * @TableName team
 */
@Data
public class TeamDeleteRequest implements Serializable {

    /**
     * id
     */
    private Long teamId;



    private static final long serialVersionUID = 1L;
}