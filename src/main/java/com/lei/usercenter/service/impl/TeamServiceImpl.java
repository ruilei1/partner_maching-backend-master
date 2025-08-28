package com.lei.usercenter.service.impl;
import java.util.Date;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lei.usercenter.common.ErrorCode;
import com.lei.usercenter.exception.BusinessException;
import com.lei.usercenter.model.domain.Team;
import com.lei.usercenter.model.domain.User;
import com.lei.usercenter.model.domain.UserTeam;
import com.lei.usercenter.model.dto.TeamQuery;
import com.lei.usercenter.model.enums.TeamStatusEnum;
import com.lei.usercenter.model.request.TeamJoinRequest;
import com.lei.usercenter.model.request.TeamQuitRequest;
import com.lei.usercenter.model.request.TeamUpdateRequest;
import com.lei.usercenter.model.vo.TeamUserVO;
import com.lei.usercenter.model.vo.UserVO;
import com.lei.usercenter.service.TeamService;
import com.lei.usercenter.mapper.TeamMapper;
import com.lei.usercenter.service.UserService;
import com.lei.usercenter.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
* @author lei
* @description 针对表【team(队伍)】的数据库操作Service实现
* @createDate 2025-08-13 18:18:22
*/
@Slf4j
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
    implements TeamService{

    @Resource
    UserTeamService userTeamService;

    @Autowired
    private UserService userService;
    @Autowired
    private ErrorPageRegistrar errorPageRegistrar;
    @Resource
    private RedissonClient redissonClient;


    /**
     * 创建队伍
     * @param team
     * @param loginUser
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long addTeam(Team team, User loginUser) {
         /*
        1.请求参数是否为空？
        2.是否登录，未登录不允许创建
        3.校验信息
         */
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        //2.队伍标题<=20
        String teamName = team.getName();
        if (teamName == null || teamName.isEmpty() || teamName.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍名称过短或过长");
        }
        //3.描述<=512
        String description = team.getDescription();
        if (description != null && description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍描述过长");
        }
        //1.队伍人数>1且<=20
        int maxTeamSize = Optional.ofNullable(team.getMaxNum()).orElse(0);
        if (maxTeamSize > 20 || maxTeamSize < 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数需要在1-20之间。");
        }
        //4.status是否公开（int）不传默认为0(公开)
        int status = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
        if (teamStatusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态错误");
        }
        //5.如果status是加密状态，一定要有密码，且密码<=32
        String password = team.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || password.length() > 32) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码设置不正确");
            }
        }
        //6.超时时间>当前时间
        Date expireTime = team.getExpireTime();
        if(new Date().after(expireTime)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "超时时间设置不正确");
        }
        //7、校验用户最多创建5个队伍
        // todo 有BUG，当用户同时快速点击，会创建多个队伍
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        //根据用户ID 查询
        queryWrapper.eq("userId", loginUser.getId());
        long hasTeamNumber = this.count(queryWrapper);
        if(hasTeamNumber >= 5){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建5个队伍");
        }
        //8、创建队伍
        team.setId(null);
        team.setUserId(loginUser.getId());
        boolean result = this.save(team);
        Long teamId = team.getId();
        if (!result || teamId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        //9. 插入用户 => 队伍关系到关系表
        UserTeam userTeam = new UserTeam();
        userTeam.setUserId(loginUser.getId());
        userTeam.setTeamId(teamId);
        userTeam.setJoinTime(new Date());
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建队伍失败");
        }
        return teamId;
    }

    /**
     * 获取列表
     *
     * @param teamQuery
     * @param isAdmin
     * @return List
     */
    @Override
    public List<TeamUserVO> listTeams(TeamQuery teamQuery, boolean isAdmin) {
        //log.info("teamQuery{}", teamQuery);
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        if(teamQuery != null){
            Long id = teamQuery.getId();
            if(id != null && id>0){
                queryWrapper.eq("id" , id);
            }
            List<Long> idList = teamQuery.getIdList();
            if (!CollectionUtils.isEmpty(idList)) {
                queryWrapper.in("id", idList);
            }
            String serachTest = teamQuery.getSearchText();
            if(StringUtils.isNotBlank(serachTest)){
                queryWrapper.and(qw -> qw.like("name",serachTest).or().like("description",serachTest));
            }
            String name = teamQuery.getName();
            if(StringUtils.isNotBlank(name)){
                queryWrapper.like("name" , name);
            }
            String description = teamQuery.getDescription();
            if(StringUtils.isNotBlank(description)){
                queryWrapper.like("description",description);
            }
            Integer maxNum = teamQuery.getMaxNum();
            //查询最大人数相等的
            if (maxNum!=null && maxNum>0){
                queryWrapper.eq("maxNum", maxNum);
            }
            Long userId = teamQuery.getUserId();
            //根据创建人ID查询
            if(userId !=null && userId>0){
                queryWrapper.eq("userId",userId);
            }
            Integer status = teamQuery.getStatus();
            //根据状态来查询
            //只有管理员可以查看加密、非公开的队伍
            TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);
            if(statusEnum == null){
                statusEnum = TeamStatusEnum.PUBLIC;
            }
            //是管理员或者是公开的可以访问反之无权限
            if(!isAdmin && !statusEnum.equals(TeamStatusEnum.PUBLIC)){
                throw new BusinessException(ErrorCode.NO_AUTH);
            }
            queryWrapper.eq("status",statusEnum.getValue());

        }
        //不展示已过期队伍
        //gt("expireTime", new Date()) 表示查询 expireTime 大于当前时间的记录
        //这样就过滤掉了已过期的队伍（expireTime 小于等于当前时间的记录）
        //只返回未过期的队伍
        //queryWrapper.gt("expireTime", new Date());

        queryWrapper.and(qw -> qw.gt("expireTime",new Date()).or().isNull("expireTime"));

        List<Team> teamList  = this.list(queryWrapper);
        //检查集合是否为空
        if(CollectionUtils.isEmpty(teamList)){
            return new ArrayList<>();
        }
        List<TeamUserVO> teamUserVOList = new ArrayList<>();
        //关联查询用户信息
        for(Team team : teamList){
            Long userId = team.getUserId();
            if(userId == null){
                continue;
            }
            User user = userService.getById(userId);
            TeamUserVO teamUserVO = new TeamUserVO();
            BeanUtils.copyProperties(team , teamUserVO);
            //用户信息脱敏
            if(user != null){
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user,userVO);
                teamUserVO.setCreateUser(userVO);
            }
            teamUserVOList.add(teamUserVO);
        }
        return teamUserVOList;
    }

    /**
     * 更新队伍
     * @param teamUpdateRequest
     * @return
     */
    @Override
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest,User loginUser) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = teamUpdateRequest.getId();
        if(id==null || id<=0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team oldTeam = this.getById(id);
        if(oldTeam == null){
            throw new BusinessException(ErrorCode.NULL_ERROR,"不存在此队伍");
        }
        //只有管理员或者队伍的创建者才可以修改队伍
        if(!Objects.equals(oldTeam.getUserId(), loginUser.getId()) && !(userService.isAdmin(loginUser))){
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        Team newTeam = new Team();
        BeanUtils.copyProperties(teamUpdateRequest,newTeam );
        // 如果老值和新值一样，直接返回 true
        if (oldTeam.equals(newTeam)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"修改后结果与原结果一致。");
        }
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(teamUpdateRequest.getStatus());
        if(teamStatusEnum.equals(TeamStatusEnum.SECRET) ){
            if(StringUtils.isBlank(teamUpdateRequest.getPassword())){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"加密队伍必须设置密码。");
            }
        }
        return this.updateById(newTeam);
    }

    /**
     * 加入队伍
     * @param teamJoinRequest
     * @return
     */
    @Override
    public boolean joinTeam(TeamJoinRequest teamJoinRequest,User loginUser) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamJoinRequest.getTeamId();
        if (teamId == null || teamId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍不存在");
        }
        Date expireTime = team.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已过期");
        }
        Integer status = team.getStatus();
        TeamStatusEnum teamStatusEnum = TeamStatusEnum.getEnumByValue(status);
        if (TeamStatusEnum.PRIVATE.equals(teamStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止加入私有队伍");
        }
        String password = teamJoinRequest.getPassword();
        if (TeamStatusEnum.SECRET.equals(teamStatusEnum)) {
            if (StringUtils.isBlank(password) || !password.equals(team.getPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
            }
        }
        //该用户已加入的队伍数量
        long userId = loginUser.getId();

        // 只有一个线程能获取到锁
        RLock lock = redissonClient.getLock("user:join_team");
        try {
            // 抢到锁并执行
            while (true) {
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                    System.out.println("getLock: " + Thread.currentThread().getId());
                    QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", userId);
                    long cnt = userTeamService.count(userTeamQueryWrapper);
                    if (cnt >= 5) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多创建和加入5个队伍");
                    }
                    //不能重复加入已加入的队伍
                    userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("teamId", teamId);
                    userTeamQueryWrapper.eq("userId", userId);
                    long num = userTeamService.count(userTeamQueryWrapper);
                    if (num > 0) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "已加入队伍，不可以重复加入");
                    }
                    //已加入队伍的人数
                    long teamJoinNum = this.countTeamUserByTeamId(teamId);
                    if (teamJoinNum >= team.getMaxNum()) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已满");
                    }
                    //修改队伍信息
                    UserTeam userTeam = new UserTeam();
                    userTeam.setUserId(userId);
                    userTeam.setTeamId(teamId);
                    userTeam.setJoinTime(new Date());
                    return userTeamService.save(userTeam);
                }
            }
        } catch (InterruptedException e) {
            log.error("doCacheRecommendUser error", e);
            return false;
        } finally {
            // 只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }
        }
    }

    /**
     * 退出队伍
     * @param teamQuitRequest
     * @param loginUser
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
        if(teamQuitRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long teamId = teamQuitRequest.getTeamId();
        if(teamId == null || teamId<=0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if(team==null){
            throw new BusinessException(ErrorCode.NULL_ERROR,"队伍不存在");
        }
        long userId = loginUser.getId();
        UserTeam queryUserTeam = new UserTeam();
        queryUserTeam.setUserId(userId);
        queryUserTeam.setTeamId(teamId);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>(queryUserTeam);
        long count = userTeamService.count(queryWrapper);
        if(count==0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"未加入队伍");
        }
        long teamJoinNum = this.countTeamUserByTeamId(teamId);
        //队伍只剩下1人，直接解散
        if(teamJoinNum == 1){
            //删除队伍和所有加入队伍的关系
            this.removeById(teamId);
            QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
            userTeamQueryWrapper.eq("teamId",teamId);
            return  userTeamService.remove(userTeamQueryWrapper);
        }else{
            //是否是队长
            if(team.getUserId() == userId){
                //把队伍转移给最早加入的用户
                //1、查询已加入的所有用户和加入时间
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.eq("teamId",teamId);
                userTeamQueryWrapper.last("order by id asc limit 2");
                List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
                if(CollectionUtils.isEmpty(userTeamList) || userTeamList.size() <= 1){
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }
                UserTeam nextUserTeam = userTeamList.get(1);
                Long nextTeamLeaderId = nextUserTeam.getUserId();
                //更新当前队伍的队长
                Team updateTeam = new Team();
                updateTeam.setId(teamId);
                updateTeam.setUserId(nextTeamLeaderId);
                boolean result =  this.updateById(updateTeam);
                if(!result){
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,"更新队长失败");
                }
            }
            //不是队长也直接移除
            //移除关系
            return userTeamService.remove(queryWrapper);
        }
    }

    /**
     * 删除解散队伍
     * @param teamId
     * @param loginUser
     * @return
     */
    @Override
    //因为下面牵扯到多张表数据库操作，防止出现脏数据，要么都执行，要么都不执行
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(Long teamId, User loginUser) {
        //1、校验队伍是否存在
        if(teamId == null || teamId<=0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = this.getById(teamId);
        if(team==null){
            throw new BusinessException(ErrorCode.NULL_ERROR,"队伍不存在");
        }
        //2、校验队长
        if(!Objects.equals(team.getUserId(), loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH,"无访问权限");
        }
        //3、移除所有加入队伍的关联信息
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId",team.getId());
        boolean result = userTeamService.remove(userTeamQueryWrapper);
        if(!result){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"删除队伍关联信息");
        }
        //5、删除队伍
        this.removeById(team.getId());
        return true;
    }

    /**
     * 根据teamId查询队伍人数
     * @param teamId
     * @return
     */
    private long countTeamUserByTeamId(long teamId){
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId",teamId);
        return userTeamService.count(userTeamQueryWrapper);
    }
}




