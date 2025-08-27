package com.lei.usercenter.service;

import com.lei.usercenter.model.domain.Team;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lei.usercenter.model.domain.User;
import com.lei.usercenter.model.dto.TeamQuery;
import com.lei.usercenter.model.request.TeamJoinRequest;
import com.lei.usercenter.model.request.TeamQuitRequest;
import com.lei.usercenter.model.request.TeamUpdateRequest;
import com.lei.usercenter.model.vo.TeamUserVO;

import java.util.List;

/**
* @author lei
* @description 针对表【team(队伍)】的数据库操作Service
* @createDate 2025-08-13 18:18:22
*/
public interface TeamService extends IService<Team> {

    /**
     *   添加队伍
     * @param team
     * @param loginUser
     * @return
     */
    Long addTeam(Team team, User loginUser);

    /**
     *   获取队伍列表
     * @param teamQuery
     * @param isAdmin
     * @return
     */
    List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdmin);

    /**
     * 更新队伍
     * @param teamUpdateRequest
     * @return
     */
    boolean updateTeam(TeamUpdateRequest teamUpdateRequest,User loginUser);

    /**
     * 加入队伍
     * @param teamJoinRequest
     * @return
     */
    boolean joinTeam(TeamJoinRequest teamJoinRequest,User loginUser);

    /**
     * 退出队伍
     * @param teamQuitRequest
     * @param user
     * @return
     */
    boolean quitTeam(TeamQuitRequest teamQuitRequest, User user);

    /**
     * 解散删除队伍
     * @param teamId
     * @param user
     * @return
     */
    boolean deleteTeam(Long teamId, User user);
}
