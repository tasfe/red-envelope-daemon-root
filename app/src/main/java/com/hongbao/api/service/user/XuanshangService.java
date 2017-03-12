package com.hongbao.api.service.user;

import com.hongbao.api.dao.*;
import com.hongbao.api.enums.MissionSubtype;
import com.hongbao.api.enums.MissionType;
import com.hongbao.api.model.*;
import com.hongbao.api.model.dto.ReBannerInfo;
import com.hongbao.api.model.vo.*;
import com.hongbao.api.service.redis.RedisBannerService;
import com.hongbao.api.util.CommonMethod;
import com.hongbao.api.util.JsonUtil;
import com.hongbao.api.util.StringUtil;
import com.wujie.common.utils.FastJsonUtil;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Created by Janita on 2016/12/26.
 */
@Service
public class XuanshangService {

    @Autowired
    private ReXuanshangDAO reXuanshangDAO;
    @Autowired
    private ReUserDAO reUserDAO;
    @Autowired
    private ReXuanshangDetailDAO reXuanshangDetailDAO;
    @Autowired
    private ReAccountDetailDAO reAccountDetailDAO;
    @Autowired
    private UserService userService;
    @Autowired
    private RedisBannerService redisBannerService;
    @Autowired
    private UserCacheService userCacheService;


    /**
     * 获取bann列表
     * @param platform
     * @param versioncode
     * @param packageName
     * @param channelName
     * @param flag
     * @return
     */
    public List<ReBannerInfo> selectXuanshangBanner(int platform, int versioncode, String packageName, String channelName, boolean flag){
        return redisBannerService.selectXuanshangBanner(platform, flag, versioncode, packageName, channelName);
    }

    /**
     * 获取发布悬赏任务的机会
     * @param userId
     * @return
     */
    public int missionTimes(Integer userId){
        if (!StringUtil.isEmpty(userId)){
            return reUserDAO.selectByPrimaryKey(userId).getMissionTimes();
        }else {
            return 0;
        }
    }

    /**
     * 悬赏任务首页
     * 悬赏任务列表
     * 悬赏任务列表不需要登录
     * 且发布人与做任务的人看见的都是一样的
     * 具体的区别在详情页下面的按钮区别
     * @param xuanshangId   当前页面最后一个悬赏任务id
     * @param userId        当前登录用户id，没有登录就是null
     * @return
     */
    public List<XuanshangVo> getXuanshangList(Integer userId,Long xuanshangId){

        List<XuanshangVo> xuanshangs = reXuanshangDAO.getXuanshangList(xuanshangId);
        List<XuanshangVo> vos = new ArrayList<>(xuanshangs.size());
        for (XuanshangVo vo:xuanshangs){
            vo = handleVos(userId,vo,System.currentTimeMillis());
            vos.add(vo);
        }
        return vos;
    }


    /**
     * 对查出的数据进行适当的处理
     * @param userId                当前登录用户id，没有登录就是null
     * @param vo                    对象
     * @param now                   当前时间
     * @return
     */
    public XuanshangVo handleVos(Integer userId,XuanshangVo vo,long now){

        XuanshangVo transferVo = new XuanshangVo();

        String missionContent = "总悬赏"+vo.getTotalMoney()+"元，简单几步就能赚钱，快来交作业～";
        int submitNum=reXuanshangDetailDAO.getSubmitNum(vo.getXuanshangId());
        String statusContent = "共有"+vo.getTotalNum()+"个任务，"+submitNum+"人已提交";
        // 发布时间
        String createTime = vo.getCreateTime();
        createTime = getShowTime(createTime);
        transferVo.setXuanshangId(vo.getXuanshangId());
        transferVo.setPortrait(vo.getPortrait());
        transferVo.setNickname(vo.getNickname());
        transferVo.setCreateTime(createTime);
        transferVo.setMissionDesc(vo.getMissionDesc());
        transferVo.setMissionContent(missionContent);
        transferVo.setStatusContent(statusContent);

        if (userId == null){
            transferVo.setIsSelf(0);
        }else {
            if (userId == vo.getUserId()){
                transferVo.setIsSelf(1);
            }
        }

        return transferVo;
    }

