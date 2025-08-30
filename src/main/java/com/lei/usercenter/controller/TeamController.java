package com.lei.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lei.usercenter.common.BaseResponse;
import com.lei.usercenter.common.ErrorCode;
import com.lei.usercenter.common.ResultUtils;
import com.lei.usercenter.exception.BusinessException;
import com.lei.usercenter.model.domain.Team;
import com.lei.usercenter.model.domain.User;
import com.lei.usercenter.model.domain.UserTeam;
import com.lei.usercenter.model.dto.TeamQuery;
import com.lei.usercenter.model.request.*;
import com.lei.usercenter.model.vo.TeamUserVO;
import com.lei.usercenter.service.TeamService;
import com.lei.usercenter.service.UserService;
import com.lei.usercenter.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 队伍管理
 *
 */
@Slf4j   //可以使用log打印日志
@RestController
@RequestMapping("/team")
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    UserTeamService userTeamService;

    @Autowired
    //注入实列
    private FileStorageService fileStorageService;


    /**
     * 创建队伍
     *
     *
     */
    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request){
        if (teamAddRequest == null){
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        User logininUser = userService.getLoginUser(request);
        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest,team);
        long teamId = teamService.addTeam(team,logininUser);
        return ResultUtils.success(teamId);
    }



    /**
     * 解散队伍
     * @param teamDeleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody TeamDeleteRequest teamDeleteRequest, HttpServletRequest request) {
        if(teamDeleteRequest == null ){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User logininUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam( teamDeleteRequest.getTeamId(),logininUser);

        if (!result){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"删除数据失败");
        }
        return ResultUtils.success(true);
    }


    /**
     * 更新队伍信息
     * @param teamUpdateRequest
     * @param httpServletRequest
     * @return
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest,HttpServletRequest httpServletRequest) {
        if(teamUpdateRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user  = userService.getLoginUser(httpServletRequest);
        boolean result = teamService.updateTeam( teamUpdateRequest,user);
        if (!result){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"更新数据失败");
        }
        return ResultUtils.success(true);
    }

    /**
     * 获取队伍信息
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(@RequestParam Long id) {
        if(id <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = teamService.getById(id);
        if (team == null){
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return ResultUtils.success(team);
    }


    /**
     * 获取队伍列表
     * @param teamQuery
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if(teamQuery == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean isAdmin = userService.isAdmin(request);
        // 1、查询队伍列表
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery,isAdmin);
        final List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        // 2、判断当前用户是否已加入队伍
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        try {
            User loginUser = userService.getLoginUser(request);
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.in("teamId", teamIdList);
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            // 已加入的队伍 id 集合
            Set<Long> hasJoinTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
            teamList.forEach(team -> {
                boolean hasJoin = hasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });
        } catch (Exception ignored) {}
        //3、查询已加入的队伍的人数
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId" , teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        //队伍 id=> 加入这个队伍的用户表
        Map<Long,List<UserTeam>> teamIdUserTeamList = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamList.forEach(team -> team.setHasJoinNum(teamIdUserTeamList.getOrDefault(team.getId(),new ArrayList<>()).size()));
        return ResultUtils.success(teamList);
    }


    /**
     * 获取我创建的队伍
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User logininUser = userService.getLoginUser(request);
        teamQuery.setUserId(logininUser.getId());
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery,true);
        // 如果没有创建的队伍，直接返回空列表
        //避免无效SQL查询：当 teamList 为空时，直接返回结果，避免执行 teamId IN () 这种无效SQL
        if (teamList.isEmpty()) {
            return ResultUtils.success(teamList);
        }
        final List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamList.forEach(team -> team.setHasJoinNum(
                teamIdUserTeamList.getOrDefault(team.getId(), new ArrayList<>()).size()));
        return ResultUtils.success(teamList);
    }

    /**
     *  获取我加入的队伍
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User logininUser = userService.getLoginUser(request);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId",logininUser.getId());
        List<UserTeam> userTeamlist = userTeamService.list(queryWrapper);

        // 如果用户没有加入任何队伍，直接返回空列表
        if (userTeamlist.isEmpty()) {
            return ResultUtils.success(new ArrayList<>());
        }

        // 取出不重复的队伍 id
        Map<Long, List<UserTeam>> listMap = userTeamlist.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        ArrayList<Long> idList = new ArrayList<>(listMap.keySet());
        teamQuery.setIdList(idList);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery,true);

        final List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        // 2、判断当前用户是否已加入队伍
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        try {
            User loginUser = userService.getLoginUser(request);
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.in("teamId", teamIdList);
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            // 已加入的队伍 id 集合
            Set<Long> hasJoinTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
            teamList.forEach(team -> {
                boolean hasJoin = hasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });
        } catch (Exception ignored) {}
        //3、查询已加入的队伍的人数
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId" , teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        //队伍 id=> 加入这个队伍的用户表
        Map<Long,List<UserTeam>> teamIdUserTeamList = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamList.forEach(team -> team.setHasJoinNum(teamIdUserTeamList.getOrDefault(team.getId(),new ArrayList<>()).size()));
        log.info(teamList.toString());
        return ResultUtils.success(teamList);
    }


    /**
     * 分页获取队伍信息
     * @param teamQuery
     * @return
     */
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQuery teamQuery) {
        if(teamQuery == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = new Team();
        // 将TeamQuery对象的属性值复制到Team对象中，用于构建查询条件
        try {
            BeanUtils.copyProperties(teamQuery, team);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "属性复制失败");
        }
        Page<Team> page = new Page<>(teamQuery.getPageNum(), teamQuery.getPageSize());
        // 根据Team对象创建查询条件构造器
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
        Page<Team> resultPage = teamService.page(page,queryWrapper);
        return ResultUtils.success(resultPage);
    }

    /**
     * 加入队伍
     * @param teamJoinRequest
     * @param request
     * @return
     */
    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request){
        if(teamJoinRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user  = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest,user);
        return ResultUtils.success(result);
    }

    /**
     * 退出队伍
     * @param teamQuitRequest
     * @param request
     * @return
     */
    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest , HttpServletRequest request){
        if(teamQuitRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user  = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest,user);
        return ResultUtils.success(result);
    }



    /**
     * 上传图片
     * @param file
     * @return
     */
    @PostMapping("/upload/image")
    public BaseResponse<String> uploadImage(@RequestParam("file") MultipartFile file) {
        FileInfo fileInfo = fileStorageService.of(file)
                .setPath("upload/") //保存到相对路径下，为了方便管理，不需要可以不写
                //.setSaveFilename("") //设置保存的文件名，不需要可以不写，会随机生成
                .setObjectId("0")   //关联对象id，为了方便管理，不需要可以不写
                .setObjectType("0") //关联对象类型，为了方便管理，不需要可以不写
                .putAttr("role","admin") //保存一些属性，可以在切面、保存上传记录、自定义存储平台等地方获取使用，不需要可以不写
                .upload();  //将文件上传到对应地方
        return ResultUtils.success(fileInfo == null ? "上传失败！" : fileInfo.getUrl());
    }


}




















