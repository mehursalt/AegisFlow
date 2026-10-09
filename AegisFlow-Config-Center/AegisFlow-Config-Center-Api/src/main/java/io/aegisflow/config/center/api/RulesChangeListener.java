package io.aegisflow.config.center.api;


import io.aegisflow.common.config.Rule;

import java.util.List;



/**
 * 规则变更监听器
 */

public interface RulesChangeListener {

    /**
     * 规则变更时调用此方法 对规则进行更新
     * @param rules 新规则
     */
    void onRulesChange(List<Rule> rules);
}
