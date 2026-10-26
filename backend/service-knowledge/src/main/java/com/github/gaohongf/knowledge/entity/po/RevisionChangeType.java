package com.github.gaohongf.knowledge.entity.po;

/** 修订快照产生的原因 */
public enum RevisionChangeType {

    /** 新建文章时写入的第 1 条快照 */
    CREATE,

    /** 编辑保存 */
    UPDATE,

    /** 回滚到历史修订 */
    ROLLBACK
}