    /**
     * 把发布时间转换未
     * 类似微信的显示方式
     * 45分钟前
     * 1小时前
     * 。。。。
     * @param createTime
     * @return
     */
    public String getShowTime(String createTime){

        long now = System.currentTimeMillis();
        long createTimeMs = CommonMethod.get13Timestamp(createTime);
        long publishTime = now - createTimeMs;
        long time;
        if(publishTime < 60 * 1000) { // 60秒以内
            time = publishTime / 1000 + 1;
            createTime = time + "秒前";
        }else if(publishTime < 60 * 60 * 1000) { // 60分钟以内
            time = publishTime / (1000 * 60) + 1;
            createTime = time + "分钟前";
        }else if(publishTime < 24 * 60 * 60 * 1000) { // 24小时以内
            time = publishTime / (1000 * 60 * 60) + 1;
            createTime = time + "小时前";
        }else { // 超过1天
            createTime = CommonMethod.fmtMd(createTimeMs);
        }
        return createTime;
    }

    /**
     * 我参与的任务
     * @param id
     * @return
     */
    public List<XuanshangVo> getMyPartInMissionList(Integer userId,Long id){

        List<XuanshangVo> myPartInList = reXuanshangDetailDAO.getMyPartInMissionList(userId,id);
        List<XuanshangVo> vos = new ArrayList<>(myPartInList.size());
        for (XuanshangVo vo:myPartInList){

            XuanshangVo transferVo = new XuanshangVo();

            String missionContent = "总悬赏"+vo.getTotalMoney()+"元，简单几步就能赚钱，快来交作业～";
            String statusContent = "共有"+vo.getTotalNum()+"个任务，"+vo.getPassNum()+"人已通过";
            // 状态; 0：待审核,1:已通过,2:未通过,3:来晚了，
            Integer detailStatus = vo.getDetailStatus();
            String  statusText;
            if (detailStatus == 1){
                statusText = "已通过";
            }else if (detailStatus == 2){
                statusText = "未通过";
            }else if ((detailStatus == 3)){
                statusText = "来晚了";
            }else {
                statusText="待审核";
            }
            transferVo.setId(vo.getId());
            transferVo.setXuanshangId(vo.getXuanshangId());
            transferVo.setPortrait(vo.getPortrait());
            transferVo.setNickname(vo.getNickname());
            transferVo.setDetailStatus(detailStatus);
            transferVo.setStatusText(statusText);
            transferVo.setMissionDesc(vo.getMissionDesc());
            transferVo.setMissionContent(missionContent);
            transferVo.setStatusContent(statusContent);

            vos.add(transferVo);
        }

        return vos;
    }


    /**
     * 我发布的悬赏任务列表
     * @param xuanshangId   当前页面最后一个我发布的悬赏任务的id
     * @return
     */
    public List<XuanshangVo> getMySendMissionList(Integer userId,Long xuanshangId){

        List<XuanshangVo> myPartInList = reXuanshangDAO.getMySendMissionList(userId,xuanshangId);
        List<XuanshangVo> vos = new ArrayList<>(myPartInList.size());
        for (XuanshangVo vo:myPartInList){

            XuanshangVo transferVo = new XuanshangVo();

            String missionContent = "总悬赏"+vo.getTotalMoney()+"元，简单几步就能赚钱，快来交作业～";
            Integer passNum = vo.getPassNum();
            String statusContent = "共有"+vo.getTotalNum()+"个任务，"+passNum+"人已通过";
            Integer totalNum = vo.getTotalNum();
            // 状态; 1:待审核：10，2：已结束
            Integer detailStatus;
            String  statusText;
            if (passNum >= totalNum){
                detailStatus = 4;
                statusText = "已结束";
            }else {
                //找到待审核的数
                int verifyNum = reXuanshangDetailDAO.getToVerifyNum(vo.getXuanshangId());
                detailStatus = 0;
                statusText = "待审核:"+verifyNum;

            }

            transferVo.setXuanshangId(vo.getXuanshangId());
            transferVo.setPortrait(vo.getPortrait());
            transferVo.setNickname(vo.getNickname());
            transferVo.setDetailStatus(detailStatus);
            transferVo.setStatusText(statusText);
            transferVo.setMissionDesc(vo.getMissionDesc());
            transferVo.setMissionContent(missionContent);
            transferVo.setStatusContent(statusContent);

            vos.add(transferVo);
        }

        return vos;
    }



