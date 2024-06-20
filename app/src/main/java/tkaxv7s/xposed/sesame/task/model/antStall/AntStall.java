package tkaxv7s.xposed.sesame.task.model.antStall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import tkaxv7s.xposed.sesame.data.ModelFields;
import tkaxv7s.xposed.sesame.data.modelFieldExt.BooleanModelField;
import tkaxv7s.xposed.sesame.data.modelFieldExt.SelectModelField;
import tkaxv7s.xposed.sesame.data.modelFieldExt.IntegerModelField;
import tkaxv7s.xposed.sesame.entity.AlipayUser;
import tkaxv7s.xposed.sesame.entity.KVNode;
import tkaxv7s.xposed.sesame.task.common.ModelTask;
import tkaxv7s.xposed.sesame.task.common.TaskCommon;
import tkaxv7s.xposed.sesame.task.model.readingDada.ReadingDada;
import tkaxv7s.xposed.sesame.util.Log;
import tkaxv7s.xposed.sesame.util.Statistics;
import tkaxv7s.xposed.sesame.util.UserIdMap;

/**
 * @author Constanline
 * @since 2023/08/22
 */
public class AntStall extends ModelTask {
    private static final String TAG = AntStall.class.getSimpleName();

    private static class Seat {
        public String userId;
        public int hot;

        public Seat(String userId, int hot) {
            this.userId = userId;
            this.hot = hot;
        }
    }

    private static final List<String> taskTypeList;

    static {
        taskTypeList = new ArrayList<>();
        taskTypeList.add("ANTSTALL_NORMAL_OPEN_NOTICE");// 开启收新村收益提醒
        taskTypeList.add("tianjiashouye");// 添加首页
        taskTypeList.add("SHANGYEHUA_ceshi");// 【木兰市集】逛精选好物
        taskTypeList.add("ANTSTALL_ELEME_VISIT");// 去饿了么果园逛一逛
        taskTypeList.add("ANTSTALL_TASK_diantao202311");// 去点淘赚元宝提现
    }

    @Override
    public String setName() {
        return "新村";
    }
    public BooleanModelField enableStall;
    public BooleanModelField stallAutoClose;
    public BooleanModelField stallAutoOpen;
    public BooleanModelField stallAutoTask;
    public BooleanModelField stallReceiveAward;
    public BooleanModelField stallOpenType;
    public SelectModelField stallOpenList;
    public SelectModelField stallWhiteList;
    public SelectModelField stallBlackList;
    public IntegerModelField stallAllowOpenTime;
    public IntegerModelField stallSelfOpenTime;
    public BooleanModelField stallDonate;
    public BooleanModelField stallInviteRegister;
    public BooleanModelField stallThrowManure;
    public SelectModelField stallInviteShopList;

