package io.github.hzhilong.bilibili.backup.app.service.impl;

import com.alibaba.fastjson.JSONObject;
import io.github.hzhilong.base.error.BusinessException;
import io.github.hzhilong.base.utils.ListUtil;
import io.github.hzhilong.bilibili.backup.api.bean.ApiResult;
import io.github.hzhilong.bilibili.backup.api.bean.FavFolder;
import io.github.hzhilong.bilibili.backup.api.bean.Media;
import io.github.hzhilong.bilibili.backup.api.bean.page.FavPageData;
import io.github.hzhilong.bilibili.backup.api.request.AddQueryParams;
import io.github.hzhilong.bilibili.backup.api.request.BaseApi;
import io.github.hzhilong.bilibili.backup.api.request.ListApi;
import io.github.hzhilong.bilibili.backup.api.request.ModifyApi;
import io.github.hzhilong.bilibili.backup.api.request.PageApi;
import io.github.hzhilong.bilibili.backup.api.user.User;
import io.github.hzhilong.bilibili.backup.app.bean.BackupRestoreResult;
import io.github.hzhilong.bilibili.backup.app.business.BusinessType;
import io.github.hzhilong.bilibili.backup.app.service.BackupRestoreService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收藏夹
 *
 * @author hzhilong
 * @version 1.0
 */
@Slf4j
public class FavoritesService extends BackupRestoreService<FavFolder> {

    private PageApi<FavPageData, Media> pageApi;

    public FavoritesService(OkHttpClient client, User user, String path) {
        super(client, user, path);
    }

    private List<FavFolder> getFavFolders() throws BusinessException {
        return new ListApi<>(client, signUser(),
                "https://api.bilibili.com/x/v3/fav/folder/created/list-all",
                queryParams -> {
                    queryParams.put("up_mid", user.getUid());
                }, FavFolder.class).getList();
    }

    public FavPageData getFavData(Long mediaId) throws BusinessException {
        return getFavData(String.valueOf(mediaId));
    }

    public FavPageData getFavData(String mediaId) throws BusinessException {
        pageApi = new PageApi<>(client, signUser(), "https://api.bilibili.com/x/v3/fav/resource/list",
                queryParams -> {
                    queryParams.put("media_id", mediaId);
                    queryParams.put("order", "mtime");
                    queryParams.put("type", "0");
                    queryParams.put("platform", "web");
                    queryParams.put("ps", "40");
                },
                FavPageData.class, Media.class);
        return pageApi.getAllPageData();
    }

    private String getOldFileName(FavFolder favFolder) {
        return favFolder.getTitle() + "-" + favFolder.getId();
    }

    private String getFileName(FavFolder favFolder) {
//        return favFolder.getTitle() + "-" + favFolder.getId();
        return String.valueOf(favFolder.getId());
    }

    @Override
    public List<BackupRestoreResult<List<FavFolder>>> backup() throws BusinessException {
        BackupRestoreResult<List<FavFolder>> folderResult = backupData("收藏夹", "创建的收藏夹",
                new BackupCallback<List<FavFolder>>() {
                    @Override
                    public List<FavFolder> getData() throws BusinessException {
                        return getFavFolders();
                    }

                    @Override
                    public List<FavFolder> processData(List<FavFolder> list) throws BusinessException {
                        for (FavFolder favFolder : list) {
                            log.info("获取收藏夹[{}]的信息", favFolder.getTitle());
                            ApiResult<FavFolder> apiResult = new BaseApi<FavFolder>(client, signUser(),
                                    "https://api.bilibili.com/x/v3/fav/folder/info",
                                    new AddQueryParams() {
                                        @Override
                                        public void addQueryParams(Map<String, String> queryParams) {
                                            queryParams.put("media_id", String.valueOf(favFolder.getId()));
                                        }
                                    }, true, FavFolder.class).apiGet();
                            if (apiResult.isSuccess()) {
                                FavFolder favFolderInfo = apiResult.getData();
                                favFolder.setIntro(favFolderInfo.getIntro());
                                favFolder.setCover(favFolderInfo.getCover());
                                log.info("收藏夹[{}]的视频数：{}", favFolder.getTitle(), favFolder.getMediaCount());
                            }
                        }
                        return list;
                    }
                });
        if (ListUtil.isEmpty(folderResult.getData())) {
            return createResults(folderResult);
        }
        List<FavFolder> favFolders = folderResult.getData();
        List<BackupRestoreResult<List<FavFolder>>> results = new ArrayList<>(favFolders.size());
        for (FavFolder favFolder : favFolders) {
            BackupRestoreResult<List<FavFolder>> tempResult = new BackupRestoreResult<>();
            tempResult.setItemName(folderResult.getItemName());
            tempResult.setBusinessType(folderResult.getBusinessType());
            tempResult.setData(Collections.singletonList(favFolder));
            String title = favFolder.getTitle();
            try {
                log.info("正在备份收藏夹[{}]...", title);
                BackupRestoreResult<FavPageData> favPageResult = backupData("收藏夹", getFileName(favFolder),
                        new BackupCallback<FavPageData>() {
                            @Override
                            public FavPageData getData() throws BusinessException {
                                return getFavData(favFolder.getId());
                            }
                        }, false, false);
                FavPageData favPageData = favPageResult.getData();
                favFolder.setMedias(favPageData.getMedias());
                Integer oldSize = favFolder.getMediaCount();
                int backupSize = ListUtil.getSize(favFolder.getMedias());
                if (oldSize == backupSize) {
                    String msg = String.format("收藏夹[%s](%s)备份成功，已备份%s个视频", title, oldSize, backupSize);
                    log.info(msg);
                    tempResult.setSuccess(msg);
                } else {
                    String msg = String.format("收藏夹[%s](%s)备份失败，已备份%s个视频", title, oldSize, backupSize);
                    log.info(msg);
                    tempResult.setFailed(msg);
                }
            } catch (BusinessException e) {
                String msg = String.format("收藏夹[%s]备份失败：%s", title, e.getMessage());
                log.info(msg);
                tempResult.setFailed(msg);
            }
            results.add(tempResult);
        }
        return results;
    }