    /**
     * 用户发布悬赏任务
     * @param totalNum      总个数
     * @param singleMoney   单个金额
     * @param missionDesc   描述／要求
     * @param imgs 图片，多张用分号隔开
     * @return
     */
    public String send(Integer userId, Integer totalNum, BigDecimal singleMoney, String missionDesc,String imgs){

        Map<String ,Object> dataResult = new HashMap<>(2);

        ReUser user = reUserDAO.selectLockByUserId(userId);

        BigDecimal totalMoney = singleMoney.multiply(new BigDecimal(totalNum));
        BigDecimal userMoney = user.getUserMoney();
        if (userMoney.compareTo(totalMoney) < 0){
            dataResult.put("code",0);
            dataResult.put("msg","余额不足");
            return JsonUtil.buildSuccessDataJson(dataResult);
        }
        if (user.getMissionTimes()<1){
            dataResult.put("code",1);
            dataResult.put("msg","发布悬赏任务机会已用完");
            return JsonUtil.buildSuccessDataJson(dataResult);
        }

        user.setMissionTimes(user.getMissionTimes() - 1);
        user.setUpdateTime(System.currentTimeMillis());
        user.setUserMoney(userMoney.subtract(totalMoney));
        reUserDAO.updateByPrimaryKeySelective(user);

        ReAccountDetail detail = new ReAccountDetail();
        detail.setUserId(userId);
        detail.setAppId(user.getAppId());
        detail.setAccountMoney(totalMoney);
        detail.setDetailType(0);
        detail.setMissionType(MissionType.reward_mission.val);
        detail.setMissionSubtype(MissionSubtype.send_reward.val);
        detail.setDetailContent("发布悬赏任务");
        detail.setDetailTime(CommonMethod.fmtTime(System.currentTimeMillis()));
        reAccountDetailDAO.insertSelective(detail);

        ReXuanshang xuanshang = new ReXuanshang();
        xuanshang.setUserId(userId);
        xuanshang.setTotalNum(totalNum);
        xuanshang.setPassNum(0);
        xuanshang.setSingleMoney(singleMoney);
        xuanshang.setMissionDesc(missionDesc);
        xuanshang.setMissionImg(imgs);
        xuanshang.setCreateTime(CommonMethod.fmtTime(System.currentTimeMillis()));
        xuanshang.setUpdateTime(CommonMethod.fmtTime(System.currentTimeMillis()));
        reXuanshangDAO.insert(xuanshang);

        dataResult.put("code",2);
        dataResult.put("msg","发布成功");

        // 删除缓存
        userCacheService.removeUser(userId);

        return JsonUtil.buildSuccessDataJson(dataResult);
    }

    /**
     * 交作业
     * 提交悬赏任务给发布者审核
     * @param xuanshangId       所做的悬赏任务的id
     * @param missionText       用户输入的备注
     * @param xuanshangImgs     用户上传的图片，用分号隔开
     * 格式：["https://ag-aw-test.oss-cn-hangzhou.aliyuncs.com/i/10/1482893568000499.jpg","https://ag-aw-test.oss-cn-hangzhou.aliyuncs.com/i/10/1482893571000430.jpg"]
     * @return
     */
    public String submitXuanshang(Integer userId,Long xuanshangId,String missionText,String xuanshangImgs){

        Map<String,Object> dataResult = new HashMap<>(2);

        //同一个悬赏任务一个人只能做一次
        ReXuanshangDetail oldDetail = reXuanshangDetailDAO.getDetailByUserId(xuanshangId,userId);
        if (oldDetail != null){

            dataResult.put("code",0);
            dataResult.put("msg","已经做过该任务了");
        }else {

            ReXuanshang xuanshang = reXuanshangDAO.selectByPrimaryKey(xuanshangId);
            if (xuanshang.getTotalNum() <= xuanshang.getPassNum()){//该任务已经结束了
                dataResult.put("code",1);
                dataResult.put("msg","该任务已经结束");
            }else {//该任务没有结束

                ReXuanshangDetail detail = new ReXuanshangDetail();
                detail.setXuanshangId(xuanshangId);
                detail.setUserId(userId);
                detail.setMissionImg(xuanshangImgs);
                detail.setMissionText(missionText);
                detail.setDetailStatus(0);
                detail.setCreateTime(CommonMethod.fmtTime(System.currentTimeMillis()));
                detail.setUpdateTime(CommonMethod.fmtTime(System.currentTimeMillis()));

                reXuanshangDetailDAO.insertSelective(detail);

                dataResult.put("code",2);
                dataResult.put("msg","交作业成功");
            }
        }
        return JsonUtil.buildSuccessDataJson(dataResult);
    }