    @Override
    public ModelFields setFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(enableStall = new BooleanModelField("enableStall", "开启新村", false));
        modelFields.addField(stallAutoOpen = new BooleanModelField("stallAutoOpen", "新村自动摆摊", false));
        modelFields.addField(stallAutoClose = new BooleanModelField("stallAutoClose", "新村自动收摊", false));
        modelFields.addField(stallAutoTask = new BooleanModelField("stallAutoTask", "新村自动任务", false));
        modelFields.addField(stallReceiveAward = new BooleanModelField("stallReceiveAward", "新村自动领奖", false));
        modelFields.addField(stallOpenType = new BooleanModelField("stallOpenType", "摊位类型(打开:摆摊列表/关闭:不摆列表)", false));
        modelFields.addField(stallOpenList = new SelectModelField("stallOpenList", "摊位类型(打开:摆摊列表/关闭:不摆列表)", new KVNode<>(new LinkedHashMap<>(), false), AlipayUser.getList()));
        modelFields.addField(stallWhiteList = new SelectModelField("stallWhiteList", "不请走列表", new KVNode<>(new LinkedHashMap<>(), false), AlipayUser.getList()));
        modelFields.addField(stallBlackList = new SelectModelField("stallBlackList", "禁摆摊列表", new KVNode<>(new LinkedHashMap<>(), false), AlipayUser.getList()));
        modelFields.addField(stallAllowOpenTime = new IntegerModelField("stallAllowOpenTime", "允许他人摆摊时长", 121));
        modelFields.addField(stallSelfOpenTime = new IntegerModelField("stallSelfOpenTime", "自己收摊时长", 120));
        modelFields.addField(stallDonate = new BooleanModelField("stallDonate", "新村自动捐赠", false));
        modelFields.addField(stallInviteRegister = new BooleanModelField("stallInviteRegister", "邀请好友开通新村", false));
        modelFields.addField(stallThrowManure = new BooleanModelField("stallThrowManure", "新村丢肥料", false));
        modelFields.addField(stallInviteShopList = new SelectModelField("stallInviteShopList", "新村邀请摆摊列表", new KVNode<>(new LinkedHashMap<>(), false), AlipayUser.getList()));
        return modelFields;
    }

    public Boolean check() {
        return enableStall.getValue() && !TaskCommon.IS_ENERGY_TIME;
    }

    public Runnable init() {
        return () -> {
            String s = AntStallRpcCall.home();
            try {
                JSONObject jo = new JSONObject(s);
                if ("SUCCESS".equals(jo.getString("resultCode"))) {
                    if (!jo.getBoolean("hasRegister") || jo.getBoolean("hasQuit")) {
                        Log.farm("蚂蚁新村⛪请先开启蚂蚁新村");
                        return;
                    }

                    JSONObject astReceivableCoinVO = jo.getJSONObject("astReceivableCoinVO");
                    if (astReceivableCoinVO.optBoolean("hasCoin")) {
                        settleReceivable();
                    }

                    if (stallThrowManure.getValue()) {
                        throwManure();
                    }

                    JSONObject seatsMap = jo.getJSONObject("seatsMap");
                    settle(seatsMap);

                    collectManure();

                    sendBack(seatsMap);

                    if (stallAutoClose.getValue()) {
                        closeShop();
                    }

                    if (stallAutoOpen.getValue()) {
                        openShop();
                    }


                    if (stallAutoTask.getValue()) {
                        taskList();
                    }
                    achieveBeShareP2P();

                    if (stallDonate.getValue()) {
                        roadmap();
                    }

                } else {
                    Log.record("home err:" + " " + s);
                }
            } catch (Throwable t) {
                Log.i(TAG, "home err:");
                Log.printStackTrace(TAG, t);
            }
        };
    }

    private void sendBack(String billNo, String seatId, String shopId, String shopUserId) {
        String s = AntStallRpcCall.shopSendBackPre(billNo, seatId, shopId, shopUserId);
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONObject astPreviewShopSettleVO = jo.getJSONObject("astPreviewShopSettleVO");
                JSONObject income = astPreviewShopSettleVO.getJSONObject("income");
                int amount = (int) income.getDouble("amount");
                s = AntStallRpcCall.shopSendBack(seatId);
                jo = new JSONObject(s);
                if ("SUCCESS".equals(jo.getString("resultCode"))) {
                    Log.farm("蚂蚁新村⛪请走[" + UserIdMap.getNameById(shopUserId) + "]的小摊"
                            + (amount > 0 ? "获得金币" + amount : ""));
                } else {
                    Log.record("sendBack err:" + " " + s);
                }
                inviteOpen(seatId);
            } else {
                Log.record("sendBackPre err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "sendBack err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void inviteOpen(String seatId) {
        String s = AntStallRpcCall.rankInviteOpen();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray friendRankList = jo.getJSONArray("friendRankList");
                for (int i = 0; i < friendRankList.length(); i++) {
                    JSONObject friend = friendRankList.getJSONObject(i);
                    String friendUserId = friend.getString("userId");
                    if (!stallInviteShopList.getValue().getKey().containsKey(friendUserId)) {
                        continue;
                    }
                    if (friend.getBoolean("canInviteOpenShop")) {
                        s = AntStallRpcCall.oneKeyInviteOpenShop(friendUserId, seatId);
                        jo = new JSONObject(s);
                        if ("SUCCESS".equals(jo.getString("resultCode"))) {
                            Log.farm("蚂蚁新村⛪邀请[" + UserIdMap.getNameById(friendUserId) + "]开店成功");
                            return;
                        }
                    }
                }
            } else {
                Log.record("inviteOpen err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "inviteOpen err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void sendBack(JSONObject seatsMap) {
        try {
            for (int i = 1; i <= 2; i++) {
                JSONObject seat = seatsMap.getJSONObject("GUEST_0" + i);
                String seatId = seat.getString("seatId");
                if ("FREE".equals(seat.getString("status"))) {
                    inviteOpen(seatId);
                    continue;
                }
                String rentLastUser = seat.getString("rentLastUser");
                // 白名单直接跳过
                if (stallWhiteList.getValue().getKey().containsKey(rentLastUser)) {
                    continue;
                }
                String rentLastBill = seat.getString("rentLastBill");
                String rentLastShop = seat.getString("rentLastShop");
                // 黑名单直接赶走
                if (stallBlackList.getValue().getKey().containsKey(rentLastUser)) {
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser);
                    continue;
                }
                long bizStartTime = seat.getLong("bizStartTime");
                if ((System.currentTimeMillis() - bizStartTime) / 1000 / 60 > stallAllowOpenTime.getValue()) {
                    sendBack(rentLastBill, seatId, rentLastShop, rentLastUser);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "sendBack err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void settle(JSONObject seatsMap) {
        try {
            JSONObject seat = seatsMap.getJSONObject("MASTER");
            if (seat.has("coinsMap")) {
                JSONObject coinsMap = seat.getJSONObject("coinsMap");
                JSONObject master = coinsMap.getJSONObject("MASTER");
                String assetId = master.getString("assetId");
                int settleCoin = (int) (master.getJSONObject("money").getDouble("amount"));
                boolean fullShow = master.getBoolean("fullShow");
                if (fullShow || settleCoin > 100) {
                    String s = AntStallRpcCall.settle(assetId, settleCoin);
                    JSONObject jo = new JSONObject(s);
                    if (jo.getString("resultCode").equals("SUCCESS")) {
                        Log.farm("蚂蚁新村⛪[收取金币]#" + settleCoin);
                    } else {
                        Log.record("settle err:" + " " + s);
                    }
                }
            }

        } catch (Throwable t) {
            Log.i(TAG, "settle err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void closeShop() {
        String s = AntStallRpcCall.shopList();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray astUserShopList = jo.getJSONArray("astUserShopList");
                for (int i = 0; i < astUserShopList.length(); i++) {
                    JSONObject shop = astUserShopList.getJSONObject(i);
                    if ("OPEN".equals(shop.getString("status"))) {
                        JSONObject rentLastEnv = shop.getJSONObject("rentLastEnv");
                        long gmtLastRent = rentLastEnv.getLong("gmtLastRent");
                        if (System.currentTimeMillis() - gmtLastRent > (long) stallSelfOpenTime.getValue() * 60 * 1000) {
                            String shopId = shop.getString("shopId");
                            String rentLastBill = shop.getString("rentLastBill");
                            String rentLastUser = shop.getString("rentLastUser");
                            shopClose(shopId, rentLastBill, rentLastUser);
                        }
                    }
                }
            } else {
                Log.record("closeShop err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "closeShop err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void openShop() {
        String s = AntStallRpcCall.shopList();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray astUserShopList = jo.getJSONArray("astUserShopList");
                Queue<String> shopIds = new LinkedList<>();
                for (int i = 0; i < astUserShopList.length(); i++) {
                    JSONObject astUserShop = astUserShopList.getJSONObject(i);
                    if ("FREE".equals(astUserShop.getString("status"))) {
                        shopIds.add(astUserShop.getString("shopId"));
                    }
                }
                rankCoinDonate(shopIds);
            } else {
                Log.record("closeShop err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "closeShop err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void rankCoinDonate(Queue<String> shopIds) {
        String s = AntStallRpcCall.rankCoinDonate();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray friendRankList = jo.getJSONArray("friendRankList");
                List<Seat> seats = new ArrayList<>();
                for (int i = 0; i < friendRankList.length(); i++) {
                    JSONObject friendRank = friendRankList.getJSONObject(i);
                    if (friendRank.getBoolean("canOpenShop")) {
                        String userId = friendRank.getString("userId");
                        Map<String, Integer> map = stallOpenList.getValue().getKey();
                        if (stallOpenType.getValue()) {
                            if (!map.containsKey(userId)) {
                                continue;
                            }
                        } else if (map.containsKey(userId)) {
                            continue;
                        }
                        int hot = friendRank.getInt("hot");
                        seats.add(new Seat(userId, hot));
                    }
                }
                friendHomeOpen(seats, shopIds);
            } else {
                Log.record("rankCoinDonate err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "rankCoinDonate err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void openShop(String seatId, String userId, Queue<String> shopIds) {
        String shopId = shopIds.peek();
        String s = AntStallRpcCall.shopOpen(seatId, userId, shopId);
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                Log.farm("蚂蚁新村⛪在[" + UserIdMap.getNameById(userId) + "]家摆摊");
                shopIds.poll();
            }
        } catch (Throwable t) {
            Log.i(TAG, "openShop err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void friendHomeOpen(List<Seat> seats, Queue<String> shopIds) {
        Collections.sort(seats, (e1, e2) -> e2.hot - e1.hot);
        int idx = 0;
        while (seats.size() > idx && !shopIds.isEmpty()) {
            Seat seat = seats.get(idx);
            String userId = seat.userId;
            String s = AntStallRpcCall.friendHome(userId);
            try {
                JSONObject jo = new JSONObject(s);
                if ("SUCCESS".equals(jo.getString("resultCode"))) {
                    JSONObject seatsMap = jo.getJSONObject("seatsMap");
                    JSONObject guest = seatsMap.getJSONObject("GUEST_01");
                    if (guest.getBoolean("canOpenShop")) {
                        openShop(guest.getString("seatId"), userId, shopIds);
                    } else {
                        guest = seatsMap.getJSONObject("GUEST_02");
                        if (guest.getBoolean("canOpenShop")) {
                            openShop(guest.getString("seatId"), userId, shopIds);
                        }
                    }
                } else {
                    Log.record("friendHomeOpen err:" + " " + s);
                }
            } catch (Throwable t) {
                Log.i(TAG, "friendHomeOpen err:");
                Log.printStackTrace(TAG, t);
            }
            idx++;
        }
    }

    private static void shopClose(String shopId, String billNo, String userId) {
        String s = AntStallRpcCall.preShopClose(shopId, billNo);
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONObject income = jo.getJSONObject("astPreviewShopSettleVO").getJSONObject("income");
                s = AntStallRpcCall.shopClose(shopId);
                jo = new JSONObject(s);
                if ("SUCCESS".equals(jo.getString("resultCode"))) {
                    Log.farm("蚂蚁新村⛪收取在[" + UserIdMap.getNameById(userId) + "]的摊位获得" + income.getString("amount"));
                } else {
                    Log.record("shopClose err:" + " " + s);
                }
            } else {
                Log.record("shopClose  err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "shopClose  err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void taskList() {
        try {
            do {
                String s = AntStallRpcCall.taskList();
                JSONObject jo = new JSONObject(s);
                if ("SUCCESS".equals(jo.getString("resultCode"))) {
                    JSONObject signListModel = jo.getJSONObject("signListModel");
                    if (!signListModel.getBoolean("currentKeySigned")) {
                        signToday();
                    }

                    JSONArray taskModels = jo.getJSONArray("taskModels");
                    for (int i = 0; i < taskModels.length(); i++) {
                        JSONObject task = taskModels.getJSONObject(i);
                        String taskStatus = task.getString("taskStatus");
                        if ("FINISHED".equals(taskStatus)) {
                            receiveTaskAward(task.getString("taskType"));
                        } else if ("TODO".equals(taskStatus)) {
                            JSONObject bizInfo = new JSONObject(task.getString("bizInfo"));
                            String taskType = task.getString("taskType");
                            String title = bizInfo.optString("title", taskType);
                            if ("VISIT_AUTO_FINISH".equals(bizInfo.getString("actionType"))
                                    || taskTypeList.contains(taskType)) {
                                if (finishTask(taskType)) {
                                    Log.farm("蚂蚁新村⛪[完成任务]#" + title);
                                    Thread.sleep(200L);
                                    continue;
                                }
                            } else if ("ANTSTALL_NORMAL_DAILY_QA".equals(taskType)) {
                                if (ReadingDada.answerQuestion(bizInfo)) {
                                    receiveTaskAward(taskType);
                                }
                            } else if ("ANTSTALL_NORMAL_INVITE_REGISTER".equals(taskType)) {
                                if (inviteRegister()) {
                                    Thread.sleep(200L);
                                    continue;
                                }
                            } else if ("ANTSTALL_P2P_DAILY_SHARER".equals(taskType)) {
                                shareP2P();
                            }
                        }
                        Thread.sleep(200L);
                    }
                } else {
                    Log.record("taskList err:" + " " + s);
                }
                break;
            } while (true);
        } catch (Throwable t) {
            Log.i(TAG, "taskList err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void signToday() {
        String s = AntStallRpcCall.signToday();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                Log.farm("蚂蚁新村⛪[签到成功]");
            } else {
                Log.record("signToday err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "signToday err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private void receiveTaskAward(String taskType) {
        if (!stallReceiveAward.getValue()) {
            return;
        }
        String s = AntStallRpcCall.receiveTaskAward(taskType);
        try {
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                Log.farm("蚂蚁新村⛪[领取奖励]");
            } else {
                Log.record("receiveTaskAward err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "receiveTaskAward err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static boolean finishTask(String taskType) {
        // String s = AntStallRpcCall.finishTask(FriendIdMap.currentUid + "_" +
        // taskType, taskType);
        String s = AntStallRpcCall.finishTask(taskType + "_" + System.currentTimeMillis(), taskType);
        try {
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                return true;
            } else {
                Log.record("finishTask err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "finishTask err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private boolean inviteRegister() {
        if (!stallInviteRegister.getValue()) {
            return false;
        }
        try {
            String s = AntStallRpcCall.rankInviteRegister();
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray friendRankList = jo.optJSONArray("friendRankList");
                if (friendRankList != null && friendRankList.length() > 0) {
                    for (int i = 0; i < friendRankList.length(); i++) {
                        JSONObject friend = friendRankList.getJSONObject(i);
                        if (friend.optBoolean("canInviteRegister", false)
                                && "UNREGISTER".equals(friend.getString("userStatus"))) {/* 是否加名单筛选 */
                            String userId = friend.getString("userId");
                            jo = new JSONObject(AntStallRpcCall.friendInviteRegister(userId));
                            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                                Log.farm("邀请好友[" + UserIdMap.getNameById(userId) + "]#开通新村");
                                return true;
                            } else {
                                Log.record("friendInviteRegister err:" + " " + jo);
                            }
                        }
                    }
                }
            } else {
                Log.record("rankInviteRegister err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "InviteRegister err:");
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private static void shareP2P() {
        try {
            String s = AntStallRpcCall.shareP2P();
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                String shareId = jo.getString("shareId");
                /* 保存shareId到Statistics */
                Statistics.stallShareIdToday(UserIdMap.getCurrentUid(), shareId);
                Log.record("蚂蚁新村⛪[分享助力]");
            } else {
                Log.record("shareP2P err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "shareP2P err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void achieveBeShareP2P() {
        try {
            if (!Statistics.canStallHelpToday(UserIdMap.getCurrentUid()))
                return;
            List<String> UserIdList = Statistics.stallP2PUserIdList(UserIdMap.getCurrentUid());
            for (String uid : UserIdList) {
                if (Statistics.canStallBeHelpToday(uid)) {
                    String shareId = Statistics.getStallShareId(uid);
                    if (shareId != null && Statistics.canStallP2PHelpToday(uid)) {
                        String s = AntStallRpcCall.achieveBeShareP2P(shareId);
                        JSONObject jo = new JSONObject(s);
                        if (jo.getBoolean("success")) {
                            Log.farm("新村助力🎈[" + UserIdMap.getNameById(uid) + "]");
                            Statistics.stallHelpToday(UserIdMap.getCurrentUid(), false);
                            Statistics.stallBeHelpToday(uid, false);
                            Statistics.stallP2PHelpeToday(uid);
                        } else if ("600000028".equals(jo.getString("code"))) {
                            Statistics.stallBeHelpToday(uid, true);
                            Log.record("被助力次数上限:" + " " + uid);
                        } else if ("600000027".equals(jo.getString("code"))) {
                            Statistics.stallHelpToday(UserIdMap.getCurrentUid(), true);
                            Log.record("助力他人次数上限:" + " " + UserIdMap.getCurrentUid());
                        } else {
                            Log.record("achieveBeShareP2P err:" + " " + s);
                        }
                        Thread.sleep(3500L);
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "achieveBeShareP2P err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void donate() {
        String s = AntStallRpcCall.projectList();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray astProjectVOS = jo.getJSONArray("astProjectVOS");
                for (int i = 0; i < astProjectVOS.length(); i++) {
                    JSONObject project = astProjectVOS.getJSONObject(i);
                    if ("ONLINE".equals(project.getString("status"))) {
                        String projectId = project.getString("projectId");
                        s = AntStallRpcCall.projectDetail(projectId);
                        JSONObject joProjectDetail = new JSONObject(s);
                        if ("SUCCESS".equals(joProjectDetail.getString("resultCode"))) {
                            s = AntStallRpcCall.projectDonate(projectId);
                            JSONObject joProjectDonate = new JSONObject(s);
                            if ("SUCCESS".equals(joProjectDonate.getString("resultCode"))) {
                                JSONObject astUserVillageVO = joProjectDonate.getJSONObject("astUserVillageVO");
                                if (astUserVillageVO.getInt("donateCount") >= astUserVillageVO.getInt("donateLimit")) {
                                    roadmap();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "donate err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void roadmap() {
        String s = AntStallRpcCall.roadmap();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONObject userInfo = jo.getJSONObject("userInfo");
                JSONObject currentCoin = userInfo.getJSONObject("currentCoin");
                int amount = (int) currentCoin.getDouble("amount");
                if (amount < 15000) {
                    return;
                }
                JSONArray roadList = jo.getJSONArray("roadList");
                boolean unFinished = false;
                boolean canNext = false;
                for (int i = 0; i < roadList.length(); i++) {
                    JSONObject road = roadList.getJSONObject(i);
                    if ("FINISHED".equals(road.getString("status"))) {
                        continue;
                    }
                    if ("LOCK".equals(road.getString("status"))) {
                        canNext = true;
                        break;
                    }
                    if (road.getInt("donateCount") < road.getInt("donateLimit")) {
                        unFinished = true;
                    }
                }
                if (unFinished) {
                    donate();
                } else if (canNext) {
                    s = AntStallRpcCall.nextVillage();
                    jo = new JSONObject(s);
                    if ("SUCCESS".equals(jo.getString("resultCode"))) {
                        Log.farm("蚂蚁新村⛪进入下一村成功");
                    }
                }
            } else {
                Log.record("roadmap err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "roadmap err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void collectManure() {
        String s = AntStallRpcCall.queryManureInfo();
        try {
            JSONObject jo = new JSONObject(s);
            if (jo.getBoolean("success")) {
                JSONObject astManureInfoVO = jo.getJSONObject("astManureInfoVO");
                if (astManureInfoVO.optBoolean("hasManure")) {
                    int manure = astManureInfoVO.getInt("manure");
                    s = AntStallRpcCall.collectManure();
                    jo = new JSONObject(s);
                    if ("SUCCESS".equals(jo.getString("resultCode"))) {
                        Log.farm("蚂蚁新村⛪获得肥料" + manure + "g");
                    }
                }
            } else {
                Log.record("collectManure err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "collectManure err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void throwManure(JSONArray dynamicList) {
        String s = AntStallRpcCall.throwManure(dynamicList);
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                Log.farm("蚂蚁新村⛪扔肥料");
            }
        } catch (Throwable th) {
            Log.i(TAG, "throwManure err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private static void throwManure() {
        String s = AntStallRpcCall.dynamicLoss();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                JSONArray astLossDynamicVOS = jo.getJSONArray("astLossDynamicVOS");
                JSONArray dynamicList = new JSONArray();
                for (int i = 0; i < astLossDynamicVOS.length(); i++) {
                    JSONObject lossDynamic = astLossDynamicVOS.getJSONObject(i);
                    if (lossDynamic.has("specialEmojiVO")) {
                        continue;
                    }
                    JSONObject dynamic = new JSONObject();
                    dynamic.put("bizId", lossDynamic.getString("bizId"));
                    dynamic.put("bizType", lossDynamic.getString("bizType"));
                    dynamicList.put(dynamic);
                    if (dynamicList.length() == 5) {
                        throwManure(dynamicList);
                        dynamicList = new JSONArray();
                    }
                }
                if (dynamicList.length() > 0) {
                    throwManure(dynamicList);
                }
            } else {
                Log.record("throwManure err:" + " " + s);
            }
        } catch (Throwable t) {
            Log.i(TAG, "throwManure err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void settleReceivable() {
        String s = AntStallRpcCall.settleReceivable();
        try {
            JSONObject jo = new JSONObject(s);
            if ("SUCCESS".equals(jo.getString("resultCode"))) {
                Log.farm("蚂蚁新村⛪收取应收金币");
            }
        } catch (Throwable th) {
            Log.i(TAG, "settleReceivable err:");
            Log.printStackTrace(TAG, th);
        }
    }
}