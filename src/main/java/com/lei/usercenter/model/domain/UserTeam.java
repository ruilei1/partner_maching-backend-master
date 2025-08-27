package com.lei.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 用户队伍关系
 * @TableName user_team
 */
@TableName(value ="user_team")
@Data
public class UserTeam {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 队伍id
     */
    private Long teamId;

    /**
     * 加入时间
     */
    private Date joinTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic  //逻辑删除
    private Integer isDelete;

    /**
     * 序列化版本标识符，用于保证序列化对象的版本兼容性
     *
     * @TableField(exist = false) 注解表示该字段不是数据库表中的字段，
     * 而是实体类的辅助属性，避免MyBatis-Plus在操作数据库时将其作为表字段处理
     */
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}