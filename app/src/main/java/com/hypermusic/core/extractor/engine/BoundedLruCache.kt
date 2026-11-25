package com.hypermusic.core.extractor.engine

internal class BoundedLruCache<K : Any, V : Any>(private val capacity: Int) {
    init { require(capacity > 0) }

    private val entries = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
    }

    @Synchronized operator fun get(key: K): V? = entries[key]
    @Synchronized operator fun set(key: K, value: V) { entries[key] = value }
}
