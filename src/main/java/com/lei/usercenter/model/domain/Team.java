package com.lei.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 队伍
 * @TableName team
 */
@TableName(value ="team")
@Data
public class Team {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 队伍名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 最大人数
     */
    private Integer maxNum;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 用户id（队长 id）
     */
    private Long userId;

    /**
     * 0 - 公开，1 - 私有，2 - 加密
     */
    private Integer status;

    /**
     * 密码
     */
    private String password;

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
     * @TableLogic 逻辑删除的工作原理
     * 当执行查询操作时（如 teamService.list(queryWrapper)），
     * MyBatis-Plus 会自动在生成的 SQL 查询语句中添加条件：is_delete = 0
     * 这意味着只有 isDelete 值为 0 的记录才会被查询出来
     * isDelete 值为 1 的记录会被自动过滤掉，不会出现在查询结果中
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