    /**
     * 审核通过（悬赏任务）
     * @param xuanshangId       悬赏任务id
     * @param id                用户做的详情id
     * @return
     */
    public String pass(Long xuanshangId,Long id){

        Map<String,Object> dataResult = new HashMap<>(2);

        //查看该任务是否已经结束
        ReXuanshang xuanshang = reXuanshangDAO.selectByPrimaryKeyLock(xuanshangId);
        Integer totalNum = xuanshang.getTotalNum();
        Integer passNum = xuanshang.getPassNum();
        if (totalNum <= passNum){//该任务已经结束了
            dataResult.put("code",0);
            dataResult.put("msg","该任务已经结束");
        }else {
            //找到该详情记录
            ReXuanshangDetail detail = reXuanshangDetailDAO.selectByPrimaryKey(id);
            if (detail != null){
                Integer status = detail.getDetailStatus();
                //状态; 0:已提交,待审核,1:已通过,2:未通过
                if (status != 0){
                    dataResult.put("code",1);
                    dataResult.put("msg","该任务已经处理过了");
                }else {//审核通过逻辑
                    detail.setDetailStatus(1);
                    detail.setUpdateTime(CommonMethod.fmtTime(System.currentTimeMillis()));
                    reXuanshangDetailDAO.updateByPrimaryKeySelective(detail);

                    xuanshang.setUpdateTime(CommonMethod.fmtTime(System.currentTimeMillis()));
                    xuanshang.setPassNum(xuanshang.getPassNum() + 1);
                    reXuanshangDAO.updateByPrimaryKeySelective(xuanshang);

                    //金额相关表更新
                    BigDecimal money = xuanshang.getSingleMoney();
                    Integer userId = detail.getUserId();

                    ReUser user = reUserDAO.selectLockByUserId(userId);
                    user.setUserMoney(user.getUserMoney().add(money));
                    user.setTodayMoney(user.getTodayMoney().add(money));
                    user.setUpdateTime(System.currentTimeMillis());
                    reUserDAO.updateByPrimaryKeySelective(user);

                    // 删除缓存
                    userCacheService.removeUser(userId);


                    ReAccountDetail accountDetail = new ReAccountDetail();

                    accountDetail.setUserId(userId);
                    accountDetail.setAppId(user.getAppId());
                    accountDetail.setAccountMoney(money);
                    accountDetail.setDetailType(1);
                    accountDetail.setMissionType(MissionType.reward_mission.val);
                    accountDetail.setMissionSubtype(MissionSubtype.finish_reward.val);
                    accountDetail.setDetailContent("完成悬赏任务");
                    accountDetail.setDetailTime(CommonMethod.fmtTime(System.currentTimeMillis()));
                    reAccountDetailDAO.insert(accountDetail);

                    //若该用户通过之后,达到了最大任务数,则,把其余未处理的都置为来晚了.
                    if (totalNum == (passNum +1)){
                        //找到该xuanshangId所有提交的,状态是0的记录,统一改为3
                        List<ReXuanshangDetail> detailList = reXuanshangDetailDAO.getAllListNotVerify(xuanshangId,id);
                        for (ReXuanshangDetail detail1 : detailList){
                            detail1.setDetailStatus(3);
                            detail1.setUpdateTime(CommonMethod.fmtTime(System.currentTimeMillis()));
                            detail1.setReason("来晚了");
                            reXuanshangDetailDAO.updateByPrimaryKeySelective(detail1);
                        }
                        dataResult.put("code",3);
                        dataResult.put("msg","操作成功,且该悬赏任务已经完成");
                    }else {
                        dataResult.put("code",2);
                        dataResult.put("msg","操作成功");
                    }
                }
            }
        }
        return JsonUtil.buildSuccessDataJson(dataResult);
    }

