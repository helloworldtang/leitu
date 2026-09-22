package cn.youhuale.leitu.capability.cache.internal;

/** 租户复合键：缓存在内部按 (租户, 键) 定位条目，业务只传裸键。 */
record ScopedKey(String tenant, Object key) {
}
