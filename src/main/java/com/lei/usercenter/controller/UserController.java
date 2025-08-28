package com.lei.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lei.usercenter.common.BaseResponse;
import com.lei.usercenter.common.ErrorCode;
import com.lei.usercenter.common.ResultUtils;
import com.lei.usercenter.exception.BusinessException;
import com.lei.usercenter.model.domain.User;
import com.lei.usercenter.model.request.UserLoginRequest;
import com.lei.usercenter.model.request.UserRegisterRequest;
import com.lei.usercenter.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.lei.usercenter.contant.UserConstant.ADMIN_ROLE;
import static com.lei.usercenter.contant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户接口
 *
 */
@Slf4j   //可以使用log打印日志
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Autowired
    //注入实列
    private FileStorageService fileStorageService;

    @Resource
    private RedisTemplate redisTemplate;


    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return result
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        // 校验
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String planetCode = userRegisterRequest.getPlanetCode();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, planetCode)) {
            return null;
        }
        long result = userService.userRegister(userAccount, userPassword, checkPassword, planetCode);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<User> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request, 
                                        HttpServletResponse response) {
        if (userLoginRequest == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.userLogin(userAccount, userPassword, request);
        
        // 设置Cookie
        if (user != null) {
            // 设置用户名Cookie
            Cookie userAccountCookie = new Cookie("userAccount", userAccount);
            // 7天过期
            userAccountCookie.setMaxAge(7 * 24 * 60 * 60);
            userAccountCookie.setPath("/");
            response.addCookie(userAccountCookie);
            
            // 如果需要记住密码，可以设置密码Cookie（注意：实际项目中不应存储密码，这里仅作演示）
            // Cookie passwordCookie = new Cookie("password", userPassword);
            // passwordCookie.setMaxAge(7 * 24 * 60 * 60);
            // passwordCookie.setPath("/");
            // response.addCookie(passwordCookie);
            
            // 设置记住我Cookie
            Cookie rememberMeCookie = new Cookie("rememberMe", "true");
            rememberMeCookie.setMaxAge(7 * 24 * 60 * 60);
            rememberMeCookie.setPath("/");
            response.addCookie(rememberMeCookie);
        }
        
        return ResultUtils.success(user);
    }

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Integer> userLogout(HttpServletRequest request, HttpServletResponse response) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        // 清除Session
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        
        // 清除我们设置的Cookie
        Cookie userAccountCookie = new Cookie("userAccount", null);
        // 立即过期
        userAccountCookie.setMaxAge(0);
        userAccountCookie.setPath("/");
        response.addCookie(userAccountCookie);
        
        Cookie rememberMeCookie = new Cookie("rememberMe", null);
        rememberMeCookie.setMaxAge(0);
        rememberMeCookie.setPath("/");
        response.addCookie(rememberMeCookie);
        
        // 清除可能存在的用户名和密码Cookie
        Cookie usernameCookie = new Cookie("username", null);
        usernameCookie.setMaxAge(0);
        usernameCookie.setPath("/");
        response.addCookie(usernameCookie);
        
        Cookie passwordCookie = new Cookie("password", null);
        passwordCookie.setMaxAge(0);
        passwordCookie.setPath("/");
        response.addCookie(passwordCookie);
        
        int result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前用户
     *
     * @param request
     * @return
     */
    @GetMapping("/current")
    public BaseResponse<User> getCurrentUser(HttpServletRequest request) {
        //System.out.println("获取当前用户，Session ID: " + request.getSession().getId());
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        //System.out.println("Session 中的用户信息: " + userObj);
        User currentUser = (User) userObj;
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        long userId = currentUser.getId();
        // TODO 校验用户是否合法
        User user = userService.getById(userId);
        User safetyUser = userService.getSafetyUser(user);
        return ResultUtils.success(safetyUser);
    }


    @GetMapping("/search")
    public BaseResponse<List<User>> searchUsers(String username, HttpServletRequest request) {
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "缺少管理员权限");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like("username", username);
        }
        List<User> userList = userService.list(queryWrapper);
        List<User> list = userList.stream().map(user -> userService.getSafetyUser(user)).collect(Collectors.toList());
        return ResultUtils.success(list);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody long id, HttpServletRequest request) {
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    private boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getUserRole() == ADMIN_ROLE;
    }


    /**
     * 根据标签搜索用户
     * @param tagNameList
     * @return
     */
    @GetMapping("/search/tags")
    public BaseResponse<List<User>> searchUsersByTags(@RequestParam(required = false) List<String> tagNameList) {
        //log.info("根据标签搜索用户" + tagNameList);
        if (CollectionUtils.isEmpty(tagNameList)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<User> userList = userService.searchUsersByTags(tagNameList);
        return ResultUtils.success(userList);
    }

    /**
     * 更新用户
     * @param user
     * @param request
     * @return
     */
    @PostMapping("/update")
    public BaseResponse<Integer> updateUser(@RequestBody User user, HttpServletRequest request) {
        //判断参数是否为空
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //判断权限
        User loginUser = userService.getLoginUser(request);
        int result = userService.updateUser(user, loginUser);
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

    /**
     * 分页查询获取所有用户
     * @return
     */
    @GetMapping("/recommend")
    public BaseResponse<Page<User>> recommendUsers(Long pageSize,Long pageNum ,HttpServletRequest request) {
        //1、获取当前用户
        User loginUser = userService.getLoginUser(request);
        //2、创建redis key，不同用户获取到的数据不同
        // 一般用 SystemId:userId:func作为rediskey, func为功能名称,注意不能重复
        String redisKey = String.format("partner_maching:user:recommend:%s", loginUser.getId());
        //log.info("获取推荐用户，redisKey: " + redisKey);
        //3、从redis中获取数据
        ValueOperations valueOperations = redisTemplate.opsForValue();
        //3.1、判断key是否存在，存在表示有缓存，直接读取
        Page<User> userPageList = (Page<User>) valueOperations.get(redisKey);
        if (userPageList != null) {
            return ResultUtils.success(userPageList);
        }
        //3.2、不存在表示没有缓存，查询数据库
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        userPageList = userService.page(new Page<>(pageNum, pageSize), queryWrapper);
        //4、将数据写入redis
        try{
            valueOperations.set(redisKey, userPageList, 30000, TimeUnit.MILLISECONDS);
        }catch (Exception e){
            log.error("redis set key error", e);
        }
        return ResultUtils.success(userPageList);
    }


    /**
     * 获取匹配度最高的用户
     * @param num
     * @param request
     * @return
     */
    @GetMapping("/match")
    public BaseResponse<List<User>> matchUser(long num,HttpServletRequest request){
        if(num<=0 || num >20){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.matchUsers(num,user));
    }

}




