    /**
     * 审核不通过
     * @param id            用户做的详情id
     * @param reason        未通过理由
     * @return
     */
    public String notPass(Long id,String reason){

        Map<String,Object> dataResult = new HashedMap(2);

        ReXuanshangDetail detail = reXuanshangDetailDAO.selectByPrimaryKey(id);
        detail.setReason(reason);
        detail.setUpdateTime(CommonMethod.fmtTime(System.currentTimeMillis()));
        detail.setDetailStatus(2);
        reXuanshangDetailDAO.updateByPrimaryKeySelective(detail);

        dataResult.put("code",1);
        dataResult.put("msg","操作成功");

        return JsonUtil.buildSuccessDataJson(dataResult);
    }

    /**
     * 我的审核列表
     * 发布任务者可以看到此列表
     * 该任务所有已经审核过的列表
     * @param xuanshangId   悬赏任务id
     * @param  id           当前页面的最后一个id
     * @return
     */
    public List<MyXuanshangListVo> getMyVerifiedList(Long xuanshangId, Long id){

        List<MyXuanshangListVo> vos = reXuanshangDetailDAO.getMyVerifiedList(xuanshangId,id);
        for (MyXuanshangListVo vo :vos){
            String missionImg = vo.getMissionImg();
            if (!StringUtil.isEmpty(missionImg)){
                List<String> list = FastJsonUtil.parseArray(missionImg,String.class);
                vo.setImgList(list);
            }
            Integer status = vo.getDetailStatus();
            String statusText = "";
            if (status == 1){
                statusText = "已通过";
            }
            if (status == 2){
                statusText = "未通过";
            }
            if (status == 3){
                statusText = "来晚了";
            }
            vo.setStatusText(statusText);
            ReXuanshangDetail detail = reXuanshangDetailDAO.selectByPrimaryKey(vo.getId());
            String submitTime = detail.getCreateTime();
            submitTime = getShowTime(submitTime);
            vo.setSubmitTime(submitTime);

        }
        return vos;
    }

    /**
     * 待审核用户列表（翻页）
     * @param xuanshangId   悬赏任务id
     * @param id            当前页面的最后一个待审核记录待id
     * @return
     */
    public List<UserToVerifyVo> getToVerifyList(Long xuanshangId,Long id){

        List<ReXuanshangDetail> verifyVos = reXuanshangDetailDAO.getToVerifyList(xuanshangId,id);
        List<UserToVerifyVo> toVerifyVos = new ArrayList<>(verifyVos.size());
        for (ReXuanshangDetail detail : verifyVos){
            toVerifyVos.add(turnReXuanshangDetailToUserToVerifyVo(detail));
        }
        return toVerifyVos;
    }

