package io.github.hzhilong.bilibili.backup.app.utils;

import lombok.extern.slf4j.Slf4j;

/**
 * 菜单工具
 *
 * @author hzhilong
 * @version 1.0
 */
@Slf4j
public class MenuUtil {

    /**
     * 校验输入的下标
     */
    public static int checkInputPos(int arraySize, String inputData) {
        try {
            int pos = Integer.parseInt(inputData);
            if (pos > -1 && pos < arraySize) {
                return pos;
            } else {
                log.info("输入错误，请重新选择 ");
                return -1;
            }
        } catch (Exception e) {
            log.info("输入错误，请重新选择");
            return -1;
        }
    }
}