    @Override
    public List<BackupRestoreResult<List<FavFolder>>> restore() throws BusinessException {
        log.info("正在还原收藏夹...");
        BackupRestoreResult<List<FavFolder>> folderResult = restoreList(
                "收藏夹", "创建的收藏夹", FavFolder.class,
                new RestoreCallback<FavFolder>() {
                    @Override
                    public List<FavFolder> getNewList() throws BusinessException {
                        return getFavFolders();
                    }

                    @Override
                    public String compareFlag(FavFolder data) {
                        return data.getTitle();
                    }

                    @Override
                    public String dataName(FavFolder data) {
                        return String.format("收藏夹[%s]", data.getTitle());
                    }

                    @Override
                    public void restoreData(FavFolder data) throws BusinessException {
                        if ((data.getAttr() >> 1 & 1) == 0) {
                            throw new BusinessException("默认收藏夹，无需创建");
                        }
                        ApiResult<FavFolder> apiResult = new ModifyApi<FavFolder>(client, user,
                                "https://api.bilibili.com/x/v3/fav/folder/add", FavFolder.class)
                                .modify(
                                        new HashMap<String, String>() {{
                                            put("title", data.getTitle());
                                            put("intro", data.getIntro());
                                            put("privacy", String.valueOf(data.getAttr() & 1));
                                            put("cover", data.getCover());
                                        }}
                                );
                        if (apiResult.isFail()) {
                            throw new BusinessException(apiResult);
                        }
                    }
                });
        List<FavFolder> newFolders = folderResult.getData();
        if (ListUtil.isEmpty(newFolders)) {
            return createResults(folderResult);
        }

        List<BackupRestoreResult<List<FavFolder>>> results = new ArrayList<>(newFolders.size());
        // 需获取一下收藏夹，因为id和备份的不一样
        newFolders = getFavFolders();

        List<FavFolder> oldFolders = JSONObject.parseArray(readJsonFile(path, "收藏夹", "创建的收藏夹"), FavFolder.class);

        Map<String, FavFolder> mapNewFolders = new HashMap<>();
        Map<Long, Set<Long>> newUserFavMedias = new HashMap<>();
        // 获取新账号的数据
        for (FavFolder folder : newFolders) {
            mapNewFolders.put(folder.getTitle(), folder);
            if (!isDirectRestore()) {
                FavPageData favData = getFavData(String.valueOf(folder.getId()));
                List<Media> medias = favData.getMedias();
                if (ListUtil.notEmpty(medias)) {
                    Set<Long> ids = new HashSet<>();
                    for (Media media : medias) {
                        ids.add(media.getId());
                    }
                    newUserFavMedias.put(folder.getId(), ids);
                }
            }
        }

        log.info("正在解析需要收藏的视频...");
        Map<Long, List<Long>> videoFavNewIds = new LinkedHashMap<>();
        Map<Long, Media> mapMedias = new HashMap<>();
        for (FavFolder oldFolder : oldFolders) {
            handleInterrupt();

            String oldFolderTitle = oldFolder.getTitle();
            BackupRestoreResult<List<FavFolder>> result = new BackupRestoreResult<>();
            result.setBusinessType(BusinessType.RESTORE);
            result.setItemName("收藏夹：" + oldFolderTitle);
            result.setSuccess(true);

            if (mapNewFolders.containsKey(oldFolderTitle)) {
                Long newFolderId = mapNewFolders.get(oldFolderTitle).getId();
                log.info("读取旧账号收藏夹列表：{}", oldFolderTitle);
                FavPageData favData;
                try {
                    favData = JSONObject.parseObject(readJsonFile(path, "收藏夹",
                            getFileName(oldFolder)), FavPageData.class);
                } catch (BusinessException ex) {
                    favData = JSONObject.parseObject(readJsonFile(path, "收藏夹",
                            getOldFileName(oldFolder)), FavPageData.class);
                }
                oldFolder.setMedias(favData.getMedias());
                result.setData(Collections.singletonList(oldFolder));

                if (favData != null && ListUtil.notEmpty(favData.getMedias())) {
                    Collections.reverse(favData.getMedias());
                    for (Media media : favData.getMedias()) {
                        mapMedias.put(media.getId(), media);
                        List<Long> idList;
                        // 解析数据：旧收藏夹id<>旧收藏夹ids
                        if (videoFavNewIds.containsKey(media.getId())) {
                            idList = videoFavNewIds.get(media.getId());
                            idList.add(newFolderId);
                        } else {
                            idList = new ArrayList<>();
                            idList.add(newFolderId);
                            videoFavNewIds.put(media.getId(), idList);
                        }
                    }
                }
            } else {
                result.setFailed("备份失败，新创建的收藏夹不存在" + oldFolderTitle);
            }
            results.add(result);
        }
        log.info("即将收藏{}个视频", videoFavNewIds.size());
        Set<Long> failIds = new HashSet<>();
        for (Map.Entry<Long, List<Long>> entry : videoFavNewIds.entrySet()) {
            handleInterrupt();
            Long mediaId = entry.getKey();
            List<Long> folderIds = entry.getValue();
            Media media = mapMedias.get(mediaId);
            boolean isNeedAdd = false;
            for (Long folderId : folderIds) {
                if (!newUserFavMedias.containsKey(folderId) || !newUserFavMedias.get(folderId).contains(mediaId)) {
                    isNeedAdd = true;
                    break;
                }
            }
            if (!isNeedAdd) {
                log.info("[{}]已收藏", media.getTitle());
            } else {
                ApiResult<Object> apiResult = new ModifyApi<>(client, user,
                        "https://api.bilibili.com/x/v3/fav/resource/deal", JSONObject.class).modify(
                        new HashMap<String, String>() {{
                            put("rid", String.valueOf(mediaId));
                            put("type", "2");
                            put("add_media_ids", ListUtil.listToString(folderIds, ","));
                            put("del_media_ids", "");
                            put("platform", "web");
                            put("eab_x", "1");
                            put("ramval", "10");
                            put("ga", "1");
                            put("gaia_source", "web_normal");
                        }}
                );
                if (apiResult.isFail()) {
                    log.info("收藏[{}]失败：{}({})", media.getTitle(), apiResult.getMessage(), apiResult.getCode());
                    failIds.add(mediaId);
                } else {
                    log.info("收藏[{}]成功", media.getTitle());
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        }
        // 统计结果
        for (BackupRestoreResult<List<FavFolder>> result : results) {
            if (result.isSuccess()) {
                FavFolder favFolder = result.getData().get(0);
                List<Media> medias = favFolder.getMedias();
                String title = favFolder.getTitle();
                if (ListUtil.isEmpty(medias)) {
                    result.setFailed("[" + title + "]收藏夹为空");
                } else {
                    medias.removeIf(media -> failIds.contains(media.getId()));
                    Integer oldSize = favFolder.getMediaCount();
                    int newSize = medias.size();
                    if (oldSize == newSize) {
                        String msg = String.format("收藏夹[%s](%s)还原成功，已还原%s个视频", title, oldSize, newSize);
                        result.setSuccess(msg);
                    } else {
                        String msg = String.format("收藏夹[%s](%s)还原失败，已还原%s个视频", title, oldSize, newSize);
                        result.setFailed(msg);
                    }
                }
            }
        }
        return results;
    }

    @Override
    public void initFileName(Map<String, String> fileNames) {
        fileNames.put("收藏夹", "Favorites");
        fileNames.put("创建的收藏夹", "CreatedFavorites");
    }


    @Override
    public int getBackupCount(File dir) throws BusinessException {
        return getBackupListSize(dir, "收藏夹", "创建的收藏夹");
    }

    @Override
    public void setInterrupt(boolean interrupt) {
        if (pageApi != null) {
            pageApi.setInterrupt(interrupt);
        }
        super.setInterrupt(interrupt);
    }
}