    /**
     * 悬赏任务id
     * @param userId        登录者id，未登录未null
     * @param xuanshagnId   悬赏任务的id
     * @return
     */
    public XuanshangDetailVo getXuanshangDetail(Integer userId,Long xuanshagnId){

        XuanshangDetailVo detailVo = new XuanshangDetailVo();
        ReXuanshang xuanshang = reXuanshangDAO.selectByPrimaryKey(xuanshagnId);
        ReUser user = reUserDAO.selectByPrimaryKey(xuanshang.getUserId());
        detailVo.setXuanshangId(xuanshagnId);
        detailVo.setPortrait(user.getPortrait());
        detailVo.setNickname(user.getNickname());
        detailVo.setMissionDesc(xuanshang.getMissionDesc());
        detailVo.setSingleMoney(xuanshang.getSingleMoney());
        detailVo.setCreateTime(getShowTime(xuanshang.getCreateTime()));
        String imgs = xuanshang.getMissionImg();
        if (!StringUtil.isEmpty(imgs)){
            List<String> imgList = FastJsonUtil.parseArray(imgs,String.class);
            detailVo.setImgList(imgList);
        }
        //查出该任务，已经审核通过的数据(轮播)
        List<ReXuanshangDetail> passDetails = reXuanshangDetailDAO.getPassList(xuanshagnId);
        List<UserPassXuanshangVo> passVos = new ArrayList<>(passDetails.size());
        List<String> rewardUserList = new ArrayList<>(passDetails.size());
        for (ReXuanshangDetail detail : passDetails){
            UserPassXuanshangVo vo = turnReXuanshangDetailToUserPassXuanshagnVo(detail);
            passVos.add(vo);
            rewardUserList.add(vo.getNickname()+"完成任务,赚到"+vo.getMoney()+"元");
        }
        detailVo.setRewardUserList(rewardUserList);
        detailVo.setPassList(passVos);

        boolean isEnd = isEnd(xuanshagnId);  

        if (userId == null){//未登录，不是自己发布的详情页面
//            detailVo.setIsShowButton(1);
            if (isEnd){
                detailVo.setButtonStatus(1);
                detailVo.setButtonTitle("已结束");
            }else {
                detailVo.setButtonStatus(0);
                detailVo.setButtonTitle("交作业");
            }

        }else {//登录

            if (userId == xuanshang.getUserId()){//自己发布的
//                detailVo.setIsShowButton(0);
                Integer passNum = xuanshang.getPassNum();
                Integer totalNum = xuanshang.getTotalNum();
                if (passNum >= totalNum){
                    detailVo.setIsComplete(1);
                }else {
                    detailVo.setIsComplete(0);
                }
                detailVo.setPassNum(passNum);
                Integer notPassNum = reXuanshangDetailDAO.getNotPassNum(xuanshagnId);
                detailVo.setNotPassNum(notPassNum);
                Integer toVerifyNum = reXuanshangDetailDAO.getToVerifyNum(xuanshagnId);
                detailVo.setToVerifyNum(toVerifyNum);
                detailVo.setNumDesc("已通过: "+xuanshang.getPassNum()+" 未通过: "+notPassNum+" 待审核: "+toVerifyNum);

                //列出待我审核的列表
                List<ReXuanshangDetail> verifyVos = reXuanshangDetailDAO.getToVerifyList(xuanshagnId,null);
                List<UserToVerifyVo> toVerifyVos = new ArrayList<>(verifyVos.size());
                for (ReXuanshangDetail detail : verifyVos){
                    toVerifyVos.add(turnReXuanshangDetailToUserToVerifyVo(detail));
                }
                detailVo.setToVerifyList(toVerifyVos);

            }else {//不是自己发布的

                boolean isPartIn = isPartIn(userId,xuanshagnId);
                if (isPartIn){//此用户已经参与过该悬赏任务
//                    detailVo.setIsShowButton(0);
                    ReXuanshangDetail detail = reXuanshangDetailDAO.getDetailByUserId(xuanshagnId,userId);

                    MyXuanshangListVo vo = new MyXuanshangListVo();
                    vo.setId(detail.getId());
                    vo.setMissionText(detail.getMissionText());
                    ReUser submitUser = reUserDAO.selectByPrimaryKey(detail.getUserId());
                    vo.setPortrait(submitUser.getPortrait());
                    vo.setNickname(submitUser.getNickname());
                    String submitTime = detail.getCreateTime();
                    submitTime = getShowTime(submitTime);
                    vo.setSubmitTime(submitTime);
                    String submitImgs = detail.getMissionImg();
                    if (!StringUtil.isEmpty(submitImgs)){
                        List<String> imgList = FastJsonUtil.parseArray(submitImgs,String.class);
                        vo.setImgList(imgList);
                    }
                    Integer status = detail.getDetailStatus();
                    String statusText = "";
                    if (status == 1){
                        statusText = "已通过";
                    }
                    if (status == 2){
                        statusText = "未通过";
                    }
                    if (status == 3){
                        statusText = "来晚了";
                    }
                    if (status == 0){
                        statusText = "待审核";
                    }
                    vo.setStatusText(statusText);
                    vo.setReason(detail.getReason());
                    vo.setDetailStatus(status);
                    detailVo.setMySubmit(vo);

//                    detailVo.setIsShowButton(1);
                    detailVo.setButtonTitle("已提交,请去我的任务查看");
                    detailVo.setButtonStatus(2);

                }else {
//                    detailVo.setIsShowButton(1);
                    if (isEnd){
                        detailVo.setButtonStatus(1);
                        detailVo.setButtonTitle("已结束");
                    }else {
                        detailVo.setButtonStatus(0);
                        detailVo.setButtonTitle("交作业");
                    }
                }
            }
        }
        return detailVo;
    }

