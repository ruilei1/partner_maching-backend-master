package com.lei.usercenter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lei.usercenter.model.domain.UserTeam;
import com.lei.usercenter.service.UserTeamService;
import com.lei.usercenter.mapper.UserTeamMapper;
import org.springframework.stereotype.Service;

/**
* @author lei
* @description 针对表【user_team(用户队伍关系)】的数据库操作Service实现
* @createDate 2025-08-13 18:21:14
*/
@Service
public class UserTeamServiceImpl extends ServiceImpl<UserTeamMapper, UserTeam>
    implements UserTeamService{

}




