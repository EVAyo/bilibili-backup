package top.ybgnb.bilibili.backup.service.impl;

import lombok.extern.slf4j.Slf4j;
import top.ybgnb.bilibili.backup.app.BilibiliBackup;
import top.ybgnb.bilibili.backup.bean.Upper;
import top.ybgnb.bilibili.backup.constant.BuType;
import top.ybgnb.bilibili.backup.error.BusinessException;
import top.ybgnb.bilibili.backup.service.BaseBusinessService;
import top.ybgnb.bilibili.backup.service.ServiceBuilder;
import top.ybgnb.bilibili.backup.user.User;
import top.ybgnb.bilibili.backup.userInfoCallback.DefaultUserInfoCallback;
import top.ybgnb.bilibili.backup.utils.CookieUtil;
import top.ybgnb.bilibili.backup.utils.ItemChoiceUtil;

import java.util.List;

import static top.ybgnb.bilibili.backup.utils.CommonUtil.userCookieThreadLocal;

/**
 * @author Dream
 */
@Slf4j
public class BackupBusinessService implements BaseBusinessService {

    @Override
    public Upper process(Object requestMsg) throws BusinessException {
        CookieUtil.getCookie();
        // 开始处理
        List<ServiceBuilder> items = ItemChoiceUtil.getServices();
        while (items.isEmpty()) {
            log.info("未选择任何项目，请重新选择");
            items = ItemChoiceUtil.getServices();
        }
        return new BilibiliBackup(items, new User(userCookieThreadLocal.get()), new DefaultUserInfoCallback()).start();
    }

    @Override
    public BuType getRequestType() {
        return BuType.BACKUP;
    }
}