    /**
     * 实体类转换
     * @param detail
     * @return
     */
    public UserPassXuanshangVo turnReXuanshangDetailToUserPassXuanshagnVo(ReXuanshangDetail detail){

        UserPassXuanshangVo vo = new UserPassXuanshangVo();
        Integer userId = detail.getUserId();
        ReUser user = reUserDAO.selectByPrimaryKey(userId);
        vo.setNickname(user.getNickname());
        ReXuanshang xuanshang = reXuanshangDAO.selectByPrimaryKey(detail.getXuanshangId());
        vo.setMoney(xuanshang.getSingleMoney());

        return vo;
    }

    /**
     * 实体类转换
     * @param detail
     * @return
     */
    public UserToVerifyVo turnReXuanshangDetailToUserToVerifyVo(ReXuanshangDetail detail){

        UserToVerifyVo vo = new UserToVerifyVo();
        vo.setId(detail.getId());
        vo.setMissionText(detail.getMissionText());
        ReUser user = reUserDAO.selectByPrimaryKey(detail.getUserId());
        vo.setPortrait(user.getPortrait());
        vo.setNickname(user.getNickname());
        String submitImgs = detail.getMissionImg();
        if (!StringUtil.isEmpty(submitImgs)){
            List<String> imgList = FastJsonUtil.parseArray(submitImgs,String.class);
            vo.setImgList(imgList);
        }
        String submitTime = detail.getCreateTime();
        submitTime = getShowTime(submitTime);
        vo.setSubmitTime(submitTime);

        return vo;
    }

    /**
     * 该任务是否结束
     * @param xuanshangId
     * @return
     */
    public Boolean isEnd(Long xuanshangId){
        ReXuanshang xuanshang = reXuanshangDAO.selectByPrimaryKey(xuanshangId);
        if (xuanshang.getPassNum() == xuanshang.getTotalNum()){
            return true;
        }
        return false;
    }

    /**
     * 判断此人是是否参与过此悬赏任务
     * @param userId            参与人id
     * @param xuanshangId       悬赏任务id
     * @return
     */
    public Boolean isPartIn(Integer userId,Long xuanshangId){
        ReXuanshangDetail detail = reXuanshangDetailDAO.getDetailByUserId(xuanshangId,userId);
        if (detail == null){
            return false;
        }
        return true;
    }


    /**
     * 删除涉及违规的
     * 管理员删除
     * @param xuanshangId   悬赏任务
     * @param userId        管理员id
     * @return
     */
    public String deleteXuanshang(Long xuanshangId,Integer userId){

        Map<String ,Object> dataResult = new HashMap<>(2);
        ReUser user = reUserDAO.selectByPrimaryKey(userId);
        if (user.getUserType() == 1){
            ReXuanshang xuanshang = reXuanshangDAO.selectByPrimaryKeyLock(xuanshangId);
            if (xuanshang != null){

                deleteXuanshangAndDetails(xuanshangId);

                dataResult.put("code",1);
                dataResult.put("msg","删除成功");
            }
        }else {
            dataResult.put("code",0);
            dataResult.put("msg","你无权删除");
        }

        return JsonUtil.buildSuccessDataJson(dataResult);
    }

    /**
     * 删除该用户发布的悬赏任务及提交的记录
     * @param xuanshangId
     */
    public void deleteXuanshangAndDetails(Long xuanshangId){
        reXuanshangDAO.deleteByPrimaryKey(xuanshangId);
        reXuanshangDetailDAO.deleteAllDetailsByXuanshangId(xuanshangId);
    }


    /**
     * 把该用户拉入黑名单
     * @param xuanshangId   悬赏任务
     * @param userId        管理员id
     * @return
     */
    public String pushToBlack(Long xuanshangId,Integer userId){

        Map<String ,Object> dataResult = new HashMap<>(2);
        ReUser user = reUserDAO.selectByPrimaryKey(userId);

        if (user.getUserType() == 1){
            ReXuanshang xuanshang = reXuanshangDAO.selectByPrimaryKeyLock(xuanshangId);
            if (xuanshang != null){

                deleteXuanshangAndDetails(xuanshangId);
                Integer blackUserId = xuanshang.getUserId();
                userService.pushToBlack(blackUserId);
                dataResult.put("code",1);
                dataResult.put("msg","删除成功");
            }
        }else {
            dataResult.put("code",0);
            dataResult.put("msg","你无权操作");
        }

        return JsonUtil.buildSuccessDataJson(dataResult);
    }


}